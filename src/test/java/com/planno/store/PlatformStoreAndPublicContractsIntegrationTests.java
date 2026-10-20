package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.store.dto.PlatformStoreRequest;
import com.planno.store.store.provisioning.StoreProvisioningRequest;
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
    private static final String PROVISIONING_TOKEN = "test-store-provisioning-token-with-at-least-32-chars";

    @Test
    void hubShouldProvisionStoreIdempotentlyWithServiceToken() throws Exception {
        StoreProvisioningRequest request = new StoreProvisioningRequest("hub-request-0001", "Loja Exemplo",
                "loja-exemplo", "contato@example.com", "Maria Silva", "Empresa Exemplo", "11999990000",
                "12345678000190", "INITIAL", "admin@example.com", "Ana Admin", null);

        String first = mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY_FOR_CUSTOMIZATION"))
                .andExpect(jsonPath("$.storeId").isNotEmpty())
                .andExpect(jsonPath("$.operationId").isNotEmpty())
                .andExpect(jsonPath("$.provisionalDomain").value("loja-exemplo.planno.test"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.storeId").value(objectMapper.readTree(first).get("storeId").asText()))
                .andExpect(jsonPath("$.operationId").value(objectMapper.readTree(first).get("operationId").asText()));

        org.assertj.core.api.Assertions.assertThat(storeRepository.count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(storeProvisioningRepository.count()).isEqualTo(1);
        java.util.UUID storeId = java.util.UUID.fromString(objectMapper.readTree(first).get("storeId").asText());
        org.assertj.core.api.Assertions.assertThat(storeRepository.findById(storeId).orElseThrow().getPublicDomain())
                .isEqualTo("loja-exemplo.planno.test");
    }

    @Test
    void provisionedStoreAdminMustSetPersonalPasswordBeforeUsingAdminApi() throws Exception {
        StoreProvisioningRequest request = new StoreProvisioningRequest("hub-admin-access-0001", "Loja Aurora",
                "loja-aurora", "contato@example.com", "Maria Silva", "Empresa Aurora", null, null,
                "INITIAL", "admin@aurora.example.test", "Ana Admin", null);
        String provisioned = mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String storeId = objectMapper.readTree(provisioned).get("storeId").asText();

        mockMvc.perform(post("/internal/stores/{storeId}/admin-access", storeId))
                .andExpect(status().isUnauthorized());
        String credentials = mockMvc.perform(post("/internal/stores/{storeId}/admin-access", storeId)
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@aurora.example.test"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = objectMapper.readTree(credentials).get("temporaryPassword").asText();

        var temporaryLoginRequest = new com.planno.store.identity.dto.LoginRequest(
                "admin@aurora.example.test", temporaryPassword);
        mockMvc.perform(post("/api/v1/auth/login").header(STORE_HEADER, "loja-aurora")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(temporaryLoginRequest)))
                .andExpect(status().isPreconditionRequired());
        String temporaryCode = lastEmailCode();
        String login = mockMvc.perform(post("/api/v1/auth/login").header(STORE_HEADER, "loja-aurora")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.identity.dto.LoginRequest(
                                "admin@aurora.example.test", temporaryPassword, temporaryCode))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/store/current").header(STORE_HEADER, "loja-aurora")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/auth/change-password").header(STORE_HEADER, "loja-aurora")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + temporaryPassword + "\",\"newPassword\":\"Aurora-Owner-New-Password-2026!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/login").header(STORE_HEADER, "loja-aurora")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@aurora.example.test\",\"password\":\"Aurora-Owner-New-Password-2026!\"}"))
                .andExpect(status().isPreconditionRequired());
        String updatedCode = lastEmailCode();
        String updatedLogin = mockMvc.perform(post("/api/v1/auth/login").header(STORE_HEADER, "loja-aurora")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.identity.dto.LoginRequest(
                                "admin@aurora.example.test", "Aurora-Owner-New-Password-2026!", updatedCode))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();
        String authenticated = objectMapper.readTree(updatedLogin).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/admin/store/current").header(STORE_HEADER, "loja-aurora")
                        .header("Authorization", "Bearer " + authenticated))
                .andExpect(status().isOk());
    }

    private String lastEmailCode() {
        org.mockito.ArgumentCaptor<org.springframework.mail.SimpleMailMessage> message =
                org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);
        org.mockito.Mockito.verify(javaMailSender, org.mockito.Mockito.atLeastOnce()).send(message.capture());
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])")
                .matcher(message.getValue().getText());
        org.assertj.core.api.Assertions.assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    @Test
    void hubProvisioningCannotAssignAnUnreservedExternalDomain() throws Exception {
        StoreProvisioningRequest request = new StoreProvisioningRequest("hub-request-domain-1", "Loja Exemplo",
                "loja-exemplo", "contato@example.com", "Maria Silva", "Empresa Exemplo", null, null,
                "INITIAL", "admin@example.com", "Ana Admin", "cliente.com.br");

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(storeRepository.count()).isZero();
    }

    @Test
    void hubCanUpdateStoreDomainOnlyWithProvisioningServiceToken() throws Exception {
        StoreProvisioningRequest request = new StoreProvisioningRequest("hub-domain-update-001", "Loja Exemplo",
                "loja-dominios", "contato@example.com", "Maria Silva", "Empresa Exemplo", null, null,
                "INITIAL", "admin@example.com", "Ana Admin", "loja-dominios.planno.online");
        String body = mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String storeId = objectMapper.readTree(body).get("storeId").asText();

        mockMvc.perform(put("/internal/stores/{storeId}/domain", storeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"www.cliente.com.br\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/internal/stores/{storeId}/domain", storeId)
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"www.cliente.com.br\"}"))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(storeRepository.findById(UUID.fromString(storeId)).orElseThrow().getPublicDomain())
                .isEqualTo("www.cliente.com.br");
    }

    @Test
    void publicStoreApiAllowsCustomHttpsStorefrontOriginsWithoutCredentials() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/v1/public/products")
                        .header("Origin", "https://www.cliente.com.br")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "https://www.cliente.com.br"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Credentials", nullValue()));
    }

    @Test
    void hubProvisioningShouldRejectInvalidTokenAndIdempotencyKeyReuse() throws Exception {
        StoreProvisioningRequest request = new StoreProvisioningRequest("hub-request-0002", "Loja Protegida",
                "loja-protegida", "contato@example.com", "Maria Silva", "Empresa Exemplo", null, null,
                "INITIAL", "admin@example.com", "Ana Admin", null);

        mockMvc.perform(post("/internal/stores/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        StoreProvisioningRequest changed = new StoreProvisioningRequest("hub-request-0002", "Outra Loja",
                "outra-loja", "contato@example.com", "Maria Silva", "Empresa Exemplo", null, null,
                "INITIAL", "admin@example.com", "Ana Admin", null);
        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", changed.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict());
    }

    @Test
    void hubProvisioningRejectsMissingOrMismatchedIdempotencyHeader() throws Exception {
        StoreProvisioningRequest request = new StoreProvisioningRequest("hub-request-key-001", "Loja Protegida",
                "loja-chave", "contato@example.com", "Maria Silva", "Empresa Exemplo", null, null,
                "INITIAL", "admin-chave@example.com", "Ana Admin", null);

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", "different-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", request.idempotencyKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StoreProvisioningRequest(
                                " " + request.idempotencyKey() + " ", "Loja Protegida", "loja-chave",
                                "contato@example.com", "Maria Silva", "Empresa Exemplo", null, null,
                                "INITIAL", "admin-chave@example.com", "Ana Admin", null))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/internal/stores/provision")
                        .header("X-Planno-Service-Token", PROVISIONING_TOKEN)
                        .header("Idempotency-Key", " " + request.idempotencyKey() + " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(storeRepository.count()).isZero();
    }

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

        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", validPng());

        mockMvc.perform(multipart("/api/v1/admin/store/current/assets/banner")
                        .file(file)
                        .header(STORE_HEADER, "store-assets")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("store-assets-test"))
                .andExpect(jsonPath("$.publicUrl").value(notNullValue()));

        mockMvc.perform(multipart("/api/v1/platform/stores/{storeId}/assets/logo", store.getId())
                        .file(new MockMultipartFile("file", "logo.png", "image/png", validPng()))
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
        return objectMapper.readTree(response).get("cartToken").asText();
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

    private AddressRequest sampleAddress() {
        return new AddressRequest("Cliente Teste", "Rua Principal", "100", "Apto 10", "Centro",
                "Sao Paulo", "SP", "01001000", "BR");
    }

    @Test
    void rejectsSpoofedImagesAndActiveSvgUploads() throws Exception {
        Store store = createStore("upload-security");
        User admin = createUser("upload-security@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String token = tokenFor(admin.getEmail());
        for (String type : java.util.List.of("image/png", "image/jpeg", "image/webp", "image/svg+xml")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/store/current/assets/logo")
                    .file(new MockMultipartFile("file", "logo.png", type, "<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8)))
                    .header(STORE_HEADER, "upload-security").header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/admin/store/current/assets/logo")
                .file(new MockMultipartFile("file", "logo.jpg", "image/jpeg", validPng()))
                .header(STORE_HEADER, "upload-security").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private static byte[] validPng() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private record Scenario(Store store, String adminToken, UUID orderId, String cartToken) {
    }
}
