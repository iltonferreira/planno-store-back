package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.service.StubMercadoPagoPaymentProviderClient;
import com.planno.store.payment.refund.dto.CreateRefundRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefundIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private StubMercadoPagoPaymentProviderClient stubMercadoPagoPaymentProviderClient;

    @Test
    void refundShouldRequireAuthenticationApprovedPaymentAndCorrectStore() throws Exception {
        PaidScenario paidScenario = createPaidScenario("store-a", "admin@example.com", "produto-refund", "SKU-REFUND", "40.00", "12.90", 2, 8);

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", paidScenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("92.90"), "sem auth"))))
                .andExpect(status().isUnauthorized());

        Store storeB = createStore("store-b");
        User adminB = createUser("admin-b@example.com", "password123");
        assignRole(adminB, RoleName.ADMIN, storeB);
        String adminTokenB = tokenFor(adminB.getEmail());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", paidScenario.paymentId())
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + adminTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("92.90"), "wrong store"))))
                .andExpect(status().isNotFound());

        PendingScenario pendingScenario = createPendingPaymentScenario("store-pending", "admin-pending@example.com", "produto-pendente", "SKU-PENDING", "30.00", "5.00", 1, 5);
        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", pendingScenario.paymentId())
                        .header(STORE_HEADER, "store-pending")
                        .header("Authorization", "Bearer " + pendingScenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("35.00"), "pending"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", paidScenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + paidScenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("999.00"), "too much"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullRefundShouldUpdatePaymentOrderHistoryAndRestoreStock() throws Exception {
        PaidScenario scenario = createPaidScenario("store-a", "admin@example.com", "produto-full", "SKU-FULL", "40.00", "12.90", 2, 8);

        String refundResponse = mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("92.90"), "cliente desistiu"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.type").value("FULL"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/v1/admin/payments/{paymentId}/refunds", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/admin/orders/{orderId}/refunds", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/admin/payments/orders/{orderId}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REFUNDED"))
                .andExpect(content().string(not(containsString("commissionAmount"))));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.statusHistory", hasSize(3)))
                .andExpect(content().string(not(containsString("commissionAmount"))));

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(8, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());
        Assertions.assertEquals(1, refundRepository.findAllByPaymentIdAndStoreIdOrderByCreatedAtDesc(scenario.paymentId(), scenario.store().getId()).size());
        Assertions.assertNotNull(objectMapper.readTree(refundResponse).get("providerRefundId").asText());
    }

    @Test
    void partialRefundShouldKeepOrderPaidAndAvoidAutomaticStockReturn() throws Exception {
        PaidScenario scenario = createPaidScenario("store-a", "admin@example.com", "produto-partial", "SKU-PARTIAL", "40.00", "12.90", 2, 8);

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("20.00"), "ajuste parcial"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.type").value("PARTIAL"));

        mockMvc.perform(get("/api/v1/admin/payments/orders/{orderId}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PARTIALLY_REFUNDED"));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(6, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());
    }

    @Test
    void refundWebhookShouldBeIdempotentAndNotReturnInventoryTwice() throws Exception {
        PaidScenario scenario = createPaidScenario("store-a", "admin@example.com", "produto-webhook", "SKU-WEBHOOK", "40.00", "12.90", 2, 8);

        String refundResponse = mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("92.90"), "webhook duplicate"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refundId = objectMapper.readTree(refundResponse).get("providerRefundId").asText();

        sendWebhook("refund-evt-1", refundId, refundWebhookPayload(refundId, scenario.providerUserId()));
        sendWebhook("refund-evt-2", refundId, refundWebhookPayload(refundId, scenario.providerUserId()));

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(8, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());
        Assertions.assertEquals(1, refundRepository.findAllByPaymentIdAndStoreIdOrderByCreatedAtDesc(scenario.paymentId(), scenario.store().getId()).size());

        mockMvc.perform(get("/api/v1/admin/orders/{id}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.statusHistory", hasSize(3)));
    }

    private PaidScenario createPaidScenario(
            String storeSlug,
            String adminEmail,
            String productSlug,
            String sku,
            String price,
            String shippingPrice,
            int quantity,
            int stock
    ) throws Exception {
        PendingScenario pendingScenario = createPendingPaymentScenario(storeSlug, adminEmail, productSlug, sku, price, shippingPrice, quantity, stock);
        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(pendingScenario.checkoutId(), com.planno.store.payment.domain.PaymentStatus.APPROVED, "pix");
        sendWebhook("pay-" + pendingScenario.paymentId(), pendingScenario.checkoutId(), paymentWebhookPayload(pendingScenario.checkoutId(), pendingScenario.providerUserId()));
        return new PaidScenario(
                pendingScenario.store(),
                pendingScenario.adminToken(),
                pendingScenario.orderId(),
                pendingScenario.paymentId(),
                pendingScenario.productId(),
                pendingScenario.providerUserId()
        );
    }

    private PendingScenario createPendingPaymentScenario(
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

        String providerUserId = connectMercadoPagoAccount(storeSlug, adminToken, "oauth-" + productSlug);
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

        UUID orderId = UUID.fromString(objectMapper.readTree(orderResponse).get("id").asText());
        String paymentResponse = mockMvc.perform(post("/api/v1/public/payments")
                        .header(CART_HEADER, cartToken)
                        .header(STORE_HEADER, storeSlug)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePaymentRequest(orderId))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return new PendingScenario(
                store,
                adminToken,
                orderId,
                UUID.fromString(objectMapper.readTree(paymentResponse).get("paymentId").asText()),
                objectMapper.readTree(paymentResponse).get("externalCheckoutId").asText(),
                UUID.fromString(productId),
                providerUserId
        );
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

    private String refundWebhookPayload(String refundId, String providerUserId) {
        return """
                {
                  "type": "refund",
                  "action": "refund.updated",
                  "user_id": "%s",
                  "data": {
                    "id": "%s"
                  }
                }
                """.formatted(providerUserId, refundId);
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

    private record PendingScenario(
            Store store,
            String adminToken,
            UUID orderId,
            UUID paymentId,
            String checkoutId,
            UUID productId,
            String providerUserId
    ) {
    }

    private record PaidScenario(
            Store store,
            String adminToken,
            UUID orderId,
            UUID paymentId,
            UUID productId,
            String providerUserId
    ) {
    }
}
