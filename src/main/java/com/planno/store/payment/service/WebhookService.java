package com.planno.store.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.ConflictException;
import com.planno.store.inventory.service.InventoryService;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.service.OrderService;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.domain.WebhookEvent;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.service.RefundService;
import com.planno.store.payment.repository.PaymentRepository;
import com.planno.store.payment.repository.WebhookEventRepository;
import com.planno.store.payment.reconciliation.dto.ReconciliationResultResponse;
import com.planno.store.payment.webhook.service.WebhookEventLifecycleService;
import java.util.UUID;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WebhookService {

    private final com.planno.store.core.auditing.FinancialLock locks;

    private final MercadoPagoWebhookSignatureVerifier signatureVerifier;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentProviderClient paymentProviderClient;
    private final PaymentProviderAccountService paymentProviderAccountService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;
    private final WebhookEventLifecycleService webhookEventLifecycleService;

    public WebhookService(
            MercadoPagoWebhookSignatureVerifier signatureVerifier,
            PaymentRepository paymentRepository,
            WebhookEventRepository webhookEventRepository,
            PaymentProviderClient paymentProviderClient,
            PaymentProviderAccountService paymentProviderAccountService,
            OrderService orderService,
            InventoryService inventoryService,
            RefundService refundService,
            ObjectMapper objectMapper,
            WebhookEventLifecycleService webhookEventLifecycleService ,
            com.planno.store.core.auditing.FinancialLock locks
    ) {
        this.locks = locks;
        this.signatureVerifier = signatureVerifier;
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.paymentProviderClient = paymentProviderClient;
        this.paymentProviderAccountService = paymentProviderAccountService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.refundService = refundService;
        this.objectMapper = objectMapper;
        this.webhookEventLifecycleService = webhookEventLifecycleService;
    }

    @Transactional
    public void processMercadoPagoWebhook(String payload, String xSignature, String xRequestId, String dataId) {
        signatureVerifier.verify(xSignature, xRequestId, dataId);

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid Mercado Pago webhook payload");
        }

        String eventType = root.path("type").asText("payment");
        String externalEventId = StringUtils.hasText(xRequestId) ? xRequestId : eventType + ":" + dataId;
        UUID eventId = webhookEventLifecycleService.ensureEvent(PaymentProvider.MERCADO_PAGO, externalEventId, eventType, payload);
        Optional<WebhookEvent> existingEvent = webhookEventRepository.findById(eventId);
        if (existingEvent.isPresent() && existingEvent.get().isProcessed()) {
            return;
        }

        try {
            if ("payment".equalsIgnoreCase(eventType)) {
                processPaymentEvent(eventId, root, dataId);
                return;
            }

            if ("refund".equalsIgnoreCase(eventType)) {
                processRefundEvent(eventId, root, dataId);
                return;
            }

            webhookEventLifecycleService.markProcessed(eventId, null, null, null, null, "Ignored unsupported Mercado Pago webhook event type " + eventType);
        } catch (RuntimeException exception) {
            webhookEventLifecycleService.markFailed(eventId, null, null, null, null, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    @Transactional
    public ReconciliationResultResponse reprocessStoredEvent(WebhookEvent event, String actor) {
        try {
            JsonNode root = objectMapper.readTree(event.getPayload());
            if ("payment".equalsIgnoreCase(event.getEventType())) {
                processPaymentEvent(event.getId(), root, extractDataId(root, event.getExternalEventId()));
            } else if ("refund".equalsIgnoreCase(event.getEventType())) {
                processRefundEvent(event.getId(), root, extractDataId(root, event.getExternalEventId()));
            } else {
                webhookEventLifecycleService.markProcessed(event.getId(), event.getStore(), event.getPaymentId(), event.getRefundId(), event.getOrderId(),
                        "Ignored unsupported Mercado Pago webhook event type " + event.getEventType());
            }
            webhookEventLifecycleService.markReprocessed(event.getId(), actor, "Reprocessed successfully");
            return ReconciliationResultResponse.of("WEBHOOK_EVENT", event.getId().toString(), true, "Webhook event reprocessed safely");
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            webhookEventLifecycleService.markFailedReprocess(event.getId(), actor);
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Webhook event reprocessing failed");
        }
    }

    @Transactional
    public void reconcilePaymentFromProvider(Payment payment, PaymentProviderPaymentDetails providerPayment, String changedBy) {
        applyProviderState(payment, providerPayment, changedBy);
    }

    private void processPaymentEvent(UUID eventId, JsonNode root, String dataId) {
        String providerUserId = root.path("user_id").isMissingNode() || root.path("user_id").isNull()
                ? null
                : root.path("user_id").asText();
        PaymentProviderAccount account = resolveProviderAccount(providerUserId);
        PaymentProviderPaymentDetails providerPayment = paymentProviderClient.getPaymentDetails(account, dataId);
        Payment payment = resolvePayment(providerPayment, dataId);
        validateAccount(payment, account);
        applyProviderState(payment, providerPayment, "mercado-pago-webhook");
        webhookEventLifecycleService.markProcessed(eventId, payment.getStore(), payment.getId(), null, payment.getOrder().getId(),
                payment.getStatus() == PaymentStatus.APPROVED && payment.getOrder().getStatus() == OrderStatus.CANCELLED
                        ? "Late payment approved for cancelled order; manual reconciliation required; inventory unchanged"
                        : "Processed Mercado Pago payment webhook with status " + providerPayment.status().name());
    }

    private void processRefundEvent(UUID eventId, JsonNode root, String dataId) {
        String providerUserId = root.path("user_id").isMissingNode() || root.path("user_id").isNull()
                ? null
                : root.path("user_id").asText();
        PaymentProviderAccount account = resolveProviderAccount(providerUserId);
        Optional<Refund> localRefund = refundService.findOptionalByProviderRefundId(PaymentProvider.MERCADO_PAGO, dataId);
        if (localRefund.isPresent()) {
            validateAccount(localRefund.get().getPayment(), account);
        }
        String providerPaymentId = localRefund.map(refund -> refund.getPayment().getProviderPaymentId())
                .orElseGet(() -> root.path("data").path("payment_id").isMissingNode()
                        ? null : root.path("data").path("payment_id").asText(null));
        PaymentProviderRefundDetails providerRefund = paymentProviderClient.getRefundDetails(account, providerPaymentId, dataId);
        Payment payment = paymentRepository.findByProviderAndProviderPaymentId(PaymentProvider.MERCADO_PAGO, providerRefund.providerPaymentId())
                .orElseThrow(() -> new ConflictException("Unable to resolve payment for the Mercado Pago refund webhook event"));
        validateAccount(payment, account);
        refundService.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO, providerRefund, "Refund confirmed by Mercado Pago webhook");
        Refund resolvedRefund = refundService.findByProviderRefundId(PaymentProvider.MERCADO_PAGO, providerRefund.providerRefundId());
        webhookEventLifecycleService.markProcessed(eventId, payment.getStore(), payment.getId(), resolvedRefund.getId(), payment.getOrder().getId(),
                "Processed Mercado Pago refund webhook with status " + providerRefund.status().name());
    }

    private Payment resolvePayment(PaymentProviderPaymentDetails providerPayment, String externalResourceId) {
        if (StringUtils.hasText(providerPayment.providerPaymentId())) {
            Optional<Payment> byPaymentId = paymentRepository.findByProviderAndProviderPaymentId(
                    PaymentProvider.MERCADO_PAGO,
                    providerPayment.providerPaymentId()
            );
            if (byPaymentId.isPresent()) {
                return byPaymentId.get();
            }
        }

        if (StringUtils.hasText(providerPayment.externalReference())) {
            try {
                Payment byInternalPaymentId = paymentRepository.findById(UUID.fromString(providerPayment.externalReference()))
                        .orElse(null);
                if (byInternalPaymentId != null) {
                    return byInternalPaymentId;
                }
            } catch (IllegalArgumentException ignored) {
                // The external reference may be the order code in real Mercado Pago responses.
            }
        }

        if (StringUtils.hasText(providerPayment.providerCheckoutId())) {
            Payment byCheckoutId = paymentRepository.findByProviderAndProviderCheckoutId(
                    PaymentProvider.MERCADO_PAGO,
                    providerPayment.providerCheckoutId()
            ).orElse(null);
            if (byCheckoutId != null) {
                return byCheckoutId;
            }
        }

        Payment byRequestedResource = paymentRepository.findByProviderAndProviderCheckoutId(PaymentProvider.MERCADO_PAGO, externalResourceId)
                .orElse(null);
        if (byRequestedResource != null) {
            return byRequestedResource;
        }

        throw new ConflictException("Unable to resolve payment for the Mercado Pago webhook event");
    }

    private PaymentProviderAccount resolveProviderAccount(String providerUserId) {
        if (!StringUtils.hasText(providerUserId)) {
            throw new ConflictException("Unable to resolve the Mercado Pago account for the webhook event");
        }
        return paymentProviderAccountService.getByProviderUserId(PaymentProvider.MERCADO_PAGO, providerUserId);
    }

    private void validateAccount(Payment payment, PaymentProviderAccount account) {
        if (!payment.getStore().getId().equals(account.getStore().getId())
                || !account.getProviderUserId().equals(payment.getProviderMerchantId())) {
            throw new ConflictException("Payment does not belong to the provider account");
        }
    }

    private void applyProviderState(Payment payment, PaymentProviderPaymentDetails providerPayment, String changedBy) {
        payment = locks.payment(payment);
        if (providerPayment.amount() == null || providerPayment.amount().compareTo(payment.getAmount()) != 0
                || providerPayment.currency() == null || !providerPayment.currency().equalsIgnoreCase(payment.getCurrency())
                || payment.getProviderMerchantId() == null || !payment.getProviderMerchantId().equals(providerPayment.providerMerchantId())) {
            throw new ConflictException("Provider payment does not match the expected merchant, amount and currency");
        }
        PaymentStatus previousStatus = payment.getStatus();
        if (previousStatus == PaymentStatus.REFUNDED && providerPayment.status() != PaymentStatus.REFUNDED) return;
        if (previousStatus == PaymentStatus.PARTIALLY_REFUNDED && providerPayment.status() != PaymentStatus.REFUNDED) return;
        if (previousStatus == PaymentStatus.APPROVED && java.util.Set.of(PaymentStatus.CREATED, PaymentStatus.PENDING,
                PaymentStatus.CANCELLED, PaymentStatus.REJECTED, PaymentStatus.FAILED).contains(providerPayment.status())) return;
        payment.updateProviderState(
                providerPayment.status(),
                providerPayment.providerPaymentId(),
                providerPayment.providerCheckoutId(),
                providerPayment.paymentMethod(),
                providerPayment.statusDetail()
        );

        Order order = payment.getOrder();
        if (providerPayment.status() == PaymentStatus.APPROVED) {
            // The financial fact survives cancellation; fulfilment and refund policy are separate.
            if (order.getStatus() == OrderStatus.CANCELLED) {
                return;
            }
            if (order.getStatus() != OrderStatus.PAID) {
                for (OrderItem item : order.getItems()) {
                    inventoryService.commitReservedQuantity(payment.getStore().getId(), item.getProduct().getId(), item.getQuantity());
                }
                orderService.markPaid(order, changedBy, "Payment approved by Mercado Pago provider state");
            }
            return;
        }

        if (providerPayment.status() == PaymentStatus.REFUNDED) {
            refundService.reconcilePaymentRefundState(
                    payment,
                    null,
                    payment.getAmount(),
                    "Refund confirmed by Mercado Pago payment state"
            );
            return;
        }

        if (providerPayment.status() == PaymentStatus.CANCELLED
                || providerPayment.status() == PaymentStatus.REJECTED
                || providerPayment.status() == PaymentStatus.FAILED) {
            if (previousStatus == PaymentStatus.APPROVED) {
                payment.updateProviderState(
                        PaymentStatus.APPROVED,
                        providerPayment.providerPaymentId(),
                        providerPayment.providerCheckoutId(),
                        providerPayment.paymentMethod(),
                        providerPayment.statusDetail()
                );
            }
        }
    }

    private String extractDataId(JsonNode root, String externalEventId) {
        String dataId = root.path("data").path("id").asText(null);
        if (StringUtils.hasText(dataId)) {
            return dataId;
        }
        String[] parts = externalEventId.split(":", 2);
        return parts.length == 2 ? parts[1] : externalEventId;
    }
}
