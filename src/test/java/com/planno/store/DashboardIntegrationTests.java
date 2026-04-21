package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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

class DashboardIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";

    @Test
    void storeDashboardShouldBeScopedAndMustNotExposeCommission() throws Exception {
        Scenario storeA = createScenario("store-a", "admin-a@example.com", "produto-a", "SKU-A", "40.00", "12.90", 2, 10);
        Scenario storeB = createScenario("store-b", "admin-b@example.com", "produto-b", "SKU-B", "30.00", "10.00", 1, 10);

        markOrderPaid(storeA.orderId());
        cancelOrder(storeA.waitingOrderId());
        markOrderPaid(storeB.orderId());

        String summaryResponse = mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(92.90))
                .andExpect(jsonPath("$.paidOrdersCount").value(1))
                .andExpect(jsonPath("$.waitingPaymentOrdersCount").value(0))
                .andExpect(jsonPath("$.cancelledOrdersCount").value(1))
                .andExpect(jsonPath("$.ordersCount").value(2))
                .andExpect(jsonPath("$.averageTicket").value(92.90))
                .andExpect(jsonPath("$.recentOrders", hasSize(2)))
                .andExpect(jsonPath("$.topProducts", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Assertions.assertFalse(summaryResponse.contains("commissionAmount"));
        Assertions.assertFalse(summaryResponse.contains("commissionRate"));
        Assertions.assertFalse(summaryResponse.contains("sellerNetAmount"));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", storeA.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("commissionAmount"))))
                .andExpect(content().string(not(containsString("commissionRate"))))
                .andExpect(content().string(not(containsString("sellerNetAmount"))))
                .andExpect(jsonPath("$.totalAmount").value(92.90))
                .andExpect(jsonPath("$.shippingAmount").value(12.90))
                .andExpect(jsonPath("$.subtotalAmount").value(80.00));

        mockMvc.perform(get("/api/v1/admin/payments/orders/{orderId}", storeA.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("commissionAmount"))))
                .andExpect(content().string(not(containsString("commissionRate"))))
                .andExpect(content().string(not(containsString("sellerNetAmount"))));
    }

    @Test
    void storeDashboardShouldApplyCustomDateRange() throws Exception {
        Scenario scenario = createScenario("store-range", "admin-range@example.com", "produto-range-a", "SKU-RANGE-A", "50.00", "10.00", 1, 10);
        markOrderPaid(scenario.orderId());

        Scenario secondScenario = createScenario("store-range", "admin-range@example.com", "produto-range-b", "SKU-RANGE-B", "20.00", "5.00", 1, 10);
        markOrderPaid(secondScenario.orderId());
        moveOrderCreatedAt(scenario.orderId(), OffsetDateTime.now().minusDays(40));

        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();

                mockMvc.perform(get("/api/v1/admin/dashboard/summary")
                        .header(STORE_HEADER, "store-range")
                        .header("Authorization", "Bearer " + scenario.adminToken())
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(25.00))
                .andExpect(jsonPath("$.paidOrdersCount").value(1))
                .andExpect(jsonPath("$.waitingPaymentOrdersCount").value(2))
                .andExpect(jsonPath("$.ordersCount").value(3));
    }

    @Test
    void platformDashboardShouldRequirePlatformAdminAndShowCommission() throws Exception {
        Scenario storeA = createScenario("store-a", "admin-a@example.com", "produto-a", "SKU-A", "40.00", "12.90", 2, 10);
        Scenario storeB = createScenario("store-b", "admin-b@example.com", "produto-b", "SKU-B", "30.00", "10.00", 1, 10);
        markOrderPaid(storeA.orderId());
        markOrderPaid(storeB.orderId());

        String platformToken = platformAdminToken("platform@example.com");

        mockMvc.perform(get("/api/v1/platform/dashboard/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/platform/dashboard/summary")
                        .header("Authorization", "Bearer " + storeA.adminToken()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/platform/dashboard/summary")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grossRevenue").value(132.90))
                .andExpect(jsonPath("$.platformCommissionTotal").value(3.32))
                .andExpect(jsonPath("$.storesCount").value(2))
                .andExpect(jsonPath("$.paidOrdersCount").value(2))
                .andExpect(jsonPath("$.commissionByStore", hasSize(2)))
                .andExpect(jsonPath("$.topStores", hasSize(2)));

        mockMvc.perform(get("/api/v1/platform/dashboard/commission-by-store")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platformCommissionTotal").exists());
    }

    private String platformAdminToken(String email) {
        User platformAdmin = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> createUser(email, "password123"));
        if (assignmentRepository.findAll().stream().noneMatch(it ->
                it.getUser().getId().equals(platformAdmin.getId())
                        && it.getRole().getName() == RoleName.PLATFORM_ADMIN
                        && it.getStore() == null)) {
            assignRole(platformAdmin, RoleName.PLATFORM_ADMIN, null);
        }
        return tokenFor(platformAdmin.getEmail());
    }

    private void markOrderPaid(UUID orderId) {
        jdbcTemplate.update("update orders set status = 'PAID' where id = ?", orderId);
    }

    private void cancelOrder(UUID orderId) {
        jdbcTemplate.update("update orders set status = ? where id = ?", OrderStatus.CANCELLED.name(), orderId);
    }

    private void moveOrderCreatedAt(UUID orderId, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                "update orders set created_at = ?, updated_at = ? where id = ?",
                createdAt,
                createdAt,
                orderId
        );
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
        if (assignmentRepository.findAll().stream().noneMatch(it ->
                it.getUser().getId().equals(admin.getId())
                        && it.getStore() != null
                        && it.getStore().getId().equals(store.getId()))) {
            assignRole(admin, RoleName.ADMIN, store);
        }
        String adminToken = tokenFor(admin.getEmail());

        String shippingMethodId = createShippingMethod(adminToken, storeSlug, "Padrao", shippingPrice, 4, true);
        String productId = createProduct(adminToken, storeSlug, productSlug, sku, ProductStatus.ACTIVE, price);
        updateInventory(adminToken, storeSlug, productId, stock, 0);

        UUID firstOrderId = createOrder(storeSlug, productId, quantity, shippingMethodId);
        UUID waitingOrderId = createOrder(storeSlug, productId, quantity, shippingMethodId);

        return new Scenario(store, adminToken, firstOrderId, waitingOrderId);
    }

    private UUID createOrder(String storeSlug, String productId, int quantity, String shippingMethodId) throws Exception {
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

        return UUID.fromString(objectMapper.readTree(orderResponse).get("id").asText());
    }

    private String createShippingMethod(String adminToken, String storeSlug, String name, String price, int estimatedDays, boolean active) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/shipping/methods")
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingMethodRequest(
                                name,
                                com.planno.store.shipping.domain.ShippingMethodType.FIXED,
                                new BigDecimal(price),
                                estimatedDays,
                                active
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String createProduct(String adminToken, String storeSlug, String productSlug, String sku, ProductStatus status, String basePrice) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                null,
                                "Produto " + productSlug,
                                productSlug,
                                sku,
                                "Descricao",
                                status,
                                new BigDecimal(basePrice),
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

    private void addCartItem(String storeSlug, String cartToken, String productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":%d}
                                """.formatted(productId, quantity)))
                .andExpect(status().isOk());
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

    private record Scenario(
            Store store,
            String adminToken,
            UUID orderId,
            UUID waitingOrderId
    ) {
    }
}
