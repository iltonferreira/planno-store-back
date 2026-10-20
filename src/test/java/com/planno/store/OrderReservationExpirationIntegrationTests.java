package com.planno.store;

import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartItem;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.core.auditing.FinancialLock;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.order.config.OrderReservationProperties;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.domain.OrderStatusChangeSource;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.order.dto.UpdateOrderStatusRequest;
import com.planno.store.order.service.OrderReservationExpirationScheduler;
import com.planno.store.order.service.OrderReservationExpirationService;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.service.PaymentInitiationJournalService;
import com.planno.store.payment.service.PaymentProviderPaymentDetails;
import com.planno.store.payment.service.WebhookService;
import com.planno.store.pricing.coupon.domain.Coupon;
import com.planno.store.pricing.domain.DiscountType;
import com.planno.store.pricing.dto.PriceCalculation;
import com.planno.store.shipping.domain.ShippingMethod;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.order-reservation.batch-size=1")
@Import(OrderReservationExpirationIntegrationTests.ClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderReservationExpirationIntegrationTests extends AbstractIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final OffsetDateTime CUTOFF = NOW.atOffset(ZoneOffset.UTC);
    @Autowired OrderReservationExpirationService expiration;
    @Autowired MutableClock clock;
    @Autowired OrderReservationProperties properties;
    @Autowired ApplicationContext context;
    @Autowired PaymentInitiationJournalService journal;
    @Autowired PlatformTransactionManager transactions;
    @Autowired FinancialLock locks;
    @Autowired WebhookService webhook;

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary @Qualifier("orderReservationClock")
        MutableClock testReservationClock() { return new MutableClock(); }
    }
    static final class MutableClock extends Clock {
        private volatile Instant instant = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        public Instant instant() { return instant; }
    }
    @BeforeEach void resetClock() { clock.instant = NOW; }

    private record Fixture(Store store, Product product, Order order, UUID couponId) {}
    private Fixture fixture(String slug, OffsetDateTime deadline, OrderStatus status, boolean coupon) {
        Store store = createStore(slug);
        Product product = productRepository.saveAndFlush(Product.create(store, null, "Synthetic item", "item",
                "SKU-ITEM", "Synthetic", ProductStatus.ACTIVE, BigDecimal.TEN, null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 10, 2));
        UUID couponId = null;
        if (coupon) {
            Coupon c = Coupon.create(store, "SYNTHETIC", null, DiscountType.FIXED, BigDecimal.ONE,
                    null, null, CUTOFF.minusDays(1), CUTOFF.plusDays(1), 10, true, false);
            c.incrementUsage();
            couponId = couponRepository.saveAndFlush(c).getId();
        }
        Order order = Order.create(store, UUID.randomUUID(), "EXP-" + UUID.randomUUID().toString().substring(0, 12),
                status, new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO, couponId, null,
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"),
                "Synthetic customer", "synthetic@example.com", null, UUID.randomUUID(), "Shipping", "FIXED", 1);
        if (deadline != null) order.initializeReservationExpiry(deadline);
        order.addItem(OrderItem.create(order, product, product.getName(), product.getSlug(), product.getSku(),
                2, BigDecimal.TEN, new BigDecimal("20.00")));
        return new Fixture(store, product, orderRepository.saveAndFlush(order), couponId);
    }
    private Fixture fixture(String slug) { return fixture(slug, CUTOFF, OrderStatus.WAITING_PAYMENT, false); }
    private Payment payment(Fixture f, PaymentStatus status) { return payment(f, f.store(), status); }
    private Payment payment(Fixture f, Store paymentStore, PaymentStatus status) {
        Payment p = Payment.create(paymentStore, f.order(), PaymentProvider.MERCADO_PAGO,
                new BigDecimal("20.00"), "BRL", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("20.00"),
                "synthetic-merchant", UUID.randomUUID().toString());
        p.updateProviderState(status, "synthetic-" + UUID.randomUUID(), null, "pix");
        return paymentRepository.saveAndFlush(p);
    }
    private long reserved(Fixture f) {
        return inventoryItemRepository.findByProductIdAndProductStoreId(f.product().getId(), f.store().getId())
                .orElseThrow().getQuantityReserved();
    }
    private OrderStatus state(Fixture f) { return orderRepository.findById(f.order().getId()).orElseThrow().getStatus(); }
    private boolean expire(Fixture f) { return expiration.expire(f.store().getId(), f.order().getId()); }
    private void inTransaction(Runnable work) { new TransactionTemplate(transactions).executeWithoutResult(s -> work.run()); }

    @Test void releasesExactlyAtDeadlineOnceAndKeepsAvailableStockAndFinancialSnapshots() {
        var f = fixture("boundary", CUTOFF, OrderStatus.WAITING_PAYMENT, true);
        clock.instant = NOW.minusNanos(1);
        assertFalse(expire(f));
        assertEquals(2, reserved(f));
        clock.instant = NOW;
        assertTrue(expire(f));
        assertFalse(expire(f));
        assertEquals(0, reserved(f));
        assertEquals(10, inventoryItemRepository.findByProductIdAndProductStoreId(f.product().getId(), f.store().getId())
                .orElseThrow().getQuantityAvailable());
        assertEquals(0, couponRepository.findById(f.couponId()).orElseThrow().getCurrentUses());
        assertEquals(OrderStatus.CANCELLED, state(f));
        assertEquals(new BigDecimal("20.00"), orderRepository.findById(f.order().getId()).orElseThrow().getTotalAmount());
        assertEquals(1, orderStatusHistoryRepository.count());
        assertEquals(OrderStatusChangeSource.RESERVATION_EXPIRATION, orderStatusHistoryRepository.findAll().getFirst().getSource());
    }

    @ParameterizedTest @EnumSource(PaymentStatus.class)
    void onlyAllRejectedCancelledOrFailedPaymentsAllowExpiration(PaymentStatus status) {
        var f = fixture("payment-status");
        payment(f, status);
        payment(f, PaymentStatus.REJECTED); // Inspect every attempt, including the older protecting payment.
        boolean eligible = Set.of(PaymentStatus.REJECTED, PaymentStatus.CANCELLED, PaymentStatus.FAILED).contains(status);
        assertEquals(eligible ? 1 : 0, expiration.findCandidates(CUTOFF, null).size());
        assertEquals(eligible, expire(f));
        assertEquals(eligible ? 0 : 2, reserved(f));
        assertTrue(expiration.findCandidates(CUTOFF, null).isEmpty());
    }

    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = "WAITING_PAYMENT", mode = EnumSource.Mode.EXCLUDE)
    void onlyWaitingPaymentOrdersExpire(OrderStatus status) {
        var f = fixture("order-status", CUTOFF, status, false);
        assertFalse(expire(f));
        assertEquals(2, reserved(f));
        assertTrue(expiration.findCandidates(CUTOFF, null).isEmpty());
    }

    @Test void legacyNullDeadlineAndFutureDeadlineAreExcluded() {
        var legacy = fixture("legacy", null, OrderStatus.WAITING_PAYMENT, false);
        var future = fixture("future", CUTOFF.plusSeconds(1), OrderStatus.WAITING_PAYMENT, false);
        assertFalse(expire(legacy));
        assertFalse(expire(future));
        assertEquals(2, reserved(legacy));
        assertEquals(2, reserved(future));
        assertTrue(expiration.findCandidates(CUTOFF, null).isEmpty());
        assertThrows(IllegalStateException.class, () -> future.order().initializeReservationExpiry(CUTOFF));
    }

    @Test void scopedExpirationDoesNotTouchAnotherStoreAndInconsistentPaymentProtects() {
        var first = fixture("store-first");
        var other = fixture("store-other");
        assertFalse(expiration.expire(other.store().getId(), first.order().getId()));
        payment(first, other.store(), PaymentStatus.REJECTED);
        assertFalse(expire(first));
        assertTrue(expire(other));
        assertEquals(2, reserved(first));
        assertEquals(0, reserved(other));
    }

    @Test void unresolvedInitiationBlocksExpirationWhileLaterCandidatesStillProgress() {
        var first = fixture("journal-first");
        var second = fixture("journal-second");
        UUID firstCandidate = expiration.findCandidates(CUTOFF, null).getFirst().getId();
        Fixture protectedOrder = first.order().getId().equals(firstCandidate) ? first : second;
        Fixture eligibleOrder = protectedOrder == first ? second : first;
        journal.register(protectedOrder.store().getId(), protectedOrder.order().getId(), UUID.randomUUID());
        assertFalse(expire(protectedOrder));
        var scheduler = new OrderReservationExpirationScheduler(expiration, clock, properties);
        scheduler.expireReservations();
        assertEquals(OrderStatus.WAITING_PAYMENT, state(protectedOrder));
        assertEquals(OrderStatus.CANCELLED, state(eligibleOrder));
        assertEquals(2, reserved(protectedOrder));
    }

    @Test void failureOnLastItemRollsBackPreviouslyReleasedStockHistoryAndCoupon() {
        var f = fixture("rollback", CUTOFF, OrderStatus.WAITING_PAYMENT, true);
        Product last = productRepository.saveAndFlush(Product.create(f.store(), null, "Last", "last", "SKU-LAST",
                null, ProductStatus.ACTIVE, BigDecimal.TEN, null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(last, 10, 0));
        inTransaction(() -> {
            var order = orderRepository.findById(f.order().getId()).orElseThrow();
            order.addItem(OrderItem.create(order, last, last.getName(), last.getSlug(), last.getSku(), 1, BigDecimal.TEN, BigDecimal.TEN));
        });
        assertThrows(RuntimeException.class, () -> expire(f));
        assertEquals(2, reserved(f));
        assertEquals(1, couponRepository.findById(f.couponId()).orElseThrow().getCurrentUses());
        assertEquals(OrderStatus.WAITING_PAYMENT, state(f));
        assertEquals(0, orderStatusHistoryRepository.count());
    }

    @Test void schedulerIsolatesFailedOrderAndContinuesToLaterCandidate() {
        var foreignStore = createStore("failed-batch-foreign");
        Coupon foreignCoupon = Coupon.create(foreignStore, "FOREIGN", null, DiscountType.FIXED, BigDecimal.ONE,
                null, null, CUTOFF.minusDays(1), CUTOFF.plusDays(1), 10, true, false);
        foreignCoupon.incrementUsage();
        UUID foreignCouponId = couponRepository.saveAndFlush(foreignCoupon).getId();
        var first = fixture("failed-batch-first");
        var second = fixture("failed-batch-second");
        UUID firstId = expiration.findCandidates(CUTOFF, null).getFirst().getId();
        Fixture failing = first.order().getId().equals(firstId) ? first : second;
        Fixture eligible = failing == first ? second : first;
        jdbcTemplate.update("update orders set coupon_id = ? where id = ?", foreignCouponId, failing.order().getId());

        var scheduler = new OrderReservationExpirationScheduler(expiration, clock, properties);
        scheduler.expireReservations();

        assertEquals(OrderStatus.WAITING_PAYMENT, state(failing));
        assertEquals(2, reserved(failing));
        assertEquals(OrderStatus.CANCELLED, state(eligible));
        assertEquals(0, reserved(eligible));
        assertEquals(1, orderStatusHistoryRepository.count());
        assertEquals(1, couponRepository.findById(foreignCouponId).orElseThrow().getCurrentUses());
        jdbcTemplate.update("update orders set coupon_id = null where id = ?", failing.order().getId());
        scheduler.expireReservations();
        assertEquals(OrderStatus.CANCELLED, state(failing));
        assertEquals(0, reserved(failing));
        assertEquals(2, orderStatusHistoryRepository.count());
    }

    @Test void deletingConsumedCouponThroughAdminApiDoesNotStrandReservation() throws Exception {
        var f = fixture("deleted-consumed-coupon", CUTOFF, OrderStatus.WAITING_PAYMENT, true);
        var admin = createUser("deleted-coupon-admin@example.com", "synthetic-password");
        assignRole(admin, RoleName.ADMIN, f.store());
        assertEquals(1, couponRepository.findById(f.couponId()).orElseThrow().getCurrentUses());
        mockMvc.perform(delete("/api/v1/admin/coupons/{id}", f.couponId())
                .header(STORE_HEADER, f.store().getSlug())
                .header("Authorization", "Bearer " + tokenFor(admin.getEmail())))
                .andExpect(status().isNoContent());
        assertFalse(couponRepository.existsById(f.couponId()));

        assertTrue(expire(f));
        assertFalse(expire(f));
        assertEquals(OrderStatus.CANCELLED, state(f));
        assertEquals(0, reserved(f));
        assertEquals(10, inventoryItemRepository.findByProductIdAndProductStoreId(f.product().getId(), f.store().getId())
                .orElseThrow().getQuantityAvailable());
        assertEquals(1, orderStatusHistoryRepository.count());
        assertEquals(OrderStatusChangeSource.RESERVATION_EXPIRATION,
                orderStatusHistoryRepository.findAll().getFirst().getSource());
    }

    @Test void couponFromAnotherStoreFailsAndRollsBackStock() {
        var other = fixture("foreign-coupon", CUTOFF, OrderStatus.WAITING_PAYMENT, true);
        var f = fixture("bad-coupon");
        jdbcTemplate.update("update orders set coupon_id = ? where id = ?", other.couponId(), f.order().getId());
        assertThrows(RuntimeException.class, () -> expire(f));
        assertEquals(2, reserved(f));
        assertEquals(1, couponRepository.findById(other.couponId()).orElseThrow().getCurrentUses());
        assertEquals(OrderStatus.WAITING_PAYMENT, state(f));
    }

    @Test void twoConcurrentExpirationsReleaseOnce() throws Exception {
        var f = fixture("two-expirations", CUTOFF, OrderStatus.WAITING_PAYMENT, true);
        var successes = new AtomicInteger();
        runTogether(() -> { if (expire(f)) successes.incrementAndGet(); }, () -> { if (expire(f)) successes.incrementAndGet(); });
        assertEquals(1, successes.get());
        assertEquals(0, reserved(f));
        assertEquals(0, couponRepository.findById(f.couponId()).orElseThrow().getCurrentUses());
        assertEquals(1, orderStatusHistoryRepository.count());
    }

    @Test void concurrentManualCancellationAndExpirationReleaseOnce() throws Exception {
        var f = fixture("cancel-race");
        var user = createUser("cancel-race@example.com", "synthetic-password");
        assignRole(user, RoleName.ADMIN, f.store());
        String token = tokenFor(user.getEmail());
        runTogether(() -> expire(f), () -> {
            try {
                int result = mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", f.order().getId())
                        .header(STORE_HEADER, f.store().getSlug()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Synthetic cancellation"))))
                        .andReturn().getResponse().getStatus();
                assertTrue(result == 200 || result == 400);
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        assertEquals(OrderStatus.CANCELLED, state(f));
        assertEquals(0, reserved(f));
        assertEquals(1, orderStatusHistoryRepository.count());
    }

    @Test void approvalRacingExpirationPreservesFinancialApprovalWithoutDoubleStockEffects() throws Exception {
        var f = fixture("approval-race");
        var payment = payment(f, PaymentStatus.REJECTED);
        runTogether(() -> expire(f), () -> approve(f, payment));
        assertEquals(PaymentStatus.APPROVED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertTrue(state(f) == OrderStatus.PAID || state(f) == OrderStatus.CANCELLED);
        assertEquals(0, reserved(f));
        assertEquals(state(f) == OrderStatus.PAID ? 8 : 10,
                inventoryItemRepository.findByProductIdAndProductStoreId(f.product().getId(), f.store().getId()).orElseThrow().getQuantityAvailable());
        assertEquals(1, orderStatusHistoryRepository.count());
        assertEquals(0, refundRepository.count());
    }

    @Test void lateApprovalAfterExpirationKeepsCancelledOrderAndOtherOrdersStock() {
        var f = fixture("late-approval");
        var payment = payment(f, PaymentStatus.REJECTED);
        assertTrue(expire(f));
        var stock = inventoryItemRepository.findByProductIdAndProductStoreId(f.product().getId(), f.store().getId()).orElseThrow();
        stock.reserve(3);
        inventoryItemRepository.saveAndFlush(stock);
        approve(f, payment);
        approve(f, payment);
        assertEquals(OrderStatus.CANCELLED, state(f));
        assertEquals(PaymentStatus.APPROVED, paymentRepository.findById(payment.getId()).orElseThrow().getStatus());
        assertEquals(3, reserved(f));
        assertEquals(10, inventoryItemRepository.findById(stock.getId()).orElseThrow().getQuantityAvailable());
        assertEquals(0, refundRepository.count());
        assertEquals(1, orderStatusHistoryRepository.count());
    }
    private void approve(Fixture f, Payment payment) {
        webhook.reconcilePaymentFromProvider(paymentRepository.findById(payment.getId()).orElseThrow(),
                new PaymentProviderPaymentDetails(payment.getProviderPaymentId(), null, "synthetic-merchant",
                        PaymentStatus.APPROVED, "accredited", new BigDecimal("20.00"), "BRL", "pix", f.order().getId().toString()),
                "synthetic-webhook");
    }

    @Test void schedulerIsDisabledInTestProfileAndStopsBeforeNewWorkAfterContextClose() {
        assertTrue(context.getBeansOfType(OrderReservationExpirationScheduler.class).isEmpty());
        var f = fixture("scheduler-stop");
        var scheduler = new OrderReservationExpirationScheduler(expiration, clock, properties);
        scheduler.stop();
        scheduler.expireReservations();
        assertEquals(OrderStatus.WAITING_PAYMENT, state(f));
        assertEquals(2, reserved(f));
    }

    @Test void checkoutCreationSetsThirtyMinuteDeadlineFromInjectedClock() throws Exception {
        Store store = createStore("created-deadline");
        var product = productRepository.saveAndFlush(Product.create(store, null, "Item", "item", "SKU",
                null, ProductStatus.ACTIVE, BigDecimal.TEN, null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 10, 0));
        Cart cart = Cart.create(store, null, UUID.randomUUID().toString());
        cart.addItem(CartItem.create(cart, product, 2, new PriceCalculation(product.getId(), 2, BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.ZERO, null, null, new BigDecimal("20.00"), new BigDecimal("20.00"), BigDecimal.ZERO)));
        cart = cartRepository.saveAndFlush(cart);
        var shipping = shippingMethodRepository.saveAndFlush(ShippingMethod.create(store, "Fixed", ShippingMethodType.FIXED,
                BigDecimal.ZERO, 1, true));
        var request = new CreateOrderRequest(cart.getId(), shipping.getId(), "Synthetic", "synthetic@example.com", null,
                new AddressRequest("Synthetic", "Street", "1", null, "Center", "City", "SP", "01001000", "BR"));
        String body = mockMvc.perform(post("/api/v1/public/orders").header(STORE_HEADER, store.getSlug())
                .header("X-Cart-Token", cart.getGuestToken()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        assertEquals(CUTOFF.plusMinutes(30), orderRepository.findById(id).orElseThrow().getReservationExpiresAt());
    }

    private void runTogether(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { await(start); first.run(); });
            var b = executor.submit(() -> { await(start); second.run(); });
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        }
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new RuntimeException(exception); }
    }
}
