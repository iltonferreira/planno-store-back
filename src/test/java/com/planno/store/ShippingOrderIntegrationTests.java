package com.planno.store;

import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.order.dto.UpdateOrderStatusRequest;
import com.planno.store.shipping.domain.ShippingMethodType;
import com.planno.store.shipping.dto.AddressRequest;
import com.planno.store.shipping.dto.ShippingMethodRequest;
import com.planno.store.shipping.dto.ShippingQuoteRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShippingOrderIntegrationTests extends AbstractIntegrationTest {

    private static final String CART_HEADER = "X-Cart-Token";

    @Test
    void shippingMethodsShouldBeManagedPerStoreAndQuotedForValidCart() throws Exception {
        Store storeA = createStore("store-a");
        Store storeB = createStore("store-b");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, storeA);
        assignRole(admin, RoleName.ADMIN, storeB);
        String adminToken = tokenFor(admin.getEmail());

        String shippingMethodA = createShippingMethod(adminToken, "store-a", "Entrega fixa", "15.00", 3, true);
        createShippingMethod(adminToken, "store-b", "Entrega outra store", "25.00", 5, true);
        createShippingMethod(adminToken, "store-a", "Inativo", "99.00", 10, false);

        String productId = createProduct(adminToken, "store-a", "produto-a", "SKU-A", ProductStatus.ACTIVE, "50.00");
        updateInventory(adminToken, "store-a", productId, 10, 0);
        String cartToken = createCart("store-a");
        addCartItem("store-a", cartToken, productId, 1);

        mockMvc.perform(get("/api/v1/admin/shipping/methods")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(post("/api/v1/public/shipping/quote")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingQuoteRequest(
                                currentCartId("store-a", cartToken),
                                sampleAddress()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shippingMethodId").value(shippingMethodA))
                .andExpect(jsonPath("$[0].price").value(15.00));

        mockMvc.perform(post("/api/v1/public/shipping/quote")
                        .header(STORE_HEADER, "store-b")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ShippingQuoteRequest(
                                currentCartId("store-a", cartToken),
                                sampleAddress()
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void orderCreationShouldPersistSnapshotsReserveStockAndSupportCancellation() throws Exception {
        Store store = createStore("store-a");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String adminToken = tokenFor(admin.getEmail());

        String shippingMethodId = createShippingMethod(adminToken, "store-a", "Expressa", "12.90", 2, true);
        String productId = createProduct(adminToken, "store-a", "produto-pedido", "SKU-ORDER", ProductStatus.ACTIVE, "40.00");
        updateInventory(adminToken, "store-a", productId, 8, 0);

        String cartToken = createCart("store-a");
        addCartItem("store-a", cartToken, productId, 2);
        UUID cartId = currentCartId("store-a", cartToken);

        String orderResponse = mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, "store-a")
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
                .andExpect(jsonPath("$.status").value("WAITING_PAYMENT"))
                .andExpect(jsonPath("$.subtotalAmount").value(80.00))
                .andExpect(jsonPath("$.shippingAmount").value(12.90))
                .andExpect(jsonPath("$.totalAmount").value(92.90))
                .andExpect(jsonPath("$.shippingMethodName").value("Expressa"))
                .andExpect(jsonPath("$.address.recipientName").value("Cliente Teste"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(40.00))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].newStatus").value("WAITING_PAYMENT"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = objectMapper.readTree(orderResponse).get("id").asText();

        var inventoryItem = inventoryItemRepository.findByProductIdAndProductStoreId(UUID.fromString(productId), store.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(2, inventoryItem.getQuantityReserved());
        org.junit.jupiter.api.Assertions.assertEquals(8, inventoryItem.getQuantityAvailable());

        mockMvc.perform(get("/api/v1/admin/orders")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/admin/orders/{id}", orderId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", notNullValue()))
                .andExpect(jsonPath("$.status").value("WAITING_PAYMENT"));

        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(
                                OrderStatus.CANCELLED,
                                "Cliente desistiu"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)))
                .andExpect(jsonPath("$.statusHistory[1].newStatus").value("CANCELLED"));

        var inventoryAfterCancel = inventoryItemRepository.findByProductIdAndProductStoreId(UUID.fromString(productId), store.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, inventoryAfterCancel.getQuantityReserved());
    }

    @Test
    void orderCreationShouldFailForInvalidCheckoutConditionsAndInvalidTransitions() throws Exception {
        Store store = createStore("store-a");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String adminToken = tokenFor(admin.getEmail());

        String shippingMethodId = createShippingMethod(adminToken, "store-a", "Padrao", "10.00", 4, true);
        String productId = createProduct(adminToken, "store-a", "produto-valido", "SKU-VALID", ProductStatus.ACTIVE, "30.00");
        updateInventory(adminToken, "store-a", productId, 1, 0);

        String emptyCartToken = createCart("store-a");
        UUID emptyCartId = currentCartId("store-a", emptyCartToken);

        mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, emptyCartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                emptyCartId,
                                UUID.fromString(shippingMethodId),
                                "Cliente",
                                "cliente@example.com",
                                null,
                                sampleAddress()
                        ))))
                .andExpect(status().isBadRequest());

        String cartToken = createCart("store-a");
        addCartItem("store-a", cartToken, productId, 1);
        UUID cartId = currentCartId("store-a", cartToken);

        mockMvc.perform(put("/api/v1/admin/products/{id}", productId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                null,
                                "Produto valido",
                                "produto-valido",
                                "SKU-VALID",
                                "agora inativo",
                                ProductStatus.INACTIVE,
                                new BigDecimal("30.00"),
                                null
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                cartId,
                                UUID.fromString(shippingMethodId),
                                "Cliente",
                                "cliente@example.com",
                                null,
                                sampleAddress()
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/admin/products/{id}", productId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                null,
                                "Produto valido",
                                "produto-valido",
                                "SKU-VALID",
                                "ativo de novo",
                                ProductStatus.ACTIVE,
                                new BigDecimal("30.00"),
                                null
                        ))))
                .andExpect(status().isOk());

        updateInventory(adminToken, "store-a", productId, 1, 1);

        mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, cartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                cartId,
                                UUID.fromString(shippingMethodId),
                                "Cliente",
                                "cliente@example.com",
                                null,
                                sampleAddress()
                        ))))
                .andExpect(status().isBadRequest());

        updateInventory(adminToken, "store-a", productId, 5, 0);
        String validCartToken = createCart("store-a");
        addCartItem("store-a", validCartToken, productId, 1);
        UUID validCartId = currentCartId("store-a", validCartToken);

        String createdOrder = mockMvc.perform(post("/api/v1/public/orders")
                        .header(STORE_HEADER, "store-a")
                        .header(CART_HEADER, validCartToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateOrderRequest(
                                validCartId,
                                UUID.fromString(shippingMethodId),
                                "Cliente",
                                "cliente@example.com",
                                null,
                                sampleAddress()
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderId = objectMapper.readTree(createdOrder).get("id").asText();

        mockMvc.perform(patch("/api/v1/admin/orders/{id}/status", orderId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateOrderStatusRequest(
                                OrderStatus.PAID,
                                "nao permitido"
                        ))))
                .andExpect(status().isBadRequest());

        createStore("store-b");
        mockMvc.perform(get("/api/v1/admin/orders/{id}", orderId)
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
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
}
