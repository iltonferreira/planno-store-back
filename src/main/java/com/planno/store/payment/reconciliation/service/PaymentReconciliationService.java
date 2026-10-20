package com.planno.store.payment.reconciliation.service;

import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.payment.reconciliation.dto.ReconciliationResultResponse;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.service.RefundService;
import com.planno.store.payment.service.PaymentProviderClient;
import com.planno.store.payment.service.PaymentProviderPaymentDetails;
import com.planno.store.payment.service.PaymentProviderRefundDetails;
import com.planno.store.payment.service.PaymentService;
import com.planno.store.payment.service.WebhookService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PaymentReconciliationService {

    private final PaymentService paymentService;
    private final RefundService refundService;
    private final WebhookService webhookService;
    private final PaymentProviderClient paymentProviderClient;
    private final PaymentProviderAccountService paymentProviderAccountService;

    public PaymentReconciliationService(
            PaymentService paymentService,
            RefundService refundService,
            WebhookService webhookService,
            PaymentProviderClient paymentProviderClient,
            PaymentProviderAccountService paymentProviderAccountService
    ) {
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.webhookService = webhookService;
        this.paymentProviderClient = paymentProviderClient;
        this.paymentProviderAccountService = paymentProviderAccountService;
    }

    @Transactional
    public ReconciliationResultResponse reconcilePayment(UUID paymentId) {
        Payment payment = paymentService.getPaymentEntity(paymentId);
        PaymentProviderAccount account = paymentProviderAccountService.requireConnectedAccountForStore(payment.getStore().getId());
        String externalId = StringUtils.hasText(payment.getProviderPaymentId()) ? payment.getProviderPaymentId() : payment.getProviderCheckoutId();
        PaymentProviderPaymentDetails providerPayment = paymentProviderClient.getPaymentDetails(account, externalId);

        String before = payment.getStatus().name() + ":" + payment.getOrder().getStatus().name();
        webhookService.reconcilePaymentFromProvider(payment, providerPayment, "payment-admin-reconcile");
        String after = payment.getStatus().name() + ":" + payment.getOrder().getStatus().name();
        boolean changed = !before.equals(after);

        if (payment.getStatus() == PaymentStatus.APPROVED && payment.getOrder().getStatus() == OrderStatus.CANCELLED) {
            return ReconciliationResultResponse.of("PAYMENT", payment.getId().toString(), changed,
                    "Late payment approved for cancelled order; manual reconciliation required; inventory unchanged");
        }

        return ReconciliationResultResponse.of("PAYMENT", payment.getId().toString(), changed, changed
                ? "Payment reconciled from provider state " + providerPayment.status().name()
                : "Payment already consistent with provider");
    }

    @Transactional
    public ReconciliationResultResponse reconcileRefund(UUID refundId) {
        Refund refund = refundService.getRefundEntity(refundId);
        if (refund.isProviderStateSummary()) {
            ReconciliationResultResponse result = reconcilePayment(refund.getPayment().getId());
            return ReconciliationResultResponse.of("REFUND", refund.getId().toString(), result.changed(),
                    "Cumulative refund balance reconciled through payment: " + result.result());
        }
        PaymentProviderAccount account = paymentProviderAccountService.requireConnectedAccountForStore(refund.getStore().getId());
        PaymentProviderRefundDetails providerRefund = paymentProviderClient.getRefundDetails(account,
                refund.getPayment().getProviderPaymentId(), refund.getProviderRefundId());
        String before = refund.getStatus().name() + ":" + refund.getPayment().getStatus().name() + ":" + refund.getOrder().getStatus().name();
        refundService.reconcileProviderRefund(refund.getProvider(), providerRefund, "Refund reconciled manually");
        String after = refund.getStatus().name() + ":" + refund.getPayment().getStatus().name() + ":" + refund.getOrder().getStatus().name();
        boolean changed = !before.equals(after);
        return ReconciliationResultResponse.of("REFUND", refund.getId().toString(), changed, changed
                ? "Refund reconciled from provider state " + providerRefund.status().name()
                : "Refund already consistent with provider");
    }
}
