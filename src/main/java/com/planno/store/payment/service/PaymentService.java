package com.planno.store.payment.service;

import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.service.OrderService;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.domain.CheckoutType;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.dto.CreateTransparentPaymentRequest;
import com.planno.store.payment.dto.PaymentInitiationResponse;
import com.planno.store.payment.dto.StorePaymentResponse;
import com.planno.store.payment.dto.TransparentPaymentResponse;
import com.planno.store.payment.repository.PaymentRepository;
import com.planno.store.store.service.CurrentStoreProvider;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final com.planno.store.core.auditing.FinancialLock locks;

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final CurrentStoreProvider currentStoreProvider;
    private final PaymentProviderClient paymentProviderClient;
    private final PaymentProviderAccountService paymentProviderAccountService;
    private final PaymentInitiationJournalService initiationJournal;
    private final Clock reservationClock;

    public PaymentService(
            PaymentRepository paymentRepository,
            OrderService orderService,
            CurrentStoreProvider currentStoreProvider,
            PaymentProviderClient paymentProviderClient,
            PaymentProviderAccountService paymentProviderAccountService ,
            com.planno.store.core.auditing.FinancialLock locks,
            PaymentInitiationJournalService initiationJournal,
            @Qualifier("orderReservationClock") Clock reservationClock
    ) {
        this.locks = locks;
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.currentStoreProvider = currentStoreProvider;
        this.paymentProviderClient = paymentProviderClient;
        this.paymentProviderAccountService = paymentProviderAccountService;
        this.initiationJournal = initiationJournal;
        this.reservationClock = reservationClock;
    }

    @Transactional
    public PaymentInitiationResponse initiate(CreatePaymentRequest request) {
        Order order = locks.order(orderService.getPublicOrderEntity(request.orderId()));
        validateOrderForPayment(order);
        PaymentProviderAccount account = paymentProviderAccountService.requireConnectedAccountForCurrentStore();
        Payment payment = createPaymentAttempt(order, account, CheckoutType.PRO);

        initiationJournal.register(order.getStore().getId(), order.getId(), payment.getId());
        PaymentCheckoutResult checkoutResult = paymentProviderClient.createCheckout(payment);
        payment.applyCheckoutResult(checkoutResult);
        initiationJournal.resolve(order.getStore().getId(), order.getId(), payment.getId());
        return PaymentInitiationResponse.from(payment, checkoutResult.checkoutUrl());
    }

    @Transactional
    public TransparentPaymentResponse initiateTransparent(CreateTransparentPaymentRequest request) {
        validateTransparentRequest(request);

        Order order = locks.order(orderService.getPublicOrderEntity(request.orderId()));
        validateOrderForPayment(order);
        PaymentProviderAccount account = paymentProviderAccountService.requireTransparentReadyAccountForCurrentStore();
        Payment payment = createPaymentAttempt(order, account, CheckoutType.TRANSPARENT);

        initiationJournal.register(order.getStore().getId(), order.getId(), payment.getId());
        PaymentCheckoutResult checkoutResult = paymentProviderClient.createTransparentPayment(
                payment,
                new TransparentPaymentProviderRequest(
                        request.token().trim(),
                        request.paymentMethodId().trim(),
                        normalize(request.issuerId()),
                        request.installments(),
                        request.payerEmail().trim(),
                        normalize(request.payerFirstName()),
                        normalize(request.payerLastName()),
                        normalize(request.identificationType()),
                        normalize(request.identificationNumber()),
                        normalize(request.deviceId())
                )
        );

        payment.initializeTransparent(
                checkoutResult.providerPaymentId(),
                checkoutResult.status(),
                checkoutResult.statusDetail(),
                checkoutResult.paymentMethod(),
                request.installments(),
                request.payerEmail().trim(),
                checkoutResult.rawResponse()
        );
        initiationJournal.resolve(order.getStore().getId(), order.getId(), payment.getId());
        return TransparentPaymentResponse.from(payment, transparentMessage(checkoutResult.status()), null);
    }

    @Transactional(readOnly = true)
    public List<StorePaymentResponse> listByOrder(UUID orderId) {
        orderService.getOrderEntity(orderId);
        return paymentRepository.findAllByOrderIdAndStoreIdOrderByCreatedAtDesc(orderId, currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(StorePaymentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Payment getPaymentEntity(UUID paymentId) {
        return paymentRepository.findByIdAndStoreId(paymentId, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public StorePaymentResponse get(UUID paymentId) {
        return StorePaymentResponse.from(getPaymentEntity(paymentId));
    }

    @Transactional(readOnly = true)
    public Payment getByProviderPaymentId(PaymentProvider provider, String providerPaymentId) {
        return paymentRepository.findByProviderAndProviderPaymentId(provider, providerPaymentId)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    @Transactional(readOnly = true)
    public Payment findByProviderCheckoutId(PaymentProvider provider, String providerCheckoutId) {
        return paymentRepository.findByProviderAndProviderCheckoutId(provider, providerCheckoutId)
                .orElse(null);
    }

    private void validateOrderForPayment(Order order) {
        if (initiationJournal.hasUnresolvedInitiation(order.getStore().getId(), order.getId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "A payment initiation requires reconciliation before retry");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cancelled orders cannot start payments");
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Refunded orders cannot start new payments");
        }
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Paid orders cannot start new payments");
        }
        if (order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The order is not in a payable state");
        }
        if (order.getReservationExpiresAt() != null && !order.getReservationExpiresAt().toInstant().isAfter(reservationClock.instant())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "The order reservation has expired");
        }
        // Reject known local monetary errors before recording any possible external effect.
        if (order.getTotalAmount().signum() <= 0 || order.getTotalAmount().stripTrailingZeros().scale() > 2) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Checkout amount must be positive and expressed in cents");
        }
    }

    private Payment createPaymentAttempt(Order order, PaymentProviderAccount account, CheckoutType checkoutType) {
        paymentRepository.findFirstByOrderIdAndStoreIdAndStatusInOrderByCreatedAtDesc(
                        order.getId(),
                        currentStoreProvider.getCurrentStoreId(),
                        Set.of(PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.APPROVED)
                )
                .ifPresent(existing -> {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "The order already has an active payment attempt");
                });

        return paymentRepository.save(Payment.create(
                currentStoreProvider.getCurrentStore(),
                order,
                PaymentProvider.MERCADO_PAGO,
                checkoutType,
                order.getTotalAmount(),
                order.getStore().getCurrency(),
                order.getCommissionRate(),
                order.getCommissionAmount(),
                order.getSellerNetAmount(),
                account.getProviderUserId(),
                UUID.randomUUID().toString()
        ));
    }

    private static void validateTransparentRequest(CreateTransparentPaymentRequest request) {
        if (request.installments() == null || request.installments() < 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Installments must be greater than zero");
        }
        boolean identificationProvided = request.identificationType() != null || request.identificationNumber() != null;
        if (identificationProvided && (isBlank(request.identificationType()) || isBlank(request.identificationNumber()))) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Identification type and number must be informed together");
        }
    }

    private static String transparentMessage(PaymentStatus status) {
        return switch (status) {
            case APPROVED -> "Transparent payment created and awaiting webhook confirmation";
            case PENDING, CREATED -> "Transparent payment created and pending provider confirmation";
            case REJECTED -> "Transparent payment was rejected by the provider";
            case CANCELLED -> "Transparent payment was cancelled by the provider";
            default -> "Transparent payment created";
        };
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
