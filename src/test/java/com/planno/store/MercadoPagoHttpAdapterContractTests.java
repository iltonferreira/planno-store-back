package com.planno.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.order.domain.Order;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.account.service.MercadoPagoOAuthClient;
import com.planno.store.payment.account.service.PaymentProviderAccountService;
import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.payment.domain.Payment;
import com.planno.store.payment.domain.PaymentProvider;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.refund.domain.Refund;
import com.planno.store.payment.refund.domain.RefundStatus;
import com.planno.store.payment.service.MercadoPagoPaymentProviderClient;
import com.planno.store.payment.service.TransparentPaymentProviderRequest;
import com.planno.store.store.domain.Store;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Real adapters with intercepted HTTP. All credentials, payloads and responses are synthetic.
 * These tests do not connect to Mercado Pago, a database, or a browser. */
class MercadoPagoHttpAdapterContractTests {
    private static final String API = "https://mercado-pago.example.test";
    private static final String TOKEN = "synthetic-access-token";
    private static final String PRIVATE_RESPONSE = "synthetic-private-provider-detail";
    private static final UUID STORE_ID = UUID.fromString("a6a86607-fb18-4236-b9c0-69f07d681f94");
    private static final UUID PAYMENT_ID = UUID.fromString("a210505c-e3ec-4120-8489-8c92b36f2a66");
    private static final UUID ORDER_ID = UUID.fromString("a710505c-e3ec-4120-8489-8c92b36f2a66");
    private static final UUID REFUND_ID = UUID.fromString("e210505c-e3ec-4120-8489-8c92b36f2a66");
    private final ObjectMapper mapper = new ObjectMapper();
    private MockRestServiceServer server;
    private RestClient.Builder restClientBuilder;
    private MercadoPagoPaymentProviderClient adapter;
    private MercadoPagoOAuthClient oauth;
    private PaymentProviderAccountService accounts;
    private PaymentProviderAccount account;
    private Payment payment;
    private Refund refund;

    @BeforeEach void setup() {
        accounts = mock(PaymentProviderAccountService.class);
        account = mock(PaymentProviderAccount.class);
        when(accounts.decryptAccessToken(account)).thenReturn(TOKEN);
        when(accounts.requireConnectedAccountForStore(STORE_ID)).thenReturn(account);
        when(accounts.requireConnectedAccountForCurrentStore()).thenReturn(account);
        var properties = new PaymentProperties(null, null, new PaymentProperties.MercadoPagoProperties(
                false, "synthetic-client", "synthetic-client-secret", null, "synthetic-webhook-secret",
                "https://store.example.test/webhook", null, null, null, API, "https://auth.example.test", false));
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        adapter = new MercadoPagoPaymentProviderClient(restClientBuilder, properties, mapper, accounts);
        oauth = new MercadoPagoOAuthClient(restClientBuilder.clone(), properties, mapper);
        payment = mock(Payment.class);
        refund = mock(Refund.class);
        var store = mock(Store.class);
        var order = mock(Order.class);
        when(payment.getStore()).thenReturn(store);
        when(store.getId()).thenReturn(STORE_ID);
        when(payment.getOrder()).thenReturn(order);
        when(order.getId()).thenReturn(ORDER_ID);
        when(order.getCode()).thenReturn("SYNTHETIC-ORDER");
        when(order.getTotalAmount()).thenReturn(new BigDecimal("82.90"));
        when(order.getSubtotalAmount()).thenReturn(new BigDecimal("80.00"));
        when(order.getShippingAmount()).thenReturn(new BigDecimal("12.90"));
        when(order.getDiscountAmount()).thenReturn(new BigDecimal("10.00"));
        when(payment.getId()).thenReturn(PAYMENT_ID);
        when(payment.getProviderPaymentId()).thenReturn("synthetic-payment");
        when(payment.getProviderMerchantId()).thenReturn("synthetic-merchant");
        when(payment.getIdempotencyKey()).thenReturn("synthetic-idempotency-key");
        when(payment.getCurrency()).thenReturn("BRL");
        when(payment.getAmount()).thenReturn(new BigDecimal("82.90"));
        when(payment.getCommissionAmount()).thenReturn(new BigDecimal("1.23"));
        when(refund.getId()).thenReturn(REFUND_ID);
        when(refund.getStore()).thenReturn(store);
        when(refund.getPayment()).thenReturn(payment);
        when(refund.getAmount()).thenReturn(new BigDecimal("20.00"));
        when(refund.getCurrency()).thenReturn("BRL");
    }

