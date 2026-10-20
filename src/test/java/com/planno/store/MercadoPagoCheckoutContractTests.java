package com.planno.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.catalog.domain.Product;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.service.MercadoPagoPaymentProviderClient;
import com.planno.store.store.domain.Store;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// Constructs the real HTTP adapter directly; no Spring context, database or external HTTP.
class MercadoPagoCheckoutContractTests {
    private static final String API_URL = "https://mercado-pago.example.test";
    private final ObjectMapper mapper = new ObjectMapper();
    private PaymentProviderAccountService accounts;
    private MockRestServiceServer server;
    private MercadoPagoPaymentProviderClient adapter;

    @BeforeEach
    void setUp() {
        accounts = mock(PaymentProviderAccountService.class);
        var account = mock(PaymentProviderAccount.class);
        when(accounts.requireConnectedAccountForCurrentStore()).thenReturn(account);
        when(accounts.decryptAccessToken(account)).thenReturn("synthetic-token");
        var properties = new PaymentProperties(null, null, new PaymentProperties.MercadoPagoProperties(
                false, null, null, null, null, "https://store.example.test/webhook",
                "https://store.example.test/success", "https://store.example.test/pending",
                "https://store.example.test/failure", API_URL, "https://auth.example.test", false));
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MercadoPagoPaymentProviderClient(builder, properties, mapper, accounts);
    }

