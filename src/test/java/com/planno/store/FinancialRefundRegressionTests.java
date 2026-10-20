package com.planno.store;

import com.planno.store.cart.domain.Cart;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import com.planno.store.payment.refund.service.RefundService;
import com.planno.store.payment.service.PaymentProviderRefundDetails;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

// Direct provider reconciliation uses synthetic data and the disposable test-profile H2 database.
class FinancialRefundRegressionTests extends AbstractIntegrationTest {
    @Autowired RefundService refunds;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private record Fixture(Store store, UUID productId, UUID orderId, UUID paymentId, String externalPaymentId) {}

    private Fixture fixture(String slug) {
        Store store = createStore(slug);
        var product = productRepository.saveAndFlush(Product.create(store, null, "Synthetic product", "refund-product",
                "SKU-REFUND", "Synthetic fixture", ProductStatus.ACTIVE, money("40.00"), null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 6, 0));
        var cart = Cart.create(store, null, "refund-owner-" + UUID.randomUUID());
        cart.markCheckedOut();
        cart = cartRepository.saveAndFlush(cart);
        var order = Order.create(store, cart.getId(), "REF-" + UUID.randomUUID().toString().substring(0, 12),
                OrderStatus.PAID, money("80.00"), money("12.90"), BigDecimal.ZERO,
                null, null, money("92.90"), BigDecimal.ZERO, BigDecimal.ZERO, money("92.90"),
                "Synthetic customer", "customer@example.com", null, UUID.randomUUID(), "Synthetic shipping", "FIXED", 4);
        order.addItem(OrderItem.create(order, product, product.getName(), product.getSlug(), product.getSku(),
                2, money("40.00"), money("80.00")));
        order = orderRepository.saveAndFlush(order);
        String externalId = "synthetic-payment-" + slug;
        var payment = Payment.create(store, order, PaymentProvider.MERCADO_PAGO, money("92.90"), "BRL",
                BigDecimal.ZERO, BigDecimal.ZERO, money("92.90"), "synthetic-merchant", UUID.randomUUID().toString());
        payment.updateProviderState(PaymentStatus.APPROVED, externalId, "synthetic-checkout-" + slug, "pix");
        payment = paymentRepository.saveAndFlush(payment);
        return new Fixture(store, product.getId(), order.getId(), payment.getId(), externalId);
    }

    private PaymentProviderRefundDetails details(Fixture f, String id, String amount) {
        return new PaymentProviderRefundDetails(id, f.externalPaymentId(), RefundStatus.APPROVED, money(amount), "BRL", null);
    }

