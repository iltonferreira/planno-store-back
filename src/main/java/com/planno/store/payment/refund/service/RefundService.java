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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entities;

    private final com.planno.store.core.auditing.FinancialLock locks;

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
            PaymentProviderClient paymentProviderClient ,
            com.planno.store.core.auditing.FinancialLock locks
    ) {
        this.locks = locks;
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
        Payment payment = locks.payment(paymentService.getPaymentEntity(paymentId));
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
        validateProviderRefund(payment, providerRefund);
        validateRefundAssociation(refund, payment, providerRefund.amount(), providerRefund.currency());
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
        return findOptionalByProviderRefundId(provider, providerRefundId)
                .orElseThrow(() -> new NotFoundException("Refund not found"));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Refund> findOptionalByProviderRefundId(PaymentProvider provider, String providerRefundId) {
        return refundRepository.findByProviderAndProviderRefundId(provider, providerRefundId);
    }

    @Transactional
    public void reconcilePaymentRefundState(Payment payment, String providerRefundId, BigDecimal amount, String reason) {
        payment = locks.payment(payment);
        Refund refund;
        if (StringUtils.hasText(providerRefundId)) {
            BigDecimal refundAmount = amount == null ? payment.getAmount() : amount;
            refund = resolveIdentifiedRefund(payment, providerRefundId, refundAmount, payment.getCurrency(), reason);
            refund.updateProviderState(providerRefundId, RefundStatus.APPROVED, null);
        } else {
            if (amount != null && amount.compareTo(payment.getAmount()) != 0) {
                throw refundConflict();
            }
            refund = findProviderStateSummary(payment);
            if (refund == null) {
                BigDecimal remaining = payment.getAmount().subtract(approvedIdentifiedRefundAmount(payment));
                refund = refundRepository.save(Refund.providerStateSummary(payment, remaining));
            }
        }
        applyApprovedRefundEffects(refund, payment, "mercado-pago-webhook", reason, OrderStatusChangeSource.REFUND_WEBHOOK);
    }

    @Transactional
    public void reconcileProviderRefund(PaymentProvider provider, PaymentProviderRefundDetails providerRefund, String reason) {
        if (!StringUtils.hasText(providerRefund.providerPaymentId())) {
            throw refundConflict();
        }
        Payment payment = locks.payment(paymentService.getByProviderPaymentId(provider, providerRefund.providerPaymentId()));
        validateProviderRefund(payment, providerRefund);
        Refund refund = resolveIdentifiedRefund(payment, providerRefund.providerRefundId(), providerRefund.amount(), providerRefund.currency(), reason);
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
        if (refund.isApproved()) {
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
        rebalanceProviderStateSummary(payment);
        BigDecimal approvedAmount = approvedRefundAmount(payment);
        if (approvedAmount.compareTo(payment.getAmount()) > 0) {
            throw refundConflict();
        }
        if (approvedAmount.compareTo(payment.getAmount()) == 0) {
            payment.updateProviderState(
                    PaymentStatus.REFUNDED,
                    payment.getProviderPaymentId(),
                    payment.getProviderCheckoutId(),
                    payment.getPaymentMethod()
            );
            // The ledger retains stock effects even when reconciliation repairs a divergent order status.
            if (payment.getOrder().getStatus() == OrderStatus.PAID) {
                if (!refundRepository.existsByPaymentIdAndStoreIdAndInventoryRestoredTrue(payment.getId(), payment.getStore().getId())) {
                    restoreInventoryForFullRefund(payment.getOrder());
                    refund.markInventoryRestored();
                }
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

    private Refund resolveIdentifiedRefund(Payment payment, String providerRefundId, BigDecimal amount, String currency, String reason) {
        validateRefundAmountAndCurrency(payment, amount, currency);
        if (!StringUtils.hasText(providerRefundId)) {
            throw refundConflict();
        }
        String identity = providerRefundId.trim();
        Refund refund = refundRepository.findByProviderAndProviderRefundIdAndPaymentIdAndStoreIdAndProviderStateSummaryFalse(
                payment.getProvider(), identity, payment.getId(), payment.getStore().getId()).orElse(null);
        if (refund == null) {
            // A known provider identity belonging to another payment/store must fail before mutation.
            if (refundRepository.findByProviderAndProviderRefundId(payment.getProvider(), identity).isPresent()) {
                throw refundConflict();
            }
            return refundRepository.save(Refund.requested(payment.getStore(), payment, payment.getOrder(), payment.getProvider(),
                    amount.compareTo(payment.getAmount()) == 0 ? RefundType.FULL : RefundType.PARTIAL,
                    amount, currency, reason, null));
        }
        // The caller may have loaded this refund before acquiring the financial locks.
        entities.refresh(refund);
        validateRefundAssociation(refund, payment, amount, currency);
        return refund;
    }

    private void validateProviderRefund(Payment payment, PaymentProviderRefundDetails details) {
        if (!StringUtils.hasText(details.providerRefundId()) || details.status() == null
                || !StringUtils.hasText(details.providerPaymentId())
                || !details.providerPaymentId().trim().equals(payment.getProviderPaymentId())) {
            throw refundConflict();
        }
        validateRefundAmountAndCurrency(payment, details.amount(), details.currency());
    }

    private void validateRefundAmountAndCurrency(Payment payment, BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2 || amount.compareTo(payment.getAmount()) > 0
                || !StringUtils.hasText(currency) || !payment.getCurrency().equalsIgnoreCase(currency.trim())) {
            throw refundConflict();
        }
    }

    private void validateRefundAssociation(Refund refund, Payment payment, BigDecimal amount, String currency) {
        if (refund.isProviderStateSummary() || !refund.getPayment().getId().equals(payment.getId())
                || !refund.getStore().getId().equals(payment.getStore().getId())
                || !refund.getOrder().getId().equals(payment.getOrder().getId()) || refund.getProvider() != payment.getProvider()
                || refund.getAmount().compareTo(amount) != 0 || !refund.getCurrency().equalsIgnoreCase(currency.trim())) {
            throw refundConflict();
        }
    }

    private Refund findProviderStateSummary(Payment payment) {
        List<Refund> summaries = refundRepository.findAllByPaymentIdAndStoreIdAndProviderStateSummaryTrue(
                payment.getId(), payment.getStore().getId());
        if (summaries.size() > 1) {
            throw refundConflict();
        }
        if (summaries.isEmpty()) {
            return null;
        }
        Refund summary = summaries.getFirst();
        entities.refresh(summary);
        if (!summary.isApproved() || summary.getProviderRefundId() != null || summary.getAmount().signum() < 0
                || summary.getProvider() != payment.getProvider() || !summary.getPayment().getId().equals(payment.getId())
                || !summary.getStore().getId().equals(payment.getStore().getId())
                || !summary.getOrder().getId().equals(payment.getOrder().getId())
                || !summary.getCurrency().equals(payment.getCurrency())) {
            throw refundConflict();
        }
        return summary;
    }

    private BigDecimal approvedIdentifiedRefundAmount(Payment payment) {
        BigDecimal identifiedAmount = BigDecimal.ZERO;
        for (Refund refund : refundRepository.findAllByPaymentIdAndStoreIdOrderByCreatedAtDesc(payment.getId(), payment.getStore().getId())) {
            // AUTO flush on the query preserves this transaction's updates before refreshing stale rows.
            entities.refresh(refund);
            if (!refund.isProviderStateSummary() && refund.isApproved()) {
                // Historical unlabelled cumulative rows require inspection, never guessed identities.
                if (!StringUtils.hasText(refund.getProviderRefundId())) {
                    throw refundConflict();
                }
                validateRefundAmountAndCurrency(payment, refund.getAmount(), refund.getCurrency());
                validateRefundAssociation(refund, payment, refund.getAmount(), refund.getCurrency());
                identifiedAmount = identifiedAmount.add(refund.getAmount());
            }
        }
        if (identifiedAmount.compareTo(payment.getAmount()) > 0) {
            throw refundConflict();
        }
        return identifiedAmount;
    }

    private void rebalanceProviderStateSummary(Payment payment) {
        BigDecimal identifiedAmount = approvedIdentifiedRefundAmount(payment);
        Refund summary = findProviderStateSummary(payment);
        if (summary != null) {
            summary.rebalanceProviderStateSummary(payment.getAmount().subtract(identifiedAmount));
        }
    }

    private BusinessException refundConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "Refund provider state is inconsistent with the payment");
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