    private TransparentPaymentProviderRequest transparentRequest() {
        return new TransparentPaymentProviderRequest("synthetic-card-token", "visa", "synthetic-issuer", 2,
                "buyer@example.test", "Synthetic", "Buyer", "CPF", "00000000000", "synthetic-device");
    }

    @Test void transparentPayloadUsesSnapshotsMetadataAndIdempotencyWithoutCardData() throws Exception {
        server.expect(requestTo(API + "/v1/payments"))
                .andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("X-Idempotency-Key", "synthetic-idempotency-key"))
                .andExpect(request -> {
                    JsonNode body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("synthetic-card-token", body.path("token").asText());
                    assertEquals("visa", body.path("payment_method_id").asText());
                    assertEquals(2, body.path("installments").asInt());
                    assertEquals("synthetic-issuer", body.path("issuer_id").asText());
                    assertEquals(0, new BigDecimal("82.90").compareTo(body.path("transaction_amount").decimalValue()));
                    assertEquals(0, new BigDecimal("1.23").compareTo(body.path("application_fee").decimalValue()));
                    assertEquals(PAYMENT_ID.toString(), body.path("external_reference").asText());
                    assertEquals(PAYMENT_ID.toString(), body.path("metadata").path("internal_payment_id").asText());
                    assertEquals(STORE_ID.toString(), body.path("metadata").path("store_id").asText());
                    assertEquals(ORDER_ID.toString(), body.path("metadata").path("order_id").asText());
                    assertEquals("buyer@example.test", body.path("payer").path("email").asText());
                    assertEquals("CPF", body.path("payer").path("identification").path("type").asText());
                    assertFalse(body.has("card_number"));
                    assertFalse(body.has("security_code"));
                    assertFalse(body.has("client_secret"));
                    assertFalse(body.toString().contains(TOKEN));
                }).andRespond(withSuccess("{\"id\":123,\"status\":\"approved\",\"status_detail\":\"accredited\",\"payment_method_id\":\"visa\"}", MediaType.APPLICATION_JSON));
        var result = adapter.createTransparentPayment(payment, transparentRequest());
        assertEquals("123", result.providerPaymentId());
        assertEquals(PaymentStatus.APPROVED, result.status());
        assertEquals("accredited", result.statusDetail());
        assertEquals("visa", result.paymentMethod());
        verify(accounts).requireConnectedAccountForStore(STORE_ID);
        server.verify();
    }

    @Test void paymentQueryParsesAmountCurrencyMerchantAndInternalIdentity() {
        server.expect(requestTo(API + "/v1/payments/synthetic-payment"))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("{\"id\":\"synthetic-payment\",\"status\":\"approved\",\"transaction_amount\":82.90,\"currency_id\":\"BRL\",\"user_id\":\"synthetic-merchant\",\"metadata\":{\"internal_payment_id\":\"" + PAYMENT_ID + "\",\"preference_id\":\"synthetic-preference\"}}", MediaType.APPLICATION_JSON));
        var result = adapter.getPaymentDetails(account, "synthetic-payment");
        assertEquals("synthetic-payment", result.providerPaymentId());
        assertEquals("synthetic-merchant", result.providerMerchantId());
        assertEquals(PAYMENT_ID.toString(), result.externalReference());
        assertEquals("synthetic-preference", result.providerCheckoutId());
        assertEquals(new BigDecimal("82.90"), result.amount());
        assertEquals("BRL", result.currency());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"82.90", "\"82.90\""})
    void officialPaymentResponseUsesCollectorIdAndAcceptsExactDecimalNumbersOrStrings(String amountJson) {
        server.expect(requestTo(API + "/v1/payments/synthetic-payment"))
                .andRespond(withSuccess("{\"id\":\"synthetic-payment\",\"status\":\"approved\",\"collector_id\":123456789,\"transaction_amount\":"
                        + amountJson + ",\"currency_id\":\"BRL\",\"external_reference\":\"" + PAYMENT_ID + "\"}", MediaType.APPLICATION_JSON));
        var result = adapter.getPaymentDetails(account, "synthetic-payment");
        assertAll(() -> assertEquals("123456789", result.providerMerchantId()),
                () -> assertEquals(new BigDecimal("82.90"), result.amount()),
                () -> assertEquals(PAYMENT_ID.toString(), result.externalReference()));
        server.verify();
    }

    @Test void officialCollectorIdentityTakesPrecedenceOverCallerControlledMetadata() {
        server.expect(requestTo(API + "/v1/payments/synthetic-payment"))
                .andRespond(withSuccess("{\"id\":\"synthetic-payment\",\"status\":\"approved\",\"collector_id\":123456789,\"user_id\":\"other-user\",\"transaction_amount\":82.90,\"currency_id\":\"BRL\",\"metadata\":{\"provider_user_id\":\"other-metadata-merchant\"}}", MediaType.APPLICATION_JSON));
        assertEquals("123456789", adapter.getPaymentDetails(account, "synthetic-payment").providerMerchantId());
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"20.00", "\"20.00\""})
    void refundQueryUsesBothDocumentedResourceIdsAndParsesDecimalAmounts(String amountJson) {
        server.expect(requestTo(API + "/v1/payments/synthetic-payment/refunds/synthetic-refund"))
                .andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("{\"id\":\"synthetic-refund\",\"payment_id\":\"synthetic-payment\",\"status\":\"approved\",\"amount\":" + amountJson + ",\"currency_id\":\"BRL\"}", MediaType.APPLICATION_JSON));
        var result = adapter.getRefundDetails(account, "synthetic-payment", "synthetic-refund");
        assertEquals("synthetic-refund", result.providerRefundId());
        assertEquals("synthetic-payment", result.providerPaymentId());
        assertEquals(new BigDecimal("20.00"), result.amount());
        assertEquals(RefundStatus.APPROVED, result.status());
        server.verify();
    }

    static Stream<Arguments> missingRefundIds() {
        return Stream.of(Arguments.of(null, "synthetic-refund"), Arguments.of("", "synthetic-refund"),
                Arguments.of(" ", "synthetic-refund"), Arguments.of("synthetic-payment", null),
                Arguments.of("synthetic-payment", ""), Arguments.of("synthetic-payment", " "));
    }

    @ParameterizedTest @MethodSource("missingRefundIds")
    void missingRefundResourceIdsStopBeforeCredentialLookupAndHttp(String paymentId, String refundId) {
        var error = assertThrows(BusinessException.class, () -> adapter.getRefundDetails(account, paymentId, refundId));
        assertEquals(409, error.getStatus().value());
        verifyNoInteractions(accounts);
        server.verify();
    }

    @Test void legacyRefundQueryWithoutPaymentIdentityFailsClosedBeforeHttp() {
        var error = assertThrows(BusinessException.class, () -> adapter.getRefundDetails(account, "synthetic-refund"));
        assertEquals(409, error.getStatus().value());
        verifyNoInteractions(accounts);
        server.verify();
    }

    static Stream<Arguments> invalidMonetaryFields() {
        return Stream.of("payment", "refund-create", "refund-query").flatMap(operation ->
                Stream.of("missing", "null", "\"\"", "\"not-money\"", "\"NaN\"", "\"1e2\"",
                        "\"-1.00\"", "-1.00", "82.901", "\"82.901\"", "true", "{}", "[]")
                        .map(value -> Arguments.of(operation, value)));
    }

    @ParameterizedTest @MethodSource("invalidMonetaryFields")
    void invalidMonetaryResponseFieldsFailClosedWithoutInventingOrRoundingMoney(String operation, String value) {
        String field = operation.equals("payment") ? "transaction_amount" : "amount";
        String monetaryJson = value.equals("missing") ? "" : ",\"" + field + "\":" + value;
        String path = switch (operation) {
            case "payment" -> "/v1/payments/synthetic-payment";
            case "refund-create" -> "/v1/payments/synthetic-payment/refunds";
            default -> "/v1/payments/synthetic-payment/refunds/synthetic-refund";
        };
        server.expect(requestTo(API + path)).andRespond(withSuccess(
                "{\"id\":\"synthetic-resource\",\"payment_id\":\"synthetic-payment\",\"status\":\"approved\",\"collector_id\":123456789,\"currency_id\":\"BRL\"" + monetaryJson + "}", MediaType.APPLICATION_JSON));
        safeGatewayFailure(() -> {
            switch (operation) {
                case "payment" -> adapter.getPaymentDetails(account, "synthetic-payment");
                case "refund-create" -> adapter.createRefund(refund);
                default -> adapter.getRefundDetails(account, "synthetic-payment", "synthetic-refund");
            }
        });
        server.verify();
    }

    static Stream<Arguments> exactJsonMonetaryNumbers() {
        return Stream.of("payment", "refund-create", "refund-query").flatMap(operation ->
                Stream.of(Arguments.of(operation, "82.900000000000001", false),
                        Arguments.of(operation, "10000000000000000.001", false),
                        Arguments.of(operation, "10000000000000000.01", true)));
    }

    @ParameterizedTest @MethodSource("exactJsonMonetaryNumbers")
    void providerJsonNumericPrecisionCannotHideSubcentValuesOrEraseValidCents(
            String operation, String literal, boolean exactCents) {
        String field = operation.equals("payment") ? "transaction_amount" : "amount";
        String path = switch (operation) {
            case "payment" -> "/v1/payments/synthetic-payment";
            case "refund-create" -> "/v1/payments/synthetic-payment/refunds";
            default -> "/v1/payments/synthetic-payment/refunds/synthetic-refund";
        };
        if (exactCents && operation.equals("refund-create")) {
            when(refund.getAmount()).thenReturn(new BigDecimal(literal));
            when(payment.getAmount()).thenReturn(new BigDecimal(literal));
        }
        server.expect(requestTo(API + path)).andRespond(withSuccess(
                "{\"id\":\"synthetic-resource\",\"payment_id\":\"synthetic-payment\",\"status\":\"approved\",\"collector_id\":123456789,\"currency_id\":\"BRL\",\""
                        + field + "\":" + literal + "}", MediaType.APPLICATION_JSON));
        if (!exactCents) {
            safeGatewayFailure(() -> readMonetaryResult(operation));
        } else {
            assertEquals(new BigDecimal(literal), readMonetaryResult(operation));
        }
        server.verify();
    }

    private BigDecimal readMonetaryResult(String operation) {
        return switch (operation) {
            case "payment" -> adapter.getPaymentDetails(account, "synthetic-payment").amount();
            case "refund-create" -> adapter.createRefund(refund).amount();
            default -> adapter.getRefundDetails(account, "synthetic-payment", "synthetic-refund").amount();
        };
    }

    @ParameterizedTest @ValueSource(strings = {"20.00", "82.90"})
    void partialAndTotalRefundsCarryExactAmountAndStableAttemptKey(String amount) throws Exception {
        when(refund.getAmount()).thenReturn(new BigDecimal(amount));
        server.expect(requestTo(API + "/v1/payments/synthetic-payment/refunds"))
                .andExpect(method(HttpMethod.POST)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andExpect(header("X-Idempotency-Key", REFUND_ID.toString()))
                .andExpect(request -> {
                    JsonNode body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(0, new BigDecimal(amount).compareTo(body.path("amount").decimalValue()));
                    assertFalse(body.toString().contains(TOKEN));
                }).andRespond(withSuccess("{\"id\":\"synthetic-refund\",\"payment_id\":\"synthetic-payment\",\"status\":\"approved\",\"amount\":" + amount + ",\"currency_id\":\"BRL\"}", MediaType.APPLICATION_JSON));
        var result = adapter.createRefund(refund);
        assertEquals("synthetic-refund", result.providerRefundId());
        assertEquals("synthetic-payment", result.providerPaymentId());
        assertEquals(RefundStatus.APPROVED, result.status());
        assertEquals(new BigDecimal(amount), result.amount());
        server.verify();
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 500})
    void providerHttpFailuresAreControlledAndDoNotExposeSensitiveBodies(int status) {
        server.expect(requestTo(API + "/v1/payments"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body(PRIVATE_RESPONSE + TOKEN).contentType(MediaType.TEXT_PLAIN));
        safeGatewayFailure(() -> adapter.createTransparentPayment(payment, transparentRequest()));
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-json", "{broken"})
    void malformedPaymentResponsesFailClosed(String response) {
        server.expect(requestTo(API + "/v1/payments"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        safeGatewayFailure(() -> adapter.createTransparentPayment(payment, transparentRequest()));
        server.verify();
    }

    @ParameterizedTest @ValueSource(strings = {"pro", "transparent", "query", "refund", "oauth"})
    void simulatedSocketTimeoutsDoNotLeakRemoteDetails(String operation) {
        String path = switch (operation) {
            case "pro" -> "/checkout/preferences";
            case "transparent" -> "/v1/payments";
            case "query" -> "/v1/payments/synthetic-payment";
            case "refund" -> "/v1/payments/synthetic-payment/refunds";
            default -> "/oauth/token";
        };
        ResponseCreator timeout = request -> { throw new SocketTimeoutException(PRIVATE_RESPONSE + TOKEN); };
        server.expect(requestTo(API + path)).andRespond(timeout);
        safeGatewayFailure(() -> {
            switch (operation) {
                case "pro" -> adapter.createCheckout(payment);
                case "transparent" -> adapter.createTransparentPayment(payment, transparentRequest());
                case "query" -> adapter.getPaymentDetails(account, "synthetic-payment");
                case "refund" -> adapter.createRefund(refund);
                default -> oauth.exchangeCode("synthetic-code", "https://store.example.test/callback");
            }
        });
        server.verify();
    }

    @Test void oauthAuthorizationCodeExchangeUsesFormAndParsesExpiryWithoutLoggingTokens() {
        OffsetDateTime before = OffsetDateTime.now();
        server.expect(requestTo(API + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("grant_type=authorization_code"));
                    assertTrue(body.contains("client_id=synthetic-client"));
                    assertTrue(body.contains("client_secret=synthetic-client-secret"));
                    assertTrue(body.contains("code=synthetic-code"));
                    assertTrue(body.contains("test_token=false"));
                    assertFalse(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION));
                }).andRespond(withSuccess("{\"access_token\":\"APP_USR-synthetic-oauth-access\",\"refresh_token\":\"synthetic-oauth-refresh\",\"expires_in\":3600,\"user_id\":123,\"public_key\":\"synthetic-public-key\",\"scope\":\"read write\",\"live_mode\":true}", MediaType.APPLICATION_JSON));
        var result = oauth.exchangeCode("synthetic-code", "https://store.example.test/callback");
        assertEquals("APP_USR-synthetic-oauth-access", result.accessToken());
        assertEquals("synthetic-oauth-refresh", result.refreshToken());
        assertEquals("123", result.userId());
        assertTrue(!result.expiresAt().isBefore(before.plusSeconds(3600)));
        assertTrue(!result.expiresAt().isAfter(OffsetDateTime.now().plusSeconds(3600)));
        server.verify();
    }

    @Test void sandboxOAuthUsesTestTokenAndAcceptsDocumentedTestTypeWhenLiveModeIsOmitted() {
        var sandbox = new MercadoPagoOAuthClient(restClientBuilder.clone(),
                new PaymentProperties(null, null, new PaymentProperties.MercadoPagoProperties(
                        false, "synthetic-client", "synthetic-client-secret", null, null, null,
                        null, null, null, API, null, true)), mapper);
        server.expect(requestTo(API + "/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> assertTrue(((MockClientHttpRequest) request).getBodyAsString().contains("test_token=true")))
                .andRespond(withSuccess("{\"access_token\":\"TEST-synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"expires_in\":3600,\"user_id\":123,\"public_key\":\"TEST-synthetic-public\",\"scope\":\"read write offline_access\"}", MediaType.APPLICATION_JSON));

        var result = sandbox.exchangeCode("synthetic-code", "https://store.example.test/callback");
        assertEquals("TEST-synthetic-access", result.accessToken());
        assertEquals("synthetic-refresh", result.refreshToken());
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("oauthModeMismatches")
    void oauthModeMismatchOrAmbiguousProductionResponseFailsClosed(String testToken, String accessToken, String liveMode) {
        boolean sandboxMode = Boolean.parseBoolean(testToken);
        var configured = new MercadoPagoOAuthClient(restClientBuilder.clone(),
                new PaymentProperties(null, null, new PaymentProperties.MercadoPagoProperties(
                        false, "synthetic-client", "synthetic-client-secret", null, null, null,
                        null, null, null, API, null, sandboxMode)), mapper);
        server.expect(requestTo(API + "/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\"synthetic-refresh\",\"expires_in\":3600,\"user_id\":123,\"public_key\":\"synthetic-public\"" + liveMode + "}", MediaType.APPLICATION_JSON));
        var error = assertThrows(BusinessException.class,
                () -> configured.exchangeCode("synthetic-code", "https://store.example.test/callback"));
        assertEquals(502, error.getStatus().value());
        assertFalse(error.getMessage().contains(accessToken));
        server.verify();
    }

    static Stream<Arguments> oauthModeMismatches() {
        return Stream.of(
                Arguments.of("true", "APP_USR-synthetic", ",\"live_mode\":false"),
                Arguments.of("true", "TEST-synthetic", ",\"live_mode\":true"),
                Arguments.of("true", "TEST-synthetic", ",\"live_mode\":\"false\""),
                Arguments.of("false", "APP_USR-synthetic", ""),
                Arguments.of("false", "APP_USR-synthetic", ",\"live_mode\":false"),
                Arguments.of("false", "TEST-synthetic", ",\"live_mode\":true"));
    }

    @ParameterizedTest @ValueSource(ints = {401, 403, 429, 500})
    void oauthHttpErrorsAreControlled(int status) {
        server.expect(requestTo(API + "/oauth/token"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body(PRIVATE_RESPONSE + TOKEN));
        safeGatewayFailure(() -> oauth.exchangeCode("synthetic-code", "https://store.example.test/callback"));
        server.verify();
    }

    @Test void expiredOrDisconnectedCredentialsFailLocalValidityWithoutExternalHttp() {
        var syntheticAccount = PaymentProviderAccount.create(mock(Store.class), PaymentProvider.MERCADO_PAGO);
        syntheticAccount.connect("synthetic-merchant", "synthetic-encrypted-access", "synthetic-encrypted-refresh",
                OffsetDateTime.now().minusMinutes(1), "synthetic-public-key", "read write");
        assertFalse(syntheticAccount.hasValidAccessToken());
        syntheticAccount.connect("synthetic-merchant", "synthetic-encrypted-access", "synthetic-encrypted-refresh",
                OffsetDateTime.now().plusMinutes(10), "synthetic-public-key", "read write");
        assertTrue(syntheticAccount.hasValidAccessToken());
        syntheticAccount.disconnect();
        assertFalse(syntheticAccount.hasValidAccessToken());
        assertNull(syntheticAccount.getAccessTokenEncrypted());
        assertNull(syntheticAccount.getRefreshTokenEncrypted());
        server.verify();
    }

    private void safeGatewayFailure(Runnable invocation) {
        var error = assertThrows(BusinessException.class, invocation::run);
        assertEquals(502, error.getStatus().value());
        assertFalse(error.getMessage().contains(TOKEN));
        assertFalse(error.getMessage().contains(PRIVATE_RESPONSE));
        assertFalse(error.getMessage().contains("synthetic-client-secret"));
        assertFalse(error.getMessage().contains("synthetic-card-token"));
    }
}
