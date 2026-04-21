package com.planno.store.payment.refund.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.security.CurrentActorProvider;
import com.planno.store.inventory.service.InventoryService;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.domain.OrderStatusChangeSource;
import com.planno.store.order.service.OrderService;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import com.planno.store.payment.refund.domain.RefundType;
import com.planno.store.payment.refund.dto.CreateRefundRequest;
import com.planno.store.payment.refund.dto.RefundResponse;
import com.planno.store.payment.refund.repository.RefundRepository;
import com.planno.store.payment.service.PaymentProviderClient;
import com.planno.store.payment.service.PaymentProviderRefundDetails;
import com.planno.store.payment.service.PaymentService;
import com.planno.store.store.service.CurrentStoreProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RefundService {

    private static final Set<RefundStatus> RESERVED_REFUND_STATUSES = Set.of(
            RefundStatus.REQUESTED,
            RefundStatus.PENDING,
            RefundStatus.APPROVED
    );

    private final RefundRepository refundRepository;
    private final PaymentService paymentService;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final CurrentStoreProvider currentStoreProvider;
    private final CurrentActorProvider currentActorProvider;
    private final PaymentProviderClient paymentProviderClient;

    public RefundService(
            RefundRepository refundRepository,
            PaymentService paymentService,
            OrderService orderService,
            InventoryService inventoryService,
            CurrentStoreProvider currentStoreProvider,
            CurrentActorProvider currentActorProvider,
            PaymentProviderClient paymentProviderClient
    ) {
        this.refundRepository = refundRepository;
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.currentStoreProvider = currentStoreProvider;
        this.currentActorProvider = currentActorProvider;
        this.paymentProviderClient = paymentProviderClient;
    }

    @Transactional
    public RefundResponse requestRefund(UUID paymentId, CreateRefundRequest request) {
        Payment payment = paymentService.getPaymentEntity(paymentId);
        validatePaymentEligibility(payment);

        BigDecimal reservedAmount = reservedRefundAmount(payment);
        BigDecimal remainingAmount = scale(payment.getAmount().subtract(reservedAmount));
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The payment no longer has refundable amount available");
        }

        BigDecimal requestedAmount = request.amount() == null ? remainingAmount : scale(request.amount());
        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Refund amount must be greater than zero");
        }
        if (requestedAmount.compareTo(remainingAmount) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Refund amount exceeds the refundable amount available");
        }

        RefundType refundType = requestedAmount.compareTo(remainingAmount) == 0 ? RefundType.FULL : RefundType.PARTIAL;
        Refund refund = refundRepository.saveAndFlush(Refund.requested(
                currentStoreProvider.getCurrentStore(),
                payment,
                payment.getOrder(),
                payment.getProvider(),
                refundType,
                requestedAmount,
                payment.getCurrency(),
                request.reason(),
                currentActorProvider.currentActorUserId()
        ));

        PaymentProviderRefundDetails providerRefund = paymentProviderClient.createRefund(refund);
        refund.updateProviderState(providerRefund.providerRefundId(), providerRefund.status(), providerRefund.rawResponse());
        applyRefundEffects(refund, payment, providerRefund, currentActorProvider.currentActor(), OrderStatusChangeSource.REFUND);
        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listByPayment(UUID paymentId) {
        paymentService.getPaymentEntity(paymentId);
        return refundRepository.findAllByPaymentIdAndStoreIdOrderByCreatedAtDesc(paymentId, currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> listByOrder(UUID orderId) {
        orderService.getOrderEntity(orderId);
        return refundRepository.findAllByOrderIdAndStoreIdOrderByCreatedAtDesc(orderId, currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(RefundResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse get(UUID refundId) {
        return RefundResponse.from(getRefundEntity(refundId));
    }

    @Transactional(readOnly = true)
    public Refund getRefundEntity(UUID refundId) {
        return refundRepository.findById(refundId)
                .filter(refund -> refund.getStore().getId().equals(currentStoreProvider.getCurrentStoreId()))
                .orElseThrow(() -> new NotFoundException("Refund not found"));
    }

    @Transactional(readOnly = true)
    public Refund findByProviderRefundId(PaymentProvider provider, String providerRefundId) {
        return refundRepository.findByProviderAndProviderRefundId(provider, providerRefundId)
                .orElseThrow(() -> new NotFoundException("Refund not found"));
    }

    @Transactional
    public void reconcilePaymentRefundState(Payment payment, String providerRefundId, BigDecimal amount, String reason) {
        Refund refund = resolveRefundForProviderState(payment, providerRefundId, amount);
        refund.updateProviderState(providerRefundId, RefundStatus.APPROVED, null);
        applyApprovedRefundEffects(refund, payment, "mercado-pago-webhook", reason, OrderStatusChangeSource.REFUND_WEBHOOK);
    }

    @Transactional
    public void reconcileProviderRefund(PaymentProvider provider, PaymentProviderRefundDetails providerRefund, String reason) {
        if (!StringUtils.hasText(providerRefund.providerPaymentId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Unable to resolve payment for the refund event");
        }

        Payment payment = paymentService.getByProviderPaymentId(provider, providerRefund.providerPaymentId());
        Refund refund = refundRepository.findByProviderAndProviderRefundId(provider, providerRefund.providerRefundId())
                .orElseGet(() -> refundRepository.save(Refund.requested(
                        payment.getStore(),
                        payment,
                        payment.getOrder(),
                        payment.getProvider(),
                        providerRefund.amount().compareTo(payment.getAmount()) == 0 ? RefundType.FULL : RefundType.PARTIAL,
                        providerRefund.amount(),
                        providerRefund.currency(),
                        reason,
                        null
                )));

        refund.updateProviderState(providerRefund.providerRefundId(), providerRefund.status(), providerRefund.rawResponse());
        applyRefundEffects(refund, payment, providerRefund, "mercado-pago-webhook", OrderStatusChangeSource.REFUND_WEBHOOK);
    }

    private void validatePaymentEligibility(Payment payment) {
        if (payment.getStatus() != PaymentStatus.APPROVED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only approved payments can be refunded");
        }
        if (payment.getOrder().getStatus() != OrderStatus.PAID) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only paid orders can be refunded");
        }
        if (!StringUtils.hasText(payment.getProviderPaymentId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The payment does not have a provider payment id yet");
        }
    }

    private void applyRefundEffects(
            Refund refund,
            Payment payment,
            PaymentProviderRefundDetails providerRefund,
            String changedBy,
            OrderStatusChangeSource source
    ) {
        if (providerRefund.status() == RefundStatus.APPROVED) {
            applyApprovedRefundEffects(refund, payment, changedBy, "Refund approved", source);
            return;
        }
        if (providerRefund.status() == RefundStatus.PENDING) {
            return;
        }
    }

    private void applyApprovedRefundEffects(
            Refund refund,
            Payment payment,
            String changedBy,
            String reason,
            OrderStatusChangeSource source
    ) {
        BigDecimal approvedAmount = approvedRefundAmount(payment);
        if (approvedAmount.compareTo(payment.getAmount()) >= 0) {
            payment.updateProviderState(
                    PaymentStatus.REFUNDED,
                    payment.getProviderPaymentId(),
                    payment.getProviderCheckoutId(),
                    payment.getPaymentMethod()
            );
            if (!refund.isInventoryRestored()) {
                restoreInventoryForFullRefund(payment.getOrder());
                refund.markInventoryRestored();
            }
            if (payment.getOrder().getStatus() != OrderStatus.REFUNDED) {
                orderService.markRefunded(payment.getOrder(), changedBy, reason, source);
            }
            return;
        }

        payment.updateProviderState(
                PaymentStatus.PARTIALLY_REFUNDED,
                payment.getProviderPaymentId(),
                payment.getProviderCheckoutId(),
                payment.getPaymentMethod()
        );
    }

    private void restoreInventoryForFullRefund(Order order) {
        for (OrderItem item : order.getItems()) {
            inventoryService.restoreCommittedQuantity(order.getStore().getId(), item.getProduct().getId(), item.getQuantity());
        }
    }

    private Refund resolveRefundForProviderState(Payment payment, String providerRefundId, BigDecimal amount) {
        if (StringUtils.hasText(providerRefundId)) {
            return refundRepository.findByProviderAndProviderRefundId(payment.getProvider(), providerRefundId)
                    .orElseGet(() -> refundRepository.save(Refund.requested(
                            payment.getStore(),
                            payment,
                            payment.getOrder(),
                            payment.getProvider(),
                            amount != null && amount.compareTo(payment.getAmount()) < 0 ? RefundType.PARTIAL : RefundType.FULL,
                            amount == null ? payment.getAmount() : scale(amount),
                            payment.getCurrency(),
                            "Refund reconciled from provider event",
                            null
                    )));
        }

        return refundRepository.findTopByPaymentIdAndStoreIdAndStatusInOrderByCreatedAtDesc(
                        payment.getId(),
                        payment.getStore().getId(),
                        Set.of(RefundStatus.REQUESTED, RefundStatus.PENDING, RefundStatus.APPROVED)
                )
                .orElseGet(() -> refundRepository.save(Refund.requested(
                        payment.getStore(),
                        payment,
                        payment.getOrder(),
                        payment.getProvider(),
                        amount != null && amount.compareTo(payment.getAmount()) < 0 ? RefundType.PARTIAL : RefundType.FULL,
                        amount == null ? payment.getAmount() : scale(amount),
                        payment.getCurrency(),
                        "Refund reconciled from provider event",
                        null
                )));
    }

    private BigDecimal reservedRefundAmount(Payment payment) {
        return scale(refundRepository.sumAmountsByPaymentAndStoreAndStatuses(
                payment.getId(),
                payment.getStore().getId(),
                RESERVED_REFUND_STATUSES
        ));
    }

    private BigDecimal approvedRefundAmount(Payment payment) {
        return scale(refundRepository.sumAmountsByPaymentAndStoreAndStatuses(
                payment.getId(),
                payment.getStore().getId(),
                Set.of(RefundStatus.APPROVED)
        ));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
