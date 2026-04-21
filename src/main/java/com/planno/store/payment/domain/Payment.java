package com.planno.store.payment.domain;

import com.planno.store.core.auditing.AuditableEntity;
import com.planno.store.order.domain.Order;
import com.planno.store.store.domain.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key")
        }
)
public class Payment extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_type", nullable = false, length = 40)
    private CheckoutType checkoutType;

    @Column(length = 120)
    private String providerPaymentId;

    @Column(length = 120)
    private String providerCheckoutId;

    @Column(length = 80)
    private String providerMerchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentStatus status;

    @Column(length = 255)
    private String statusDetail;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(length = 80)
    private String paymentMethod;

    @Column
    private Integer installments;

    @Column(length = 255)
    private String payerEmail;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal sellerNetAmount;

    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;

    @Column(length = 20000)
    private String rawInitResponse;

    protected Payment() {
    }

    private Payment(
            Store store,
            Order order,
            PaymentProvider provider,
            CheckoutType checkoutType,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal sellerNetAmount,
            String providerMerchantId,
            String idempotencyKey
    ) {
        this.store = store;
        this.order = order;
        this.provider = provider;
        this.checkoutType = checkoutType;
        this.status = status;
        this.amount = amount.setScale(2, RoundingMode.HALF_UP);
        this.currency = currency.trim().toUpperCase();
        this.commissionRate = commissionRate.setScale(2, RoundingMode.HALF_UP);
        this.commissionAmount = commissionAmount.setScale(2, RoundingMode.HALF_UP);
        this.sellerNetAmount = sellerNetAmount.setScale(2, RoundingMode.HALF_UP);
        this.providerMerchantId = blankToNull(providerMerchantId);
        this.idempotencyKey = idempotencyKey;
    }

    public static Payment create(
            Store store,
            Order order,
            PaymentProvider provider,
            BigDecimal amount,
            String currency,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal sellerNetAmount,
            String providerMerchantId,
            String idempotencyKey
    ) {
        return create(
                store,
                order,
                provider,
                CheckoutType.PRO,
                amount,
                currency,
                commissionRate,
                commissionAmount,
                sellerNetAmount,
                providerMerchantId,
                idempotencyKey
        );
    }

    public static Payment create(
            Store store,
            Order order,
            PaymentProvider provider,
            CheckoutType checkoutType,
            BigDecimal amount,
            String currency,
            BigDecimal commissionRate,
            BigDecimal commissionAmount,
            BigDecimal sellerNetAmount,
            String providerMerchantId,
            String idempotencyKey
    ) {
        return new Payment(
                store,
                order,
                provider,
                checkoutType,
                PaymentStatus.CREATED,
                amount,
                currency,
                commissionRate,
                commissionAmount,
                sellerNetAmount,
                providerMerchantId,
                idempotencyKey
        );
    }

    public void markPending(String providerCheckoutId, String rawInitResponse) {
        applyCheckoutResult(new com.planno.store.payment.service.PaymentCheckoutResult(
                null,
                providerCheckoutId,
                null,
                PaymentStatus.PENDING,
                null,
                null,
                rawInitResponse
        ));
    }

    public void initializeTransparent(
            String providerPaymentId,
            PaymentStatus status,
            String statusDetail,
            String paymentMethod,
            Integer installments,
            String payerEmail,
            String rawInitResponse
    ) {
        this.providerPaymentId = blankToNull(providerPaymentId);
        this.status = status;
        this.statusDetail = blankToNull(statusDetail);
        this.paymentMethod = blankToNull(paymentMethod);
        this.installments = installments;
        this.payerEmail = blankToNull(payerEmail);
        this.rawInitResponse = blankToNull(rawInitResponse);
    }

    public void applyCheckoutResult(com.planno.store.payment.service.PaymentCheckoutResult result) {
        this.providerPaymentId = blankToNull(result.providerPaymentId());
        this.providerCheckoutId = blankToNull(result.providerCheckoutId());
        this.status = result.status();
        this.statusDetail = blankToNull(result.statusDetail());
        this.paymentMethod = blankToNull(result.paymentMethod());
        this.rawInitResponse = blankToNull(result.rawResponse());
    }

    public void updateProviderState(
            PaymentStatus status,
            String providerPaymentId,
            String providerCheckoutId,
            String paymentMethod
    ) {
        updateProviderState(status, providerPaymentId, providerCheckoutId, paymentMethod, null);
    }

    public void updateProviderState(
            PaymentStatus status,
            String providerPaymentId,
            String providerCheckoutId,
            String paymentMethod,
            String statusDetail
    ) {
        this.status = status;
        this.providerPaymentId = blankToNull(providerPaymentId);
        if (providerCheckoutId != null && !providerCheckoutId.isBlank()) {
            this.providerCheckoutId = providerCheckoutId.trim();
        }
        this.paymentMethod = blankToNull(paymentMethod);
        this.statusDetail = blankToNull(statusDetail);
    }

    public Store getStore() {
        return store;
    }

    public Order getOrder() {
        return order;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public CheckoutType getCheckoutType() {
        return checkoutType;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getProviderCheckoutId() {
        return providerCheckoutId;
    }

    public String getProviderMerchantId() {
        return providerMerchantId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getStatusDetail() {
        return statusDetail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public Integer getInstallments() {
        return installments;
    }

    public String getPayerEmail() {
        return payerEmail;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public BigDecimal getCommissionAmount() {
        return commissionAmount;
    }

    public BigDecimal getSellerNetAmount() {
        return sellerNetAmount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRawInitResponse() {
        return rawInitResponse;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
