package com.planno.store.payment.service;

import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.refund.domain.Refund;

public interface PaymentProviderClient {

    PaymentCheckoutResult createCheckout(Payment payment);

    PaymentCheckoutResult createTransparentPayment(Payment payment, TransparentPaymentProviderRequest request);

    PaymentProviderPaymentDetails getPaymentDetails(PaymentProviderAccount account, String externalResourceId);

    PaymentProviderRefundDetails createRefund(Refund refund);

    PaymentProviderRefundDetails getRefundDetails(PaymentProviderAccount account, String externalRefundId);
}
