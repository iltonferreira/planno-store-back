package com.planno.store;

import com.planno.store.cart.domain.Cart;
import com.planno.store.catalog.domain.Product;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.inventory.domain.InventoryItem;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.dto.CreateTransparentPaymentRequest;
import com.planno.store.payment.service.PaymentProviderClient;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CheckoutAuthorizationIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";
    private static final String CARD_TOKEN = "synthetic-card-token";

    @MockitoSpyBean
    private PaymentProviderClient provider;

    private enum Checkout { PRO, TRANSPARENT }
    private enum DeniedOwner { MISSING, BLANK, UNKNOWN, ACTIVE, OTHER_CHECKED_OUT, OTHER_STORE_CART, OTHER_STORE_HEADER, ADMIN_WITHOUT_CART }

    private record Fixture(Store store, UUID productId, UUID orderId, String cartToken, String adminToken) {}

    static Stream<Arguments> deniedOwners() {
        return Arrays.stream(Checkout.values()).flatMap(checkout ->
                Arrays.stream(DeniedOwner.values()).map(owner -> Arguments.of(checkout, owner)));
    }

    @ParameterizedTest(name = "{0} denies {1} before any payment side effect")
    @MethodSource("deniedOwners")
    void mustOwnTheOriginatingCheckedOutCart(Checkout checkout, DeniedOwner deniedOwner) throws Exception {
        var f = fixture();
        String storeSlug = f.store().getSlug();
        String cartToken = switch (deniedOwner) {
            case MISSING, ADMIN_WITHOUT_CART -> null;
            case BLANK -> "   ";
            case UNKNOWN -> "unknown-opaque-cart-token";
            case ACTIVE -> persistCart(f.store(), false).getGuestToken();
            case OTHER_CHECKED_OUT -> persistCart(f.store(), true).getGuestToken();
            case OTHER_STORE_CART -> persistCart(createStore("checkout-other"), true).getGuestToken();
            case OTHER_STORE_HEADER -> f.cartToken();
        };
        if (deniedOwner == DeniedOwner.OTHER_STORE_HEADER) {
            storeSlug = createStore("checkout-other").getSlug();
        }
        var request = paymentRequest(checkout, f.orderId(), storeSlug, cartToken);
        if (deniedOwner == DeniedOwner.ADMIN_WITHOUT_CART) {
            request.header("Authorization", "Bearer " + f.adminToken());
        }
        clearInvocations(provider);

        var response = mockMvc.perform(request).andReturn().getResponse();

        int expectedStatus = switch (deniedOwner) {
            case MISSING, BLANK, ADMIN_WITHOUT_CART -> 400;
            default -> 404;
        };
        assertAll(
                () -> assertEquals(expectedStatus, response.getStatus()),
                () -> verifyNoInteractions(provider),
                () -> assertEquals(0, paymentRepository.count()),
                () -> assertUnchangedOrderAndStock(f),
                () -> assertNoPrivateFields(response.getContentAsString())
        );
    }

    @ParameterizedTest
    @EnumSource(Checkout.class)
    void anonymousOwnerCanPayWithAnOpaqueTrimmedCartToken(Checkout checkout) throws Exception {
        var f = fixture();
        clearInvocations(provider);

        var response = mockMvc.perform(paymentRequest(checkout, f.orderId(), f.store().getSlug(), "  " + f.cartToken() + "  "))
                .andExpect(status().isCreated()).andReturn().getResponse();

        assertEquals(1, paymentRepository.count());
        var payment = paymentRepository.findAll().getFirst();
        assertEquals(f.orderId(), payment.getOrder().getId());
        assertEquals(f.store().getId(), payment.getStore().getId());
        assertEquals(new BigDecimal("92.90"), payment.getAmount());
        assertUnchangedOrderAndStock(f);
        assertNoPrivateFields(response.getContentAsString());
        assertFalse(response.getContentAsString().contains(f.cartToken()));
        if (checkout == Checkout.PRO) {
            verify(provider).createCheckout(any());
            verify(provider, never()).createTransparentPayment(any(), any());
        } else {
            verify(provider).createTransparentPayment(any(), any());
            verify(provider, never()).createCheckout(any());
            assertFalse(payment.getRawInitResponse().contains(CARD_TOKEN));
        }

        // Administrative reads remain store-scoped and do not require a guest cart token.
        mockMvc.perform(get("/api/v1/admin/orders/{id}", f.orderId())
                        .header(STORE_HEADER, f.store().getSlug())
                        .header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder paymentRequest(
            Checkout checkout, UUID orderId, String storeSlug, String cartToken) throws Exception {
        String path = checkout == Checkout.PRO ? "/api/v1/public/payments" : "/api/v1/public/payments/transparent";
        Object body = checkout == Checkout.PRO ? new CreatePaymentRequest(orderId)
                : new CreateTransparentPaymentRequest(orderId, CARD_TOKEN, "visa", null, 1,
                        "payer@example.com", "Synthetic", "Payer", null, null, null);
        var request = post(path).header(STORE_HEADER, storeSlug).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (cartToken != null) request.header(CART_HEADER, cartToken);
        return request;
    }

    private Fixture fixture() {
        Store store = createStore("checkout-owner");
        var admin = createUser("checkout-admin@example.com", "synthetic-password");
        assignRole(admin, RoleName.ADMIN, store);
        var product = productRepository.saveAndFlush(Product.create(store, null, "Synthetic product", "checkout-product",
                "SKU-CHECKOUT", "Synthetic fixture", ProductStatus.ACTIVE, new BigDecimal("40.00"), null));
        inventoryItemRepository.saveAndFlush(InventoryItem.create(product, 8, 2));
        Cart cart = persistCart(store, true);
        var order = Order.create(store, cart.getId(), "CHK-" + UUID.randomUUID().toString().substring(0, 12),
                OrderStatus.WAITING_PAYMENT, new BigDecimal("80.00"), new BigDecimal("12.90"), BigDecimal.ZERO,
                null, null, new BigDecimal("92.90"), new BigDecimal("2.50"), new BigDecimal("2.32"), new BigDecimal("90.58"),
                "Synthetic customer", "customer@example.com", "12345678900", UUID.randomUUID(), "Synthetic shipping", "FIXED", 4);
        order.addItem(OrderItem.create(order, product, product.getName(), product.getSlug(), product.getSku(),
                2, new BigDecimal("40.00"), new BigDecimal("80.00")));
        order = orderRepository.saveAndFlush(order);
        addressRepository.saveAndFlush(com.planno.store.shipping.domain.Address.forOrder(store, cart.getId(), order,
                "Synthetic customer", "Synthetic street", "1", null, "Center", "City", "SP", "01001000", "BR"));
        var account = PaymentProviderAccount.create(store, PaymentProvider.MERCADO_PAGO);
        account.connect("synthetic-checkout-merchant", "dummy-not-a-real-token", null, OffsetDateTime.now().plusDays(1),
                "dummy-public-key", "read write");
        paymentProviderAccountRepository.saveAndFlush(account);
        return new Fixture(store, product.getId(), order.getId(), cart.getGuestToken(), tokenFor(admin.getEmail()));
    }

    private Cart persistCart(Store store, boolean checkedOut) {
        var cart = Cart.create(store, null, "opaque-owner-" + UUID.randomUUID());
        if (checkedOut) cart.markCheckedOut();
        return cartRepository.saveAndFlush(cart);
    }

    private void assertUnchangedOrderAndStock(Fixture f) {
        var order = orderRepository.findById(f.orderId()).orElseThrow();
        var stock = inventoryItemRepository.findByProductIdAndProductStoreId(f.productId(), f.store().getId()).orElseThrow();
        assertEquals(OrderStatus.WAITING_PAYMENT, order.getStatus());
        assertEquals(new BigDecimal("92.90"), order.getTotalAmount());
        assertEquals(new BigDecimal("2.32"), order.getCommissionAmount());
        assertEquals(0, orderStatusHistoryRepository.count());
        assertEquals(8, stock.getQuantityAvailable());
        assertEquals(2, stock.getQuantityReserved());
    }

    private void assertNoPrivateFields(String response) {
        for (String sensitive : new String[]{"customerName", "customerEmail", "customerDocument", "customer@example.com",
                "Synthetic customer", "12345678900", "payer@example.com", "payerEmail", "commissionRate", "commissionAmount",
                "sellerNetAmount", "rawProviderResponse", "access_token", "cardNumber", "securityCode", CARD_TOKEN}) {
            assertFalse(response.contains(sensitive), "Public payment responses must omit " + sensitive);
        }
    }
}