    private void event(Fixture f, String id, String amount) {
        refunds.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO, details(f, id, amount), "Synthetic refund event");
    }

    private void fullProviderState(Fixture f) {
        refunds.reconcilePaymentRefundState(paymentRepository.findById(f.paymentId()).orElseThrow(), null,
                money("92.90"), "Synthetic full provider refund state");
    }

    private void assertClosed(Fixture f) {
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(f.paymentId()).orElseThrow().getStatus());
        assertEquals(8, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityAvailable());
        assertEquals(0, approvedTotal(f).compareTo(money("92.90")));
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from order_status_history where order_id = ?", Integer.class, f.orderId()));
    }

    private BigDecimal approvedTotal(Fixture f) {
        return refundRepository.findAllByPaymentIdAndStoreIdOrderByCreatedAtDesc(f.paymentId(), f.store().getId())
                .stream().filter(Refund::isApproved).map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test void earlierPartialReplayAfterFullRefundMustNotRestoreInventoryTwice() {
        var f = fixture("partial-replay");
        event(f, "synthetic-refund-first", "20.00");
        event(f, "synthetic-refund-last", "72.90");
        assertClosed(f);
        event(f, "synthetic-refund-first", "20.00");
        event(f, "synthetic-refund-last", "72.90");
        assertClosed(f);
    }

    @Test void cumulativeProviderRefundAfterPartialMustCloseOrderWithoutRewritingPartial() {
        var f = fixture("provider-total");
        event(f, "synthetic-partial", "20.00");
        fullProviderState(f);
        assertClosed(f);
        fullProviderState(f);
        event(f, "synthetic-partial", "20.00");
        assertClosed(f);
        var partial = refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "synthetic-partial").orElseThrow();
        assertEquals(money("20.00"), partial.getAmount());
        assertEquals("synthetic-partial", partial.getProviderRefundId());
    }

    private Refund summary(Fixture f) {
        var summaries = refundRepository.findAllByPaymentIdAndStoreIdAndProviderStateSummaryTrue(f.paymentId(), f.store().getId());
        assertEquals(1, summaries.size());
        var result = summaries.getFirst();
        assertNull(result.getProviderRefundId());
        assertTrue(result.isApproved());
        return result;
    }

    @Test void futureIdentifiedRefundMustAbsorbSummaryAndKeepZeroMarker() {
        var f = fixture("future-identity");
        event(f, "future-first", "20.00");
        fullProviderState(f);
        assertEquals(money("72.90"), summary(f).getAmount());
        event(f, "future-last", "72.90");
        assertEquals(money("0.00"), summary(f).getAmount());
        for (int i = 0; i < 2; i++) {
            fullProviderState(f);
            event(f, "future-first", "20.00");
            event(f, "future-last", "72.90");
            assertClosed(f);
            assertEquals(money("0.00"), summary(f).getAmount());
            assertEquals(3, refundRepository.count());
        }
    }

    @Test void fullRealRefundBeforeProviderTotalMustRetainIdentityAndZeroSummary() {
        var f = fixture("identity-first");
        event(f, "full-real", "92.90");
        fullProviderState(f);
        assertEquals(money("0.00"), summary(f).getAmount());
        event(f, "full-real", "92.90");
        assertClosed(f);
        assertEquals(2, refundRepository.count());
        assertEquals(money("92.90"), refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "full-real").orElseThrow().getAmount());
    }

    @Test void fullRealRefundAfterProviderTotalMustAbsorbSummary() {
        var f = fixture("summary-first");
        fullProviderState(f);
        event(f, "full-after-summary", "92.90");
        fullProviderState(f);
        event(f, "full-after-summary", "92.90");
        assertClosed(f);
        assertEquals(money("0.00"), summary(f).getAmount());
        assertEquals(2, refundRepository.count());
    }

    @Test void approvedRefundMustIgnoreOlderPendingAndRejectedStatuses() {
        var f = fixture("old-status");
        event(f, "monotonic", "20.00");
        for (var status : java.util.List.of(RefundStatus.PENDING, RefundStatus.REJECTED)) {
            refunds.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO,
                    new PaymentProviderRefundDetails("monotonic", f.externalPaymentId(), status, money("20.00"), "BRL", null), "Old event");
            assertEquals(RefundStatus.APPROVED, refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "monotonic").orElseThrow().getStatus());
            assertEquals(money("20.00"), approvedTotal(f));
        }
        fullProviderState(f);
        assertClosed(f);
    }

    @Test void knownRefundMustRejectChangedAmountWithoutMutatingLedger() {
        var f = fixture("changed-amount");
        event(f, "immutable-amount", "20.00");
        assertThrows(BusinessException.class, () -> event(f, "immutable-amount", "21.00"));
        assertEquals(money("20.00"), approvedTotal(f));
        assertEquals(OrderStatus.PAID, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
    }

    @Test void knownRefundMustRejectChangedCurrencyWithoutMutatingLedger() {
        var f = fixture("changed-currency");
        event(f, "immutable-currency", "20.00");
        assertThrows(BusinessException.class, () -> refunds.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO,
                new PaymentProviderRefundDetails("immutable-currency", f.externalPaymentId(), RefundStatus.APPROVED, money("20.00"), "USD", null), "Invalid event"));
        assertEquals(money("20.00"), approvedTotal(f));
    }

    @Test void knownRefundMustRejectDifferentPaymentAndStore() {
        var a = fixture("identity-store-a");
        var b = fixture("identity-store-b");
        event(a, "cross-payment-refund", "20.00");
        assertThrows(BusinessException.class, () -> event(b, "cross-payment-refund", "20.00"));
        assertEquals(money("20.00"), approvedTotal(a));
        assertEquals(0, approvedTotal(b).signum());
        assertEquals(PaymentStatus.APPROVED, paymentRepository.findById(b.paymentId()).orElseThrow().getStatus());
    }

    @Test void approvedAmountsExceedingPaymentMustRollbackWithAndWithoutSummary() {
        var f = fixture("overpaid");
        event(f, "over-first", "20.00");
        assertThrows(BusinessException.class, () -> event(f, "over-last", "80.00"));
        assertEquals(1, refundRepository.count());
        assertEquals(money("20.00"), approvedTotal(f));
        fullProviderState(f);
        assertThrows(BusinessException.class, () -> event(f, "over-last", "80.00"));
        assertClosed(f);
        assertEquals(money("72.90"), summary(f).getAmount());
        assertEquals(2, refundRepository.count());
    }

    @Test void independentStoreRefundsMustNotContaminateCumulativeTotal() {
        var a = fixture("scope-a");
        var b = fixture("scope-b");
        event(a, "scope-first", "20.00");
        event(b, "scope-other", "72.90");
        fullProviderState(a);
        assertClosed(a);
        assertEquals(money("72.90"), approvedTotal(b));
        assertEquals(OrderStatus.PAID, orderRepository.findById(b.orderId()).orElseThrow().getStatus());
        assertEquals(6, inventoryItemRepository.findByProductIdAndProductStoreId(b.productId(), b.store().getId()).orElseThrow().getQuantityAvailable());
    }

    @Test void cancelledOrderProviderRefundMustKeepReleasedStockAndCancellation() {
        var f = fixture("cancelled-refund");
        var order = orderRepository.findById(f.orderId()).orElseThrow();
        order.updateStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);
        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow();
        inventory.setQuantities(8, 0);
        inventoryItemRepository.saveAndFlush(inventory);
        fullProviderState(f);
        event(f, "cancelled-real-refund", "92.90");
        fullProviderState(f);
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(f.paymentId()).orElseThrow().getStatus());
        assertEquals(8, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityAvailable());
        assertEquals(0, orderStatusHistoryRepository.count());
    }

    @Test void multipleSummaryMarkersMustFailClosed() {
        var f = fixture("duplicate-summary");
        fullProviderState(f);
        refundRepository.saveAndFlush(Refund.providerStateSummary(paymentRepository.findById(f.paymentId()).orElseThrow(), money("0.00")));
        assertThrows(BusinessException.class, () -> fullProviderState(f));
        assertThrows(BusinessException.class, () -> event(f, "duplicate-marker-real", "20.00"));
        assertEquals(2, refundRepository.count());
        assertEquals(8, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityAvailable());
    }

    @Test void cumulativeStateMustRejectPartialAmountInsteadOfInventingSummary() {
        var f = fixture("invalid-total");
        assertThrows(BusinessException.class, () -> refunds.reconcilePaymentRefundState(paymentRepository.findById(f.paymentId()).orElseThrow(), null,
                money("20.00"), "Not full refund state"));
        assertEquals(0, refundRepository.count());
    }

    @Test void missingIdentityZeroNegativeAndOverpaidIndividualAmountsMustFailClosed() {
        var f = fixture("invalid-individual");
        for (String amount : java.util.List.of("0.00", "-1.00", "93.00", "0.001")) {
            assertThrows(BusinessException.class, () -> event(f, "invalid-refund", amount));
        }
        assertThrows(BusinessException.class, () -> event(f, null, "20.00"));
        assertEquals(0, refundRepository.count());
    }

    @Test void legacyApprovedUnidentifiedRefundMustFailClosedWithoutGuessingFinancialHistory() {
        var f = fixture("legacy-summary");
        var payment = paymentRepository.findById(f.paymentId()).orElseThrow();
        var historical = Refund.requested(f.store(), payment, payment.getOrder(), PaymentProvider.MERCADO_PAGO,
                com.planno.store.payment.refund.domain.RefundType.FULL, money("92.90"), "BRL", "Historical unidentified provider state", null);
        historical.updateProviderState(null, RefundStatus.APPROVED, null);
        refundRepository.saveAndFlush(historical);
        assertThrows(BusinessException.class, () -> event(f, "legacy-new-real", "20.00"));
        assertThrows(BusinessException.class, () -> fullProviderState(f));
        assertEquals(1, refundRepository.count());
        assertEquals(money("92.90"), approvedTotal(f));
        assertEquals(OrderStatus.PAID, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.APPROVED, paymentRepository.findById(f.paymentId()).orElseThrow().getStatus());
        assertEquals(6, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityAvailable());
    }

    @Test void concurrentPartialReplaysAndCumulativeStateMustRestoreInventoryOnce() throws Exception {
        var f = fixture("concurrent-replays");
        event(f, "parallel-first", "20.00");
        event(f, "parallel-last", "72.90");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var tasks = java.util.List.of(
                    executor.submit(() -> { start.await(); event(f, "parallel-first", "20.00"); return null; }),
                    executor.submit(() -> { start.await(); event(f, "parallel-last", "72.90"); return null; }),
                    executor.submit(() -> { start.await(); fullProviderState(f); return null; }));
            start.countDown();
            for (var task : tasks) task.get(15, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertClosed(f);
        assertEquals(money("0.00"), summary(f).getAmount());
    }

    @Test void staleManagedPendingRefundMustNotOverwriteConcurrentApproval() throws Exception {
        var f = fixture("stale-managed-refund");
        var pending = new PaymentProviderRefundDetails("stale-pending", f.externalPaymentId(), RefundStatus.PENDING, money("20.00"), "BRL", null);
        refunds.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO, pending, "Synthetic pending event");
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                var preloaded = refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "stale-pending").orElseThrow();
                assertEquals(RefundStatus.PENDING, preloaded.getStatus());
                var concurrentApproval = executor.submit(() -> event(f, "stale-pending", "20.00"));
                try {
                    concurrentApproval.get(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError("Concurrent approval must commit before the older event", exception);
                }
                assertEquals(RefundStatus.PENDING, preloaded.getStatus(), "Fixture must retain a stale managed entity");
                refunds.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO, pending, "Older pending event");
            });
        }
        assertEquals(RefundStatus.APPROVED, refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "stale-pending").orElseThrow().getStatus());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, paymentRepository.findById(f.paymentId()).orElseThrow().getStatus());
        assertEquals(money("20.00"), approvedTotal(f));
        assertEquals(6, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityAvailable());
    }

    @Test void cumulativeLedgerMustRefreshPreloadedRefundsAfterConcurrentApproval() throws Exception {
        var f = fixture("stale-cumulative-ledger");
        fullProviderState(f);
        refunds.reconcileProviderRefund(PaymentProvider.MERCADO_PAGO,
                new PaymentProviderRefundDetails("stale-ledger-first", f.externalPaymentId(), RefundStatus.PENDING, money("20.00"), "BRL", null), "Pending individual identity");
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(transaction -> {
                var preloaded = refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "stale-ledger-first").orElseThrow();
                assertEquals(money("92.90"), summary(f).getAmount());
                var concurrentApproval = executor.submit(() -> event(f, "stale-ledger-first", "20.00"));
                try {
                    concurrentApproval.get(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new AssertionError("Concurrent approval must commit before the next individual refund", exception);
                }
                assertEquals(RefundStatus.PENDING, preloaded.getStatus());
                event(f, "stale-ledger-last", "72.90");
            });
        }
        assertClosed(f);
        assertEquals(money("0.00"), summary(f).getAmount());
        assertEquals(3, refundRepository.count());
    }

    @Test void earlierPartialMustRespectStockReturnRecordedByAnotherRefundWhenOrderStateDiverges() {
        var f = fixture("divergent-order-replay");
        event(f, "divergent-first", "20.00");
        event(f, "divergent-last", "72.90");
        assertClosed(f);
        assertFalse(refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "divergent-first").orElseThrow().isInventoryRestored());
        assertTrue(refundRepository.findByProviderAndProviderRefundId(PaymentProvider.MERCADO_PAGO, "divergent-last").orElseThrow().isInventoryRestored());
        jdbcTemplate.update("update orders set status = 'PAID' where id = ?", f.orderId());
        jdbcTemplate.update("update payments set status = 'APPROVED' where id = ?", f.paymentId());
        event(f, "divergent-first", "20.00");
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
        assertEquals(PaymentStatus.REFUNDED, paymentRepository.findById(f.paymentId()).orElseThrow().getStatus());
        assertEquals(money("92.90"), approvedTotal(f));
        assertEquals(8, inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow().getQuantityAvailable());
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from order_status_history where order_id = ?", Integer.class, f.orderId()));
    }

    private static BigDecimal money(String amount) { return new BigDecimal(amount); }
}
