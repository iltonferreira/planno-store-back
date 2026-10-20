package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.payment.dto.CreatePaymentRequest;
import com.planno.store.payment.refund.dto.CreateRefundRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReconciliationIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";
    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    @Autowired
    private StubMercadoPagoPaymentProviderClient stubMercadoPagoPaymentProviderClient;

    @Test
    void adminShouldListWebhookEventsByStoreAndProtectOtherStores() throws Exception {
        PendingScenario storeA = createPendingPaymentScenario("store-a", "admin-a@example.com", "produto-a", "SKU-A", "40.00", "12.90", 2, 8);
        PendingScenario storeB = createPendingPaymentScenario("store-b", "admin-b@example.com", "produto-b", "SKU-B", "30.00", "10.00", 1, 5);

        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(storeA.checkoutId(), com.planno.store.payment.domain.PaymentStatus.APPROVED, "pix");
        sendWebhook("evt-store-a", storeA.checkoutId(), paymentWebhookPayload(storeA.checkoutId(), storeA.providerUserId()));

        String failedRefundId = "unknown-refund-id";
        mockMvc.perform(post("/api/v1/public/webhooks/mercado-pago")
                        .queryParam("data.id", failedRefundId)
                        .header("x-request-id", "evt-failed-refund")
                        .header("x-signature", signatureHeader("evt-failed-refund", failedRefundId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refundWebhookPayload(failedRefundId, storeA.providerUserId())))
                .andExpect(status().isBadRequest());

        String listResponse = mockMvc.perform(get("/api/v1/admin/webhooks/events")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String eventId = objectMapper.readTree(listResponse).get(0).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/webhooks/events/{id}", eventId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("access_token"))));

        mockMvc.perform(get("/api/v1/admin/webhooks/events")
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + storeB.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/admin/webhooks/events/{id}", eventId)
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + storeB.adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reprocessingShouldBeIdempotentAndRegisterResult() throws Exception {
        PendingScenario scenario = createPendingPaymentScenario("store-a", "admin@example.com", "produto-reprocess", "SKU-REPROC", "40.00", "12.90", 2, 8);
        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(scenario.checkoutId(), com.planno.store.payment.domain.PaymentStatus.APPROVED, "pix");
        sendWebhook("evt-reprocess", scenario.checkoutId(), paymentWebhookPayload(scenario.checkoutId(), scenario.providerUserId()));

        String listResponse = mockMvc.perform(get("/api/v1/admin/webhooks/events")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String eventId = objectMapper.readTree(listResponse).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/admin/webhooks/events/{id}/reprocess", eventId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("WEBHOOK_EVENT"));

        mockMvc.perform(get("/api/v1/admin/webhooks/events/{id}", eventId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessCount").value(1))
                .andExpect(jsonPath("$.lastReprocessResult").value(containsString("successfully")));

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(6, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());
    }

    @Test
    void paymentReconciliationShouldFixDivergenceAndRemainIdempotent() throws Exception {
        PendingScenario scenario = createPendingPaymentScenario("store-a", "admin@example.com", "produto-pay-rec", "SKU-PAYREC", "40.00", "12.90", 2, 8);
        stubMercadoPagoPaymentProviderClient.simulatePaymentStatus(scenario.checkoutId(), com.planno.store.payment.domain.PaymentStatus.APPROVED, "pix");

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/reconcile", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/reconcile", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        mockMvc.perform(get("/api/v1/admin/payments/{paymentId}", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(content().string(not(containsString("commissionAmount"))));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(6, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());

        Store storeB = createStore("store-b");
        User adminB = createUser("admin-b@example.com", "password123");
        assignRole(adminB, RoleName.ADMIN, storeB);
        String adminTokenB = tokenFor(adminB.getEmail());

        mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/reconcile", scenario.paymentId())
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + adminTokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void refundReconciliationShouldFixDivergenceWithoutDoubleInventoryReturn() throws Exception {
        PaidScenario scenario = createPaidScenario("store-a", "admin@example.com", "produto-ref-rec", "SKU-REFREC", "40.00", "12.90", 2, 8);

        String refundResponse = mockMvc.perform(post("/api/v1/admin/payments/{paymentId}/refunds", scenario.paymentId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRefundRequest(new BigDecimal("92.90"), "refund reconcile"))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID refundId = UUID.fromString(objectMapper.readTree(refundResponse).get("id").asText());
        jdbcTemplate.update("update refunds set status = 'REQUESTED', processed_at = null where id = ?", refundId);
        jdbcTemplate.update("update payments set status = 'APPROVED' where id = ?", scenario.paymentId());
        jdbcTemplate.update("update orders set status = 'PAID' where id = ?", scenario.orderId());

        mockMvc.perform(post("/api/v1/admin/refunds/{refundId}/reconcile", refundId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true));

        mockMvc.perform(post("/api/v1/admin/refunds/{refundId}/reconcile", refundId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false));

        mockMvc.perform(get("/api/v1/admin/refunds/{refundId}", refundId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + scenario.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        var inventory = inventoryItemRepository.findByProductIdAndProductStoreId(scenario.productId(), scenario.store().getId()).orElseThrow();
        Assertions.assertEquals(8, inventory.getQuantityAvailable());
        Assertions.assertEquals(0, inventory.getQuantityReserved());

        Store storeB = createStore("store-b");
        User adminB = createUser("admin-b@example.com", "password123");
        assignRole(adminB, RoleName.ADMIN, storeB);
        String adminTokenB = tokenFor(adminB.getEmail());
        mockMvc.perform(post("/api/v1/admin/refunds/{refundId}/reconcile", refundId)
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + adminTokenB))
                .andExpect(status().isNotFound());
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
