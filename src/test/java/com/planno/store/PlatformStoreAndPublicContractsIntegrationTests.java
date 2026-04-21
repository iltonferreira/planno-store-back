package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.store.dto.PlatformStoreRequest;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlatformStoreAndPublicContractsIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";

    @Test
    void platformAdminShouldManageStoresAndCommission() throws Exception {
        String platformToken = platformAdminToken("platform@example.com");

        String createResponse = mockMvc.perform(post("/api/v1/platform/stores")
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformStoreRequest(
                                "Loja Plataforma",
                                "loja-plataforma",
                                "loja@example.com",
                                "BRL",
                                "America/Sao_Paulo",
                                "Descricao",
                                null,
                                null,
                                "#111111",
                                "#eeeeee",
                                new BigDecimal("7.50")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("loja-plataforma"))
                .andExpect(jsonPath("$.commissionRate").value(7.50))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String storeId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/platform/stores")
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/v1/platform/stores/{id}", storeId)
                        .header("Authorization", "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlatformStoreRequest(
                                "Loja Plataforma Editada",
                                "loja-plataforma-editada",
                                "loja@example.com",
                                "BRL",
                                "America/Sao_Paulo",
                                "Descricao 2",
                                "https://cdn.example.com/logo.png",
                                "https://cdn.example.com/banner.png",
                                "#222222",
                                "#dddddd",
                                new BigDecimal("9.25")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("loja-plataforma-editada"))
                .andExpect(jsonPath("$.bannerUrl").value("https://cdn.example.com/banner.png"))
                .andExpect(jsonPath("$.commissionRate").value(9.25));

        mockMvc.perform(delete("/api/v1/platform/stores/{id}", storeId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/platform/stores/{id}", storeId)
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void publicContractsShouldExposeOrderLookupAndTransparentCapabilities() throws Exception {
        Scenario scenario = createScenario("store-a", "admin@example.com", "produto-publico", "SKU-PUB", "40.00", "12.90", 2, 8);

        mockMvc.perform(get("/api/v1/public/payments/transparent/capabilities")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.publicKey").value(nullValue()));

        connectMercadoPagoAccount("store-a", scenario.adminToken(), "oauth-public-capabilities");

        mockMvc.perform(get("/api/v1/public/payments/transparent/capabilities")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.publicKey").value(notNullValue()));

        mockMvc.perform(get("/api/v1/public/orders/{orderId}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, scenario.cartToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(scenario.orderId().toString()))
                .andExpect(jsonPath("$.totalAmount").value(92.90))
                .andExpect(jsonPath("$.latestPaymentStatus").value(nullValue()));

        mockMvc.perform(get("/api/v1/public/orders/{orderId}", scenario.orderId())
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, createCart("store-a")))
                .andExpect(status().isNotFound());
    }

    @Test
    void storeAdminAndPlatformAdminShouldUploadBrandingAssets() throws Exception {
        Store store = createStore("store-assets");
        User admin = createUser("admin-assets@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String adminToken = tokenFor(admin.getEmail());
        String platformToken = platformAdminToken("platform-assets@example.com");

        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", "fake-image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/store/current/assets/banner")
                        .file(file)
                        .header(STORE_HEADER, "store-assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("store-assets-test"))
                .andExpect(jsonPath("$.publicUrl").value(notNullValue()));

        mockMvc.perform(multipart("/api/v1/platform/stores/{storeId}/assets/logo", store.getId())
                        .file(new MockMultipartFile("file", "logo.png", "image/png", "fake-image".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("store-assets-test"))
                .andExpect(jsonPath("$.publicUrl").value(notNullValue()));

        mockMvc.perform(get("/api/v1/admin/store/current")
                        .header(STORE_HEADER, "store-assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(notNullValue()))
                .andExpect(jsonPath("$.bannerUrl").value(notNullValue()));
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

        return new Scenario(store, adminToken, UUID.fromString(objectMapper.readTree(orderResponse).get("id").asText()), cartToken);
    }

    private String connectMercadoPagoAccount(String storeSlug, String adminToken, String oauthCode) throws Exception {
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

        return authorizationUrl;
    }

    private String extractQueryParam(String url, String param) {
        String query = URI.create(url).getQuery();
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

    private void updateInventory(String adminToken, String storeSlug, String productId, int available, int reserved) throws Exception {
        mockMvc.perform(put("/api/v1/admin/inventory/{productId}", productId)
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryUpdateRequest(available, reserved))))
                .andExpect(status().isOk());
    }

    private String createCart(String storeSlug) throws Exception {
        String response = mockMvc.perform(post("/api/v1/public/cart")
                        .header(STORE_HEADER, storeSlug))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("guestToken").asText();
    }

    private void addCartItem(String storeSlug, String cartToken, String productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, storeSlug)
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":%s}
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

    private record Scenario(Store store, String adminToken, UUID orderId, String cartToken) {
    }
}
