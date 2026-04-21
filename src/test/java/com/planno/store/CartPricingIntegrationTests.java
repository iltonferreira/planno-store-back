package com.planno.store;

import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.core.storecontext.StoreContext;
import com.planno.store.core.storecontext.StoreContextHolder;
import com.planno.store.core.storecontext.StoreContextSource;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.pricing.dto.PriceCalculation;
import com.planno.store.pricing.service.PricingService;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartPricingIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";

    @Autowired
    private PricingService pricingService;

    @AfterEach
    void clearStoreContext() {
        StoreContextHolder.clear();
    }

    @Test
    void pricingServiceShouldCalculateRoundedBreakdownThroughCentralService() {
        Store store = createStore("store-a");
        var product = productRepository.saveAndFlush(com.planno.store.catalog.domain.Product.create(
                store,
                null,
                "Produto",
                "produto",
                "SKU-001",
                "Descricao",
                ProductStatus.ACTIVE,
                new BigDecimal("10"),
                null
        ));

        StoreContextHolder.set(new StoreContext(store.getId(), store.getSlug(), StoreContextSource.DEFAULT_ACTIVE_STORE));

        PriceCalculation calculation = pricingService.calculatePrice(product.getId(), 3);

        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("10.00"), calculation.basePrice());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("10.00"), calculation.finalPrice());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("0.00"), calculation.discountAmount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("30.00"), calculation.lineFinalPrice());
    }

    @Test
    void cartLifecycleShouldRespectPricingInventoryAndStoreIsolation() throws Exception {
        Store store = createStore("store-a");
        createStore("store-b");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String adminToken = tokenFor(admin.getEmail());

        String productId = createProduct(adminToken, "store-a", "produto-ativo", "SKU-100", ProductStatus.ACTIVE, "25.50");
        updateInventory(adminToken, "store-a", productId, 10, 1);

        String cartToken = createCart("store-a");

        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.cart.dto.CartItemRequest(
                                UUID.fromString(productId),
                                2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity", is(2)))
                .andExpect(jsonPath("$.items[0].unitFinalPrice", is(25.50)))
                .andExpect(jsonPath("$.items[0].totalFinalPrice", is(51.00)))
                .andExpect(jsonPath("$.subtotal", is(51.00)))
                .andExpect(jsonPath("$.total", is(51.00)));

        String itemId = mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String cartItemId = objectMapper.readTree(itemId).get("items").get(0).get("id").asText();

        mockMvc.perform(put("/api/v1/public/cart/items/{id}", cartItemId)
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.cart.dto.UpdateCartItemRequest(4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity", is(4)))
                .andExpect(jsonPath("$.subtotal", is(102.00)));

        mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, "store-b")
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/public/cart/items/{id}", cartItemId)
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/public/cart")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.subtotal", is(0.00)));
    }

    @Test
    void cartShouldRejectInsufficientStockAndInactiveProducts() throws Exception {
        Store store = createStore("store-a");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String adminToken = tokenFor(admin.getEmail());

        String activeProductId = createProduct(adminToken, "store-a", "produto-limitado", "SKU-200", ProductStatus.ACTIVE, "9.90");
        updateInventory(adminToken, "store-a", activeProductId, 2, 0);

        String inactiveProductId = createProduct(adminToken, "store-a", "produto-inativo", "SKU-201", ProductStatus.INACTIVE, "9.90");
        updateInventory(adminToken, "store-a", inactiveProductId, 20, 0);

        String cartToken = createCart("store-a");

        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.cart.dto.CartItemRequest(
                                UUID.fromString(activeProductId),
                                3
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/public/cart/items")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.planno.store.cart.dto.CartItemRequest(
                                UUID.fromString(inactiveProductId),
                                1
                        ))))
                .andExpect(status().isBadRequest());
    }

    private String createCart(String storeSlug) throws Exception {
        String response = mockMvc.perform(post("/api/v1/public/cart")
                        .header(STORE_HEADER, storeSlug))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cartToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("cartToken").asText();
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

    private void updateInventory(String adminToken, String storeSlug, String productId, long available, long reserved) throws Exception {
        mockMvc.perform(put("/api/v1/admin/inventory/{productId}", productId)
                        .header(STORE_HEADER, storeSlug)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryUpdateRequest(available, reserved))))
                .andExpect(status().isOk());
    }
}
