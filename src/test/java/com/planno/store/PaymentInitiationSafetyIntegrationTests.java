package com.planno.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.planno.store.cart.domain.Cart;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.service.OrderReservationExpirationService;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.dto.CreateTransparentPaymentRequest;
import com.planno.store.payment.service.PaymentProviderClient;
import com.planno.store.payment.service.PaymentCheckoutResult;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentInitiationSafetyIntegrationTests extends AbstractIntegrationTest {
    @MockitoSpyBean PaymentProviderClient provider;
    @Autowired OrderReservationExpirationService expiration;
    @Autowired PlatformTransactionManager transactions;
    private enum Mode { PRO, TRANSPARENT }
    private record Fixture(UUID storeId, UUID productId, UUID orderId, String token) {}

    private Fixture fixture() {
        var store = createStore("initiation-safety");
        var product = productRepository.saveAndFlush(Product.create(store, null, "Synthetic product", "synthetic-product",
                "SKU-INITIATION", "Synthetic fixture", ProductStatus.ACTIVE, new BigDecimal("40.00"), null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 8, 2));
        var cart = Cart.create(store, null, "opaque-owner-" + UUID.randomUUID());
        cart.markCheckedOut();
        cart = cartRepository.saveAndFlush(cart);
        var order = Order.create(store, cart.getId(), "SAFE-" + UUID.randomUUID().toString().substring(0, 12),
                OrderStatus.WAITING_PAYMENT, new BigDecimal("80.00"), new BigDecimal("12.90"), BigDecimal.ZERO,
                null, null, new BigDecimal("92.90"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("92.90"),
                "Synthetic customer", "customer@example.com", null, UUID.randomUUID(), "Synthetic shipping", "FIXED", 4);
        order.initializeReservationExpiry(OffsetDateTime.now().plusMinutes(30));
        order.addItem(OrderItem.create(order, product, product.getName(), product.getSlug(), product.getSku(),
                2, new BigDecimal("40.00"), new BigDecimal("80.00")));
        order = orderRepository.saveAndFlush(order);
        var account = PaymentProviderAccount.create(store, PaymentProvider.MERCADO_PAGO);
        account.connect("synthetic-merchant", "dummy-not-a-real-token", null, OffsetDateTime.now().plusDays(1),
                "dummy-public-key", "read write");
        paymentProviderAccountRepository.saveAndFlush(account);
        return new Fixture(store.getId(), product.getId(), order.getId(), cart.getGuestToken());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(Fixture f, Mode mode) throws Exception {
        Object body = mode == Mode.PRO ? new CreatePaymentRequest(f.orderId())
                : new CreateTransparentPaymentRequest(f.orderId(), "synthetic-card-token", "visa", null, 1,
                        "payer@example.com", null, null, null, null, null);
        return post(mode == Mode.PRO ? "/api/v1/public/payments" : "/api/v1/public/payments/transparent")
                .header(STORE_HEADER, "initiation-safety").header("X-Cart-Token", f.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private void makeExpired(Fixture f) {
        jdbcTemplate.update("update orders set reservation_expires_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), f.orderId());
    }

    private void assertStockReserved(Fixture f) {
        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.storeId()).orElseThrow();
        assertEquals(8, inventory.getQuantityAvailable());
        assertEquals(2, inventory.getQuantityReserved());
        assertEquals(OrderStatus.WAITING_PAYMENT, orderRepository.findById(f.orderId()).orElseThrow().getStatus());
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void externalEffectThenTimeoutSurvivesRollbackAndPreventsExpiryAndRetry(Mode mode) throws Exception {
        var f = fixture();
        var externalEffect = new AtomicReference<PaymentCheckoutResult>();
        org.mockito.stubbing.Answer<PaymentCheckoutResult> uncertain = invocation -> {
            externalEffect.set((PaymentCheckoutResult) invocation.callRealMethod());
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "Synthetic timeout after external effect");
        };
        if (mode == Mode.PRO) doAnswer(uncertain).when(provider).createCheckout(any());
        else doAnswer(uncertain).when(provider).createTransparentPayment(any(), any());
        mockMvc.perform(request(f, mode)).andExpect(status().isBadGateway());
        assertNotNull(externalEffect.get(), "The stub provider performed its external effect before the failure");
        assertEquals(0, paymentRepository.count());
        assertEquals(1, paymentInitiationJournalRepository.count());
        assertFalse(paymentInitiationJournalRepository.findAll().getFirst().isResolved());
        clearInvocations(provider);
        mockMvc.perform(request(f, mode)).andExpect(status().isConflict());
        verifyNoInteractions(provider);
        makeExpired(f);
        assertFalse(expiration.expire(f.storeId(), f.orderId()));
        assertStockReserved(f);
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void knownCommittedPaymentResolvesJournalAndStillProtectsReservation(Mode mode) throws Exception {
        var f = fixture();
        mockMvc.perform(request(f, mode)).andExpect(status().isCreated());
        assertEquals(1, paymentRepository.count());
        assertTrue(paymentInitiationJournalRepository.findAll().getFirst().isResolved());
        makeExpired(f);
        assertFalse(expiration.expire(f.storeId(), f.orderId()));
        assertStockReserved(f);
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void laterTransactionRollbackCannotEraseTheUncertainInitiation(Mode mode) throws Exception {
        var f = fixture();
        var response = new AtomicReference<JsonNode>();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                var content = mockMvc.perform(request(f, mode)).andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString();
                response.set(objectMapper.readTree(content));
            } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertNotNull(response.get());
        assertEquals(0, paymentRepository.count());
        assertEquals(1, paymentInitiationJournalRepository.count());
        assertFalse(paymentInitiationJournalRepository.findAll().getFirst().isResolved());
        makeExpired(f);
        assertFalse(expiration.expire(f.storeId(), f.orderId()));
        assertStockReserved(f);
    }

    @ParameterizedTest @EnumSource(Mode.class)
    void expiredOrderCannotStartPaymentEvenBeforeSchedulerRuns(Mode mode) throws Exception {
        var f = fixture();
        makeExpired(f);
        clearInvocations(provider);
        mockMvc.perform(request(f, mode)).andExpect(status().isBadRequest());
        verifyNoInteractions(provider);
        assertEquals(0, paymentRepository.count());
        assertEquals(0, paymentInitiationJournalRepository.count());
        assertStockReserved(f);
    }

    @Test void approvedTransparentPaymentAwaitingWebhookKeepsReservation() throws Exception {
        var f = fixture();
        doReturn(new PaymentCheckoutResult("synthetic-approved", null, null, PaymentStatus.APPROVED,
                "approved", "visa", "{}"))
                .when(provider).createTransparentPayment(any(), any());
        mockMvc.perform(request(f, Mode.TRANSPARENT)).andExpect(status().isCreated());
        assertTrue(paymentInitiationJournalRepository.findAll().getFirst().isResolved());
        makeExpired(f);
        assertFalse(expiration.expire(f.storeId(), f.orderId()));
        assertStockReserved(f);
    }

    @Test void locallyRejectedZeroTotalMustNotCreateAnUncertainExternalEffect() throws Exception {
        var f = fixture();
        // The allowed full-discount/free-shipping composition has no positive amount to charge.
        jdbcTemplate.update("update orders set shipping_amount = 0, discount_amount = 80, total_amount = 0, seller_net_amount = 0 where id = ?", f.orderId());
        var accounts = mock(com.planno.store.payment.account.service.PaymentProviderAccountService.class);
        var builder = org.springframework.web.client.RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var properties = new com.planno.store.payment.config.PaymentProperties(null, null,
                new com.planno.store.payment.config.PaymentProperties.MercadoPagoProperties(false, null, null, null,
                        null, null, null, null, null, "https://zero-total.example.test", null, false));
        var realAdapter = new com.planno.store.payment.service.MercadoPagoPaymentProviderClient(builder, properties, objectMapper, accounts);
        doAnswer(invocation -> realAdapter.createCheckout(invocation.getArgument(0))).when(provider).createCheckout(any());
        mockMvc.perform(request(f, Mode.PRO)).andExpect(status().isBadRequest());
        server.verify();
        verifyNoInteractions(accounts);
        assertEquals(0, paymentRepository.count());
        makeExpired(f);
        assertAll(
                () -> assertEquals(0, paymentInitiationJournalRepository.count(), "Known local rejection must not be treated as an external uncertainty"),
                () -> assertTrue(expiration.expire(f.storeId(), f.orderId()), "An unpaid checkout must remain eligible for expiry")
        );
    }
}
