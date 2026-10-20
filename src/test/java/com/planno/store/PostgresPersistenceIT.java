package com.planno.store;

import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.WebhookEvent;
import com.planno.store.payment.repository.WebhookEventRepository;
import com.planno.store.payment.webhook.service.WebhookEventLifecycleService;
import com.planno.store.core.auditing.FinancialLock;
import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartItem;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.pricing.dto.PriceCalculation;
import com.planno.store.shipping.domain.ShippingMethod;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import com.planno.store.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Opt-in PostgreSQL-only tests. The postgres-integration profile supplies an isolated cluster. */
class PostgresPersistenceIT extends AbstractIntegrationTest {
    @Autowired WebhookEventLifecycleService lifecycle;
    @Autowired FinancialLock financialLock;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean WebhookEventRepository events;

    @Test
    void realPostgresHasAllMigrationsAndV26ToV28CatalogContracts() throws Exception {
        try (var connection = PostgresTestDatabase.connect()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertTrue(connection.getMetaData().getDatabaseMajorVersion() >= 17);
            assertEquals(28, scalar(connection, "select count(*) from flyway_schema_history where success and type='SQL'"));
            assertEquals(3, scalar(connection, "select count(*) from flyway_schema_history where success and version in ('26','27','28')"));
            assertEquals(1, scalar(connection, "select count(*) from information_schema.columns where table_schema='public' and table_name='refunds' and column_name='provider_state_summary' and is_nullable='NO' and column_default='false'"));
            assertEquals(1, scalar(connection, "select count(*) from information_schema.columns where table_schema='public' and table_name='orders' and column_name='reservation_expires_at' and is_nullable='YES' and data_type='timestamp with time zone'"));
            assertEquals(3, scalar(connection, "select count(*) from pg_indexes where schemaname='public' and indexname in ('idx_orders_reservation_expiration','idx_payment_initiation_journal_order','uq_orders_store_cart')"));
            assertEquals(0, scalar(connection, "select count(*) from pg_constraint where conrelid='payment_initiation_journal'::regclass and contype='f'"), "Journal must commit while uncommitted or locked parent rows remain in another transaction");
            assertEquals(1, scalar(connection, "select count(*) from pg_constraint where conrelid='payment_initiation_journal'::regclass and contype='p'"));
            assertEquals(2, scalar(connection, "select count(*) from pg_constraint where conname in ('fk_refunds_payment','uk_webhook_events_provider_external')"));
            assertEquals(0, scalar(connection, "select count(*) from pg_roles where rolname=current_user and (rolsuper or rolcreatedb or rolcreaterole)"));
        }
    }