    static Stream<Arguments> orderTotals() {
        return Stream.of(
                Arguments.of("quantity without shipping", 2, "40.00", "80.00", "80.00", "0.00", "0.00", "80.00"),
                Arguments.of("shipping", 2, "40.00", "80.00", "80.00", "12.90", "0.00", "92.90"),
                Arguments.of("coupon and shipping", 2, "40.00", "80.00", "80.00", "12.90", "10.00", "82.90"),
                Arguments.of("order discount without shipping", 3, "10.00", "30.00", "30.00", "0.00", "9.99", "20.01"),
                Arguments.of("item promotion already in snapshot", 2, "30.00", "60.00", "80.00", "12.90", "20.00", "72.90"),
                Arguments.of("free shipping discount", 2, "40.00", "80.00", "80.00", "12.90", "12.90", "80.00"),
                Arguments.of("unit and line rounding differ", 3, "6.67", "20.00", "30.00", "12.90", "10.00", "32.90"),
                Arguments.of("persisted monetary rounding", 3, "10.005", "30.02", "30.015", "0.00", "0.00", "30.015")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("orderTotals")
    void checkoutChargeEqualsBothFinancialSnapshots(String scenario, int quantity, String unitPrice,
            String lineTotal, String subtotal, String shipping, String discount, String total) throws Exception {
        Payment payment = payment(quantity, unitPrice, lineTotal, subtotal, shipping, discount, total);
        JsonNode payload = createAndCapture(payment);

        BigDecimal charged = BigDecimal.ZERO;
        for (JsonNode item : payload.path("items")) {
            BigDecimal price = item.path("unit_price").decimalValue();
            assertTrue(price.signum() > 0, "Provider items must have a positive price");
            assertTrue(price.scale() <= 2, "Provider prices must fit cents");
            assertTrue(item.path("quantity").asInt() > 0);
            assertEquals(payment.getCurrency(), item.path("currency_id").asText());
            charged = charged.add(price.multiply(BigDecimal.valueOf(item.path("quantity").asInt())));
        }
        assertEquals(payment.getOrder().getTotalAmount(), charged.setScale(2), scenario);
        assertEquals(payment.getAmount(), charged.setScale(2), scenario);
        assertEquals(0, payment.getCommissionAmount().compareTo(payload.path("marketplace_fee").decimalValue()));
        assertEquals(payment.getOrder().getCode(), payload.path("external_reference").asText());
        assertEquals(payment.getId().toString(), payload.path("metadata").path("internal_payment_id").asText());
        assertEquals(payment.getOrder().getId().toString(), payload.path("metadata").path("order_id").asText());
        assertEquals(payment.getStore().getId().toString(), payload.path("metadata").path("store_id").asText());
        assertEquals("synthetic-merchant", payload.path("metadata").path("provider_user_id").asText());
        assertEquals("https://store.example.test/checkout/retorno?status=success", payload.path("back_urls").path("success").asText());
        assertEquals("approved", payload.path("auto_return").asText());
    }

    @Test
    void differingPaymentAndOrderTotalsStopBeforeCredentialLookupOrHttp() {
        Payment payment = payment(2, "40.00", "80.00", "80.00", "12.90", "0.00", "92.90");
        ReflectionTestUtils.setField(payment, "amount", new BigDecimal("80.00"));

        var exception = assertThrows(BusinessException.class, () -> adapter.createCheckout(payment));

        assertEquals(409, exception.getStatus().value());
        verifyNoInteractions(accounts);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-1.00"})
    void nonPositiveChargeStopsBeforeCredentialLookupOrHttp(String total) {
        Payment payment = payment(1, total, total, total, "0.00", "0.00", total);

        var exception = assertThrows(BusinessException.class, () -> adapter.createCheckout(payment));

        assertEquals(400, exception.getStatus().value());
        verifyNoInteractions(accounts);
        server.verify();
    }

    @Test
    void unrepresentableCentsStopWithoutRoundingTheCharge() {
        Payment payment = payment(2, "40.00", "80.00", "80.00", "12.90", "0.00", "92.90");
        ReflectionTestUtils.setField(payment, "amount", new BigDecimal("92.901"));
        ReflectionTestUtils.setField(payment.getOrder(), "totalAmount", new BigDecimal("92.901"));

        var exception = assertThrows(BusinessException.class, () -> adapter.createCheckout(payment));

        assertEquals(400, exception.getStatus().value());
        verifyNoInteractions(accounts);
        server.verify();
    }

    @Test
    void aggregatedOrderLineExplainsShippingAndDiscountWithoutCustomerData() throws Exception {
        Payment payment = payment(2, "40.00", "80.00", "80.00", "12.90", "10.00", "82.90");

        JsonNode payload = createAndCapture(payment);

        assertEquals(1, payload.path("items").size());
        JsonNode item = payload.path("items").get(0);
        assertEquals(payment.getOrder().getId().toString(), item.path("id").asText());
        assertEquals(1, item.path("quantity").asInt());
        assertEquals("Pedido SYNTHETIC-ORDER", item.path("title").asText());
        assertEquals("Total do pedido: BRL 82.90 (subtotal: 80.00; frete: 12.90; descontos: 10.00)", item.path("description").asText());
        assertTrue(item.path("description").asText().length() <= 255);
        assertFalse(item.toString().contains(payment.getOrder().getCustomerName()));
        assertFalse(item.toString().contains(payment.getOrder().getCustomerEmail()));
    }

    @Test
    void multipleProductLinesAndCouponStillChargeOrderTotal() throws Exception {
        Payment payment = payment(2, "40.00", "80.00", "105.00", "12.90", "10.01", "107.89");
        var otherProduct = mock(Product.class);
        when(otherProduct.getId()).thenReturn(UUID.randomUUID());
        payment.getOrder().addItem(OrderItem.create(payment.getOrder(), otherProduct,
                "Other synthetic product", "other-synthetic-product", "OTHER-SKU", 5,
                new BigDecimal("5.00"), new BigDecimal("25.00")));

        JsonNode payload = createAndCapture(payment);

        assertEquals(1, payload.path("items").size());
        assertEquals(0, payment.getAmount().compareTo(payload.path("items").get(0).path("unit_price").decimalValue()));
    }

    @Test
    void chargeDoesNotRecalculateFromCurrentProductPrice() throws Exception {
        Payment payment = payment(2, "40.00", "80.00", "80.00", "12.90", "10.00", "82.90");
        Product product = payment.getOrder().getItems().getFirst().getProduct();
        clearInvocations(product);

        JsonNode payload = createAndCapture(payment);

        assertEquals(0, new BigDecimal("82.90").compareTo(payload.path("items").get(0).path("unit_price").decimalValue()));
        verifyNoInteractions(product);
    }

    private JsonNode createAndCapture(Payment payment) throws Exception {
        var observed = new AtomicReference<JsonNode>();
        server.expect(requestTo(API_URL + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-token"))
                .andExpect(header("X-Idempotency-Key", payment.getIdempotencyKey()))
                .andExpect(request -> observed.set(mapper.readTree(((MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess("{\"id\":\"synthetic-preference\",\"init_point\":\"https://checkout.example.test\"}", MediaType.APPLICATION_JSON));
        var result = adapter.createCheckout(payment);
        server.verify();
        assertEquals("synthetic-preference", result.providerCheckoutId());
        assertEquals("https://checkout.example.test", result.checkoutUrl());
        return observed.get();
    }

    private Payment payment(int quantity, String unitPrice, String lineTotal, String subtotal,
            String shipping, String discount, String total) {
        var store = mock(Store.class);
        when(store.getId()).thenReturn(UUID.randomUUID());
        when(store.getPublicDomain()).thenReturn("store.example.test");
        var product = mock(Product.class);
        when(product.getId()).thenReturn(UUID.randomUUID());
        BigDecimal amount = new BigDecimal(total);
        BigDecimal commission = new BigDecimal("1.23");
        var order = Order.create(store, UUID.randomUUID(), "SYNTHETIC-ORDER", OrderStatus.WAITING_PAYMENT,
                new BigDecimal(subtotal), new BigDecimal(shipping), new BigDecimal(discount), null, null, amount,
                new BigDecimal("2.00"), commission, amount.subtract(commission), "Synthetic customer",
                "synthetic@example.test", null, UUID.randomUUID(), "Synthetic shipping", "FIXED", 4);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        order.addItem(OrderItem.create(order, product, "Synthetic product", "synthetic-product", "SYNTHETIC-SKU",
                quantity, new BigDecimal(unitPrice), new BigDecimal(lineTotal)));
        var payment = Payment.create(store, order, PaymentProvider.MERCADO_PAGO, amount, "BRL",
                order.getCommissionRate(), commission, order.getSellerNetAmount(), "synthetic-merchant", "synthetic-idempotency");
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        return payment;
    }
}
