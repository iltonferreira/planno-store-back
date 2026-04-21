package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.order.dto.UpdateOrderStatusRequest;
import com.planno.store.payment.account.domain.PaymentProviderAccount;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.dto.CreateTransparentPaymentRequest;
import com.planno.store.payment.service.StubMercadoPagoPaymentProviderClient;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private StubMercadoPagoPaymentProviderClient stubMercadoPagoPaymentProviderClient;

    @Test
    void oauthShouldStartValidateStateAndPersistEncryptedAccount() throws Exception {
        Store store = createStore("store-a");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String adminToken = tokenFor(admin.getEmail());

        User customer = createUser("customer@example.com", "password123");
        assignRole(customer, RoleName.CUSTOMER, store);
        String customerToken = tokenFor(customer.getEmail());

        mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/connect")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/connect")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        String connectResponse = mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/connect")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("MERCADO_PAGO"))
                .andExpect(jsonPath("$.authorizationUrl", containsString("state=")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String authorizationUrl = objectMapper.readTree(connectResponse).get("authorizationUrl").asText();
        String state = extractQueryParam(authorizationUrl, "state");

        mockMvc.perform(get("/api/v1/public/payments/mercado-pago/oauth/callback")
                        .queryParam("code", "oauth-code-1")
                        .queryParam("state", "invalid-state"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/public/payments/mercado-pago/oauth/callback")
                        .queryParam("code", "oauth-code-1")
                        .queryParam("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.readyForCheckout").value(true))
                .andExpect(jsonPath("$.readyForSplit").value(true))
                .andExpect(jsonPath("$.readyForTransparentCheckout").value(true));

        mockMvc.perform(get("/api/v1/public/payments/mercado-pago/oauth/callback")
                        .queryParam("code", "oauth-code-2")
                        .queryParam("state", state))
                .andExpect(status().isBadRequest());

        PaymentProviderAccount account = paymentProviderAccountRepository.findByStoreIdAndProvider(store.getId(), com.planno.store.payment.domain.PaymentProvider.MERCADO_PAGO)
                .orElseThrow();
        Assertions.assertEquals("CONNECTED", account.getConnectionStatus().name());
        Assertions.assertNotNull(account.getProviderUserId());
        Assertions.assertNotNull(account.getAccessTokenEncrypted());
        Assertions.assertNotEquals("mock-access-token-oauth-code-1", account.getAccessTokenEncrypted());

        mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/account")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("MERCADO_PAGO"))
                .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.hasValidAccessToken").value(true))
                .andExpect(jsonPath("$.readyForTransparentCheckout").value(true))
                .andExpect(content().string(not(containsString("access_token"))))
                .andExpect(content().string(not(containsString("refresh_token"))));

        Store storeB = createStore("store-b");
        User adminB = createUser("admin-b@example.com", "password123");
        assignRole(adminB, RoleName.ADMIN, storeB);
        String adminTokenB = tokenFor(adminB.getEmail());

        mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/account")
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/account")
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + adminTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("PENDING"));
    }

    @Test
    void transparentPaymentShouldRequireReadyStoreAndPersistTransparentFields() throws Exception {
        Scenario scenario = createScenario("store-a", "admin@example.com", "produto-transparent", "SKU-TP", "40.00", "12.90", 2, 8);

        mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_test",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isBadRequest());

        connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-transparent");

        String response = mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_test",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.checkoutType").value("TRANSPARENT"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerPaymentId", notNullValue()))
                .andExpect(jsonPath("$.statusDetail").value("pending_waiting_payment"))
                .andExpect(content().string(not(containsString("tok_test"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = objectMapper.readTree(response).get("paymentId").asText();
        var payment = paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow();
        Assertions.assertEquals("TRANSPARENT", payment.getCheckoutType().name());
        Assertions.assertEquals("pending_waiting_payment", payment.getStatusDetail());
        Assertions.assertEquals("master", payment.getPaymentMethod());
        Assertions.assertEquals(1, payment.getInstallments());
        Assertions.assertEquals("buyer@example.com", payment.getPayerEmail());

        createStore("store-b");
        mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_test",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void transparentPaymentShouldValidateStateAndProviderErrorsSafely() throws Exception {
        Scenario scenario = createScenario("store-a", "admin@example.com", "produto-transparent-2", "SKU-TP2", "40.00", "12.90", 2, 8);
        connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-transparent-2");

        jdbcTemplate.update("update payment_provider_accounts set public_key = null where store_id = ?", scenario.store().getId());
        mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_test",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isBadRequest());

        connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-transparent-2b");
        mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_error",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isBadGateway());
        Assertions.assertEquals(0, paymentRepository.findAll().size());

        Scenario cancelled = createScenario("store-b", "admin-b@example.com", "produto-cancelado", "SKU-CAN", "35.00", "8.00", 1, 4);
        connectMercadoPagoAccount("store-b", cancelled.adminToken(), "oauth-transparent-3");
        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", cancelled.orderId())
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + cancelled.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "manual cancel"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                cancelled.orderId(),
                                "tok_test",
                                "visa",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transparentWebhookShouldConsolidateOrderAndRemainRefundCompatible() throws Exception {
        Scenario scenario = createScenario("store-a", "admin@example.com", "produto-transparent-3", "SKU-TP3", "40.00", "12.90", 2, 8);
        String providerUserId = connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-transparent-4");

        String response = mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_test",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-1"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = objectMapper.readTree(response).get("paymentId").asText();
        String providerPaymentId = objectMapper.readTree(response).get("providerPaymentId").asText();

        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(providerPaymentId, PaymentStatus.APPROVED, "master");
        sendWebhook("evt-transparent-1", providerPaymentId, paymentWebhookPayload(providerPaymentId, providerUserId));
        sendWebhook("evt-transparent-1", providerPaymentId, paymentWebhookPayload(providerPaymentId, providerUserId));

        mockMvc.perform(get("/api/v1/admin/payments/{paymentId}", paymentId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutType").value("TRANSPARENT"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.statusDetail").value("accredited"));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(post("/api/v1/public/payments/transparent")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTransparentPaymentRequest(
                                scenario.orderId(),
                                "tok_test_2",
                                "master",
                                null,
                                1,
                                "buyer@example.com",
                                "Buyer",
                                "Test",
                                "CPF",
                                "19119119100",
                                "device-2"
                        ))))
                .andExpect(status().isBadRequest());

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(6, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", paymentId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.payment.refund.dto.CreateRefundRequest(new BigDecimal("92.90"), "refund transparent"))))
                .andExpect(status().isCreated());
    }

    @Test
    void paymentShouldRequireConnectedAccountAndUseOrderFinancialSnapshot() throws Exception {
        Scenario scenario = createScenario("store-a", "admin@example.com", "produto-pagamento", "SKU-PAY", "40.00", "12.90", 2, 8);

        mockMvc.perform(post("/api/v1/public/payments")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(scenario.orderId()))))
                .andExpect(status().isBadRequest());

        connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-code-store-a");

        String paymentResponse = mockMvc.perform(post("/api/v1/public/payments")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(scenario.orderId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("MERCADO_PAGO"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(92.90))
                .andExpect(jsonPath("$.checkoutUrl", notNullValue()))
                .andExpect(content().string(not(containsString("commissionAmount"))))
                .andExpect(content().string(not(containsString("sellerNetAmount"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentId = objectMapper.readTree(paymentResponse).get("paymentId").asText();
        var payment = paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow();
        Assertions.assertEquals(new BigDecimal("2.50"), payment.getCommissionRate());
        Assertions.assertEquals(new BigDecimal("2.32"), payment.getCommissionAmount());
        Assertions.assertEquals(new BigDecimal("90.58"), payment.getSellerNetAmount());
        Assertions.assertNotNull(payment.getProviderMerchantId());

        mockMvc.perform(get("/api/v1/admin/payments/orders/{orderId}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("commissionAmount"))))
                .andExpect(content().string(not(containsString("sellerNetAmount"))))
                .andExpect(content().string(not(containsString("access_token"))));

        createStore("store-b");
        mockMvc.perform(post("/api/v1/public/payments")
                        .header(STORE_HEADER, "store-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(scenario.orderId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void webhookShouldRemainIdempotentAcrossMultipleConnectedStores() throws Exception {
        Scenario storeA = createScenario("store-a", "admin-a@example.com", "produto-a", "SKU-A", "40.00", "12.90", 2, 8);
        Scenario storeB = createScenario("store-b", "admin-b@example.com", "produto-b", "SKU-B", "30.00", "10.00", 1, 5);

        String providerUserIdA = connectMercadoPagoAccount("store-a", storeA.adminToken(), "oauth-a");
        String providerUserIdB = connectMercadoPagoAccount("store-b", storeB.adminToken(), "oauth-b");

        String paymentResponseA = initiatePayment("store-a", storeA.orderId());
        String paymentResponseB = initiatePayment("store-b", storeB.orderId());
        String checkoutIdA = objectMapper.readTree(paymentResponseA).get("externalCheckoutId").asText();
        String checkoutIdB = objectMapper.readTree(paymentResponseB).get("externalCheckoutId").asText();

        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(checkoutIdA, PaymentStatus.APPROVED, "pix");
        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(checkoutIdB, PaymentStatus.APPROVED, "credit_card");

        sendWebhook("evt-a-1", checkoutIdA, paymentWebhookPayload(checkoutIdA, providerUserIdA));
        sendWebhook("evt-a-1", checkoutIdA, paymentWebhookPayload(checkoutIdA, providerUserIdA));
        sendWebhook("evt-b-1", checkoutIdB, paymentWebhookPayload(checkoutIdB, providerUserIdB));

        var orderA = orderRepository.findByIdAndStoreId(storeA.orderId(), storeA.store().getId()).orElseThrow();
        var orderB = orderRepository.findByIdAndStoreId(storeB.orderId(), storeB.store().getId()).orElseThrow();
        Assertions.assertEquals(OrderStatus.PAID, orderA.getStatus());
        Assertions.assertEquals(OrderStatus.PAID, orderB.getStatus());

        var inventoryA = inventoryItemRepository.findByProductIdAndProductStoreId(storeA.productId(), storeA.store().getId()).orElseThrow();
        var inventoryB = inventoryItemRepository.findByProductIdAndProductStoreId(storeB.productId(), storeB.store().getId()).orElseThrow();
        Assertions.assertEquals(0, inventoryA.getQuantityReserved());
        Assertions.assertEquals(6, inventoryA.getQuantityAvailable());
        Assertions.assertEquals(0, inventoryB.getQuantityReserved());
        Assertions.assertEquals(4, inventoryB.getQuantityAvailable());

        mockMvc.perform(get("/api/v1/admin/orders/{id}", storeA.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)));

        Assertions.assertEquals(2, webhookEventRepository.findAll().size());
    }

    @Test
    void rejectedWebhookShouldNotFinalizeInventoryAndDisconnectShouldBlockNewPayments() throws Exception {
        Scenario scenario = createScenario("store-a", "admin@example.com", "produto-rejeitado", "SKU-REJ", "50.00", "9.90", 2, 6);
        String providerUserId = connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-rej");

        String paymentResponse = initiatePayment("store-a", scenario.orderId());
        String checkoutId = objectMapper.readTree(paymentResponse).get("externalCheckoutId").asText();
        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(checkoutId, PaymentStatus.REJECTED, "credit_card");

        sendWebhook("evt-r-1", checkoutId, paymentWebhookPayload(checkoutId, providerUserId));

        var order = orderRepository.findByIdAndStoreId(scenario.orderId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(OrderStatus.WAITING_PAYMENT, order.getStatus());

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(2, inventory.getQuantityReserved());
        Assertions.assertEquals(6, inventory.getQuantityAvailable());

        mockMvc.perform(post("/api/v1/admin/payments/mercado-pago/disconnect")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("DISCONNECTED"));

        Scenario nextScenario = createScenario("store-a", "admin@example.com", "produto-novo", "SKU-NEW", "20.00", "5.00", 1, 3);
        mockMvc.perform(post("/api/v1/public/payments")
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(nextScenario.orderId()))))
                .andExpect(status().isBadRequest());
    }

    private String connectMercadoPagoAccount(String storeSlug, String adminToken, String oauthCode) throws Exception {
        Store store = storeRepository.findBySlugIgnoreCase(storeSlug).orElseThrow();

        String connectResponse = mockMvc.perform(get("/api/v1/admin/payments/mercado-pago/connect")
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String authorizationUrl = objectMapper.readTree(connectResponse).get("authorizationUrl").asText();
        String state = extractQueryParam(authorizationUrl, "state");

        mockMvc.perform(get("/api/v1/public/payments/mercado-pago/oauth/callback")
                        .queryParam("code", oauthCode)
                        .queryParam("state", state))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionStatus").value("CONNECTED"));

        return paymentProviderAccountRepository.findByStoreIdAndProvider(store.getId(), com.planno.store.payment.domain.PaymentProvider.MERCADO_PAGO)
                .stream()
                .findFirst()
                .orElseThrow()
                .getProviderUserId();
    }

    private String initiatePayment(String storeSlug, UUID orderId) throws Exception {
        return mockMvc.perform(post("/api/v1/public/payments")
                        .header(STORE_HEADER, storeSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(orderId))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Scenario createScenario(
            String storeSlug,
            String adminEmail,
            String productSlug,
            String sku,
            String price,
            String shippingPrice,
            int quantity,
            int stock
    ) throws Exception {
        Store store = storeRepository.findBySlugIgnoreCase(storeSlug).orElseGet(() -> createStore(storeSlug));
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> createUser(adminEmail, "password123"));
        if (assignmentRepository.findAll().stream().noneMatch(it -> it.getUser().getId().equals(admin.getId()) && it.getStore().getId().equals(store.getId()))) {
            assignRole(admin, RoleName.ADMIN, store);
        }
        String adminToken = tokenFor(admin.getEmail());

        String shippingMethodId = createShippingMethod(adminToken, storeSlug, "Padrao", shippingPrice, 4, true);
        String productId = createProduct(adminToken, storeSlug, productSlug, sku, ProductStatus.ACTIVE, price);
        updateInventory(adminToken, storeSlug, productId, stock, 0);

        String cartToken = createCart(storeSlug);
        addCartItem(storeSlug, cartToken, productId, quantity);
        UUID cartId = currentCartId(storeSlug, cartToken);

        String orderResponse = mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                cartId,
                                UUID.fromString(shippingMethodId),
                                "Cliente Teste",
                                "cliente@example.com",
                                "12345678900",
                                sampleAddress()
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new Scenario(
                store,
                adminToken,
                UUID.fromString(objectMapper.readTree(orderResponse).get("id").asText()),
                UUID.fromString(productId)
        );
    }

    private void sendWebhook(String requestId, String dataId, String payload) throws Exception {
        mockMvc.perform(post("/api/v1/public/webhooks/mercado-pago")
                        .queryParam("data.id", dataId)
                        .header("x-request-id", requestId)
                        .header("x-signature", signatureHeader(requestId, dataId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private String signatureHeader(String requestId, String dataId) throws Exception {
        String timestamp = "1713550000";
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return "ts=" + timestamp + ",v1=" + builder;
    }

    private String paymentWebhookPayload(String dataId, String providerUserId) {
        return """
                {
                  "type": "payment",
                  "action": "payment.updated",
                  "user_id": "%s",
                  "data": {
                    "id": "%s"
                  }
                }
                """.formatted(providerUserId, dataId);
    }

    private String extractQueryParam(String url, String param) {
        var query = URI.create(url).getQuery();
        for (String entry : query.split("&")) {
            String[] pair = entry.split("=", 2);
            if (pair.length == 2 && pair[0].equals(param)) {
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Missing query param: " + param);
    }

    private String createShippingMethod(String adminToken, String storeSlug, String name, String price, int days, boolean active) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/shipping/methods")
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingMethodRequest(
                                name,
                                ShippingMethodType.FIXED,
                                new BigDecimal(price),
                                days,
                                active
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createProduct(String adminToken, String storeSlug, String slug, String sku, ProductStatus status, String price) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                null,
                                "Produto " + slug,
                                slug,
                                sku,
                                "Descricao",
                                status,
                                new BigDecimal(price),
                                null
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void updateInventory(String adminToken, String storeSlug, String productId, int quantityAvailable, int quantityReserved) throws Exception {
        mockMvc.perform(put("/api/v1/admin/inventory/{productId}", productId)
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryUpdateRequest(quantityAvailable, quantityReserved))))
                .andExpect(status().isOk());
    }

    private String createCart(String storeSlug) throws Exception {
        String response = mockMvc.perform(post("/api/v1/public/cart")
                        .header(STORE_HEADER, storeSlug))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("cartToken").asText();
    }

    private UUID currentCartId(String storeSlug, String cartToken) throws Exception {
        String response = mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addCartItem(String storeSlug, String cartToken, String productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.cart.dto.CartItemRequest(
                                UUID.fromString(productId),
                                quantity
                        ))))
                .andExpect(status().isOk());
    }

    private AddressRequest sampleAddress() {
        return new AddressRequest(
                "Cliente Teste",
                "Rua Principal",
                "100",
                "Apto 10",
                "Centro",
                "Sao Paulo",
                "SP",
                "01001000",
                "BR"
        );
    }

    private record Scenario(Store store, String adminToken, UUID orderId, UUID productId) {
    }
}