    @Test
    void concurrentCheckoutRequestsCreateOneOrderAndReserveStockOnce() throws Exception {
        Store store = createStore("pg-checkout-race");
        var product = productRepository.saveAndFlush(Product.create(store, null, "Concurrent product", "concurrent-product",
                "PG-RACE", null, ProductStatus.ACTIVE, new BigDecimal("25.00"), null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 5, 0));
        var shipping = shippingMethodRepository.saveAndFlush(ShippingMethod.create(store, "Standard",
                ShippingMethodType.FIXED, new BigDecimal("5.00"), 3, true));
        String cartToken = UUID.randomUUID().toString();
        Cart cart = Cart.create(store, null, cartToken);
        cart.addItem(CartItem.create(cart, product, 1, new PriceCalculation(product.getId(), 1,
                new BigDecimal("25.00"), new BigDecimal("25.00"), BigDecimal.ZERO,
                null, null, new BigDecimal("25.00"), new BigDecimal("25.00"), BigDecimal.ZERO)));
        cart = cartRepository.saveAndFlush(cart);
        var request = new CreateOrderRequest(cart.getId(), shipping.getId(), "Concurrent buyer", "race@example.test", null,
                new AddressRequest("Concurrent buyer", "Main Street", "1", null, "Center", "City", "SP", "01001000", "BR"));
        String body = objectMapper.writeValueAsString(request);
        var gate = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> postCheckoutTogether(store.getSlug(), cartToken, body, gate));
            Future<String> second = executor.submit(() -> postCheckoutTogether(store.getSlug(), cartToken, body, gate));
            String firstId = first.get(20, TimeUnit.SECONDS);
            String secondId = second.get(20, TimeUnit.SECONDS);
            assertEquals(firstId, secondId);
        }
        assertEquals(1, orderRepository.count());
        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(product.getId(), store.getId()).orElseThrow();
        assertEquals(1, inventory.getQuantityReserved());
    }

    private String postCheckoutTogether(String storeSlug, String cartToken, String body, CyclicBarrier gate) {
        try {
            gate.await(10, TimeUnit.SECONDS);
            var response = mockMvc.perform(post("/api/v1/public/orders")
                            .header(STORE_HEADER, storeSlug)
                            .header("X-Cart-Token", cartToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse();
            assertEquals(201, response.getStatus());
            return objectMapper.readTree(response.getContentAsString()).get("id").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent checkout request failed", exception);
        }
    }

    @Test
    void upgradeFromV25PreservesSyntheticLegacyStatesAndDoesNotBackfillExpiry() throws Exception {
        var database = PostgresTestDatabase.createDatabase();
        try (var connection = PostgresTestDatabase.connect(database)) {
            var source = new SingleConnectionDataSource(connection, true);
            var before = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("25").load();
            assertEquals(25, before.migrate().migrationsExecuted);
            var store = UUID.randomUUID();
            insertStore(connection, store, "pg-legacy");
            var paid = UUID.randomUUID();
            var paidCart = UUID.randomUUID();
            var cancelled = UUID.randomUUID();
            var pending = UUID.randomUUID();
            var uncertain = UUID.randomUUID();
            insertOrder(connection, store, paid, "PAID", paidCart);
            insertOrder(connection, store, cancelled, "CANCELLED");
            insertOrder(connection, store, pending, "WAITING_PAYMENT");
            insertOrder(connection, store, uncertain, "WAITING_PAYMENT");
            var product = UUID.randomUUID();
            update(connection, "insert into products (id,store_id,name,slug,sku,status,base_price) values (?,?,'Synthetic legacy product','legacy-product','LEGACY-PG','ACTIVE',100)", product, store);
            update(connection, "insert into inventory_items (id,product_id,quantity_available,quantity_reserved) values (?,?,8,3)", UUID.randomUUID(), product);
            update(connection, "insert into order_items (id,order_id,product_id,product_name_snapshot,product_slug_snapshot,sku_snapshot,quantity,unit_price_snapshot,total_price_snapshot) values (?,?,?,'Synthetic legacy product','legacy-product','LEGACY-PG',2,50,100)", UUID.randomUUID(), pending, product);
            update(connection, "insert into order_items (id,order_id,product_id,product_name_snapshot,product_slug_snapshot,sku_snapshot,quantity,unit_price_snapshot,total_price_snapshot) values (?,?,?,'Synthetic legacy product','legacy-product','LEGACY-PG',1,100,100)", UUID.randomUUID(), uncertain, product);
            var approvedPayment = UUID.randomUUID();
            var pendingPayment = UUID.randomUUID();
            insertPayment(connection, store, paid, approvedPayment, "APPROVED");
            insertPayment(connection, store, pending, pendingPayment, "PENDING");
            update(connection, "insert into refunds (id,created_at,updated_at,store_id,payment_id,order_id,provider,type,amount,currency,status,requested_at) values (?,now(),now(),?,?,?,'MERCADO_PAGO','PARTIAL',10,'BRL','APPROVED',now())", UUID.randomUUID(), store, approvedPayment, paid);
            var after = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
            assertEquals(3, after.migrate().migrationsExecuted);
            after.validate();
            var duplicateCart = assertThrows(SQLException.class,
                    () -> insertOrder(connection, store, UUID.randomUUID(), "WAITING_PAYMENT", paidCart));
            assertEquals("23505", duplicateCart.getSQLState(), "V28 must enforce one order per store and cart");
            assertEquals(4, scalar(connection, "select count(*) from orders where reservation_expires_at is null"));
            assertEquals(1, scalar(connection, "select count(*) from orders where status='PAID'"));
            assertEquals(1, scalar(connection, "select count(*) from orders where status='CANCELLED'"));
            assertEquals(2, scalar(connection, "select count(*) from orders where status='WAITING_PAYMENT'"));
            assertEquals(1, scalar(connection, "select count(*) from payments where status='APPROVED'"));
            assertEquals(1, scalar(connection, "select count(*) from payments where status='PENDING'"));
            assertEquals(1, scalar(connection, "select count(*) from refunds where not provider_state_summary and not inventory_restored and amount=10"));
            assertEquals(1, scalar(connection, "select count(*) from inventory_items where quantity_available=8 and quantity_reserved=3 and version=0"), "Additive upgrade preserves old reservations until a separately authorized reconciliation policy");
            assertEquals(0, scalar(connection, "select count(*) from payment_initiation_journal"), "Migration cannot infer absent legacy external-attempt evidence");
            update(connection, "insert into payment_initiation_journal (id,store_id,order_id,created_at) values (?,?,?,now())", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            assertEquals(1, scalar(connection, "select count(*) from payment_initiation_journal where not resolved"));
        }
    }

    @Test
    void foreignKeyAndUniqueFailuresAbortOnlyTheirTransactionsAndRecoverAfterRollback() throws Exception {
        var valid = createStore("pg-constraints");
        try (var connection = PostgresTestDatabase.connect()) {
            connection.setAutoCommit(false);
            var fk = assertThrows(SQLException.class, () -> update(connection,
                    "insert into webhook_events (id,store_id,provider,external_event_id,event_type,payload) values (?,?,'MERCADO_PAGO',?,'payment','{}')",
                    UUID.randomUUID(), UUID.randomUUID(), "missing-store-" + UUID.randomUUID()));
            assertEquals("23503", fk.getSQLState());
            var aborted = assertThrows(SQLException.class, () -> scalar(connection, "select count(*) from stores"));
            assertEquals("25P02", aborted.getSQLState(), "A failed PostgreSQL transaction cannot recover by querying again");
            connection.rollback();
            insertEvent(connection, valid.getId(), "same-external-id");
            connection.commit();
            var duplicate = assertThrows(SQLException.class, () -> insertEvent(connection, valid.getId(), "same-external-id"));
            assertEquals("23505", duplicate.getSQLState());
            connection.rollback();
            assertEquals(1, scalar(connection, "select count(*) from webhook_events"));
            assertEquals(1, scalar(connection, "select count(*) from stores"));
            insertEvent(connection, valid.getId(), "new-after-rollback");
            connection.commit();
            assertEquals(2, scalar(connection, "select count(*) from webhook_events"));
        }
    }

    @Test
    void tenantScopedRepositoriesDoNotReadOtherStoresRowsOnPostgres() throws Exception {
        var owner = createStore("pg-owner");
        var other = createStore("pg-other");
        var order = UUID.randomUUID();
        var cart = UUID.randomUUID();
        try (var connection = PostgresTestDatabase.connect()) {
            insertOrder(connection, owner.getId(), order, "WAITING_PAYMENT", cart);
        }
        assertTrue(orderRepository.findByIdAndStoreId(order, owner.getId()).isPresent());
        assertTrue(orderRepository.findByIdAndStoreIdAndCartId(order, owner.getId(), cart).isPresent());
        assertTrue(orderRepository.findByIdAndStoreId(order, other.getId()).isEmpty());
        assertTrue(orderRepository.findByIdAndStoreIdAndCartId(order, other.getId(), cart).isEmpty());
        assertTrue(orderRepository.findByIdAndStoreIdAndCartId(order, owner.getId(), UUID.randomUUID()).isEmpty());
    }

    @Test
    void concurrentDuplicateWebhookRegistrationReturnsOneCommittedIdentity() throws Exception {
        var external = "pg-race-" + UUID.randomUUID();
        var gate = new CyclicBarrier(2);
        var synchronizedReads = new AtomicInteger();
        // Spring's repository proxy spy uses a delegating default Answer rather than a copied spy instance.
        // Invoke that original Answer directly so this derived query still reaches PostgreSQL.
        var realQuery = mockingDetails(events).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Optional<WebhookEvent> found = (Optional<WebhookEvent>) realQuery.answer(invocation);
            if (found.isEmpty() && synchronizedReads.incrementAndGet() <= 2) gate.await(10, TimeUnit.SECONDS);
            return found;
        }).when(events).findByProviderAndExternalEventId(PaymentProvider.MERCADO_PAGO, external);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<UUID> first = executor.submit(() -> lifecycle.ensureEvent(PaymentProvider.MERCADO_PAGO, external, "payment", "{}"));
            Future<UUID> second = executor.submit(() -> lifecycle.ensureEvent(PaymentProvider.MERCADO_PAGO, external, "payment", "{}"));
            UUID[] identities = new UUID[2];
            assertAll("Both real concurrent transactions must complete idempotently",
                    () -> identities[0] = first.get(20, TimeUnit.SECONDS),
                    () -> identities[1] = second.get(20, TimeUnit.SECONDS));
            assertNotNull(identities[0]);
            assertEquals(identities[0], identities[1]);
            assertEquals(1, events.count());
            assertEquals(identities[0], events.findByProviderAndExternalEventId(PaymentProvider.MERCADO_PAGO, external).orElseThrow().getId());
        } finally {
            reset(events);
        }
    }

    @Test
    void orderLockMustRefreshCommittedStateAfterWaitingForConcurrentWriter() throws Exception {
        var store = createStore("pg-lock-refresh");
        var order = UUID.randomUUID();
        var readerPid = new AtomicInteger();
        try (var writer = PostgresTestDatabase.connect(); var observer = PostgresTestDatabase.connect();
                var executor = Executors.newSingleThreadExecutor()) {
            insertOrder(writer, store.getId(), order, "WAITING_PAYMENT");
            writer.setAutoCommit(false);
            try (var lock = writer.prepareStatement("select id from orders where id=? for update")) {
                lock.setObject(1, order);
                try (var result = lock.executeQuery()) { assertTrue(result.next()); }
            }
            update(writer, "update orders set status='CANCELLED' where id=?", order);
            Future<OrderStatus> observed = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                readerPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                var before = orderRepository.findByIdAndStoreId(order, store.getId()).orElseThrow();
                assertEquals(OrderStatus.WAITING_PAYMENT, before.getStatus(), "Uncommitted writer changes must be invisible");
                return financialLock.order(before).getStatus();
            }));
            try {
                awaitReaderLockWait(observer, readerPid);
                writer.commit();
                assertEquals(OrderStatus.CANCELLED, observed.get(10, TimeUnit.SECONDS),
                        "Lock acquisition must precede the authoritative refresh; an old MVCC snapshot is unsafe");
            } finally {
                // Also unblock the worker when a diagnostic assertion fails.
                writer.rollback();
            }
        }
    }

    @Test
    void optionalStoreSearchTextKeepsNullBlankAndCaseInsensitiveSemanticsOnPostgres() {
        var alpha = createStore("pg-alpha");
        var beta = createStore("pg-beta");
        assertEquals(2, storeRepository.search(null, null).size());
        assertEquals(2, storeRepository.search(null, "").size());
        assertEquals(java.util.List.of(alpha.getId()), storeRepository.search(null, "PG-ALpHa").stream().map(s -> s.getId()).toList());
        assertEquals(java.util.List.of(beta.getId()), storeRepository.search(null, "PG-BeTA@example.com").stream().map(s -> s.getId()).toList());
        assertTrue(storeRepository.search(null, "not-a-synthetic-store").isEmpty());
    }

    @Test
    void optionalWebhookSearchTextPreservesCaseAndTenantIsolationOnPostgres() {
        var owner = createStore("pg-event-owner");
        var other = createStore("pg-event-other");
        var ownEvent = events.saveAndFlush(WebhookEvent.create(owner, PaymentProvider.MERCADO_PAGO,
                "pg-owner-event", "payment.updated", "{}"));
        events.saveAndFlush(WebhookEvent.create(other, PaymentProvider.MERCADO_PAGO,
                "pg-other-event", "payment.updated", "{}"));
        events.saveAndFlush(WebhookEvent.create(owner, PaymentProvider.MERCADO_PAGO,
                "pg-owner-refund", "refund.updated", "{}"));
        assertEquals(2, events.searchByStore(owner.getId(), null, null, null, null, null).size());
        assertEquals(java.util.List.of(ownEvent.getId()), events.searchByStore(owner.getId(), null, false,
                "PaYmEnT.UpDaTeD", null, null).stream().map(WebhookEvent::getId).toList());
        assertTrue(events.searchByStore(owner.getId(), null, null, "", null, null).isEmpty());
        assertTrue(events.searchByStore(owner.getId(), null, null, "missing.event", null, null).isEmpty());
        var now = java.time.OffsetDateTime.now().withOffsetSameInstant(java.time.ZoneOffset.ofHours(-3));
        assertEquals(2, events.searchByStore(owner.getId(), null, null, null,
                now.minusMinutes(5), now.plusMinutes(5)).size());
        assertEquals(2, events.searchByStore(owner.getId(), null, null, null,
                now.minusMinutes(5), null).size());
        assertEquals(2, events.searchByStore(owner.getId(), null, null, null,
                null, now.plusMinutes(5)).size());
        assertTrue(events.searchByStore(owner.getId(), null, null, null,
                now.plusMinutes(5), null).isEmpty());
        assertTrue(events.searchByStore(owner.getId(), null, null, null,
                null, now.minusMinutes(5)).isEmpty());
        assertEquals(3, events.searchPlatform(null, null, null, null).size());
        assertEquals(2, events.searchPlatform(null, PaymentProvider.MERCADO_PAGO, false, "PAYMENT.UPDATED").size());
        assertEquals(1, events.searchPlatform(other.getId(), null, null, "payment.updated").size());
    }

    private static void awaitReaderLockWait(Connection observer, AtomicInteger readerPid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (readerPid.get() != 0) {
                try (var query = observer.prepareStatement("select count(*) from pg_stat_activity where pid=? and wait_event_type='Lock'")) {
                    query.setInt(1, readerPid.get());
                    try (var result = query.executeQuery()) {
                        assertTrue(result.next());
                        if (result.getInt(1) == 1) return;
                    }
                }
            }
            Thread.sleep(10);
        }
        fail("Reader did not reach a real PostgreSQL lock wait while the writer transaction remained uncommitted");
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static void update(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static void insertStore(Connection connection, UUID store, String slug) throws SQLException {
        update(connection, "insert into stores (id,name,slug,status,contact_email,currency,timezone) values (?,'Synthetic legacy store',?,'ACTIVE','fixture@example.com','BRL','UTC')", store, slug);
    }

    private static void insertOrder(Connection connection, UUID store, UUID order, String state) throws SQLException {
        insertOrder(connection, store, order, state, UUID.randomUUID());
    }

    private static void insertOrder(Connection connection, UUID store, UUID order, String state, UUID cart) throws SQLException {
        update(connection, "insert into orders (id,store_id,cart_id,code,status,subtotal_amount,shipping_amount,discount_amount,total_amount,customer_name,customer_email,shipping_method_id,shipping_method_name,shipping_method_type,shipping_estimated_days) values (?,?,?,?,?,100,0,0,100,'Synthetic customer','fixture@example.com',?,'Synthetic shipping','FIXED',1)", order, store, cart, "PG-" + order, state, UUID.randomUUID());
    }

    private static void insertPayment(Connection connection, UUID store, UUID order, UUID payment, String state) throws SQLException {
        update(connection, "insert into payments (id,store_id,order_id,provider,status,amount,currency,idempotency_key) values (?,?,?,'MERCADO_PAGO',?,100,'BRL',?)", payment, store, order, state, payment.toString());
    }

    private static void insertEvent(Connection connection, UUID store, String external) throws SQLException {
        update(connection, "insert into webhook_events (id,store_id,provider,external_event_id,event_type,payload) values (?,?,'MERCADO_PAGO',?,'payment','{}')", UUID.randomUUID(), store, external);
    }
}
