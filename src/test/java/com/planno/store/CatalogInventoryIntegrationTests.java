package com.planno.store;

import com.planno.store.catalog.dto.CategoryRequest;
import com.planno.store.catalog.dto.ProductRequest;
import com.planno.store.catalog.domain.CategoryStatus;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.inventory.dto.InventoryUpdateRequest;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogInventoryIntegrationTests extends AbstractIntegrationTest {

    @Test
    void catalogCrudAndInventoryUpdateShouldWorkInsideCurrentStore() throws Exception {
        Store store = createStore("store-a");
        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String token = tokenFor(admin.getEmail());

        String categoryJson = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(
                                "Eletronicos",
                                "eletronicos",
                                "Categoria principal",
                                CategoryStatus.ACTIVE
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("eletronicos"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String categoryId = objectMapper.readTree(categoryJson).get("id").asText();

        String productJson = mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductRequest(
                                java.util.UUID.fromString(categoryId),
                                "Notebook Pro",
                                "notebook-pro",
                                "NOTE-001",
                                "Produto ativo",
                                ProductStatus.ACTIVE,
                                new BigDecimal("5999.90"),
                                "https://img.example.com/notebook-pro.png"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("notebook-pro"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String productId = objectMapper.readTree(productJson).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/inventory/{productId}", productId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(0))
                .andExpect(jsonPath("$.quantityReserved").value(0));

        mockMvc.perform(put("/api/v1/admin/inventory/{productId}", productId)
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InventoryUpdateRequest(15, 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityAvailable").value(15))
                .andExpect(jsonPath("$.quantityReserved").value(3));

        mockMvc.perform(get("/api/v1/public/categories")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("eletronicos"));

        mockMvc.perform(get("/api/v1/public/products")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("notebook-pro"));

        mockMvc.perform(get("/api/v1/public/products/notebook-pro")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NOTE-001"));
    }

    @Test
    void uniquenessShouldBePerStoreAndInactiveProductsShouldNotLeakPublicly() throws Exception {
        Store storeA = createStore("store-a");
        Store storeB = createStore("store-b");

        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, storeA);
        assignRole(admin, RoleName.ADMIN, storeB);
        String token = tokenFor(admin.getEmail());

        String categoryPayload = objectMapper.writeValueAsString(new CategoryRequest(
                "Eletronicos",
                "eletronicos",
                "Categoria",
                CategoryStatus.ACTIVE
        ));

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryPayload))
                .andExpect(status().isConflict());

        String inactiveProductPayload = objectMapper.writeValueAsString(new ProductRequest(
                null,
                "Produto Inativo",
                "produto-inativo",
                "SKU-001",
                "Nao publicar",
                ProductStatus.INACTIVE,
                new BigDecimal("10.00"),
                null
        ));

        String activeProductPayload = objectMapper.writeValueAsString(new ProductRequest(
                null,
                "Produto Ativo",
                "produto-ativo",
                "SKU-002",
                "Publico",
                ProductStatus.ACTIVE,
                new BigDecimal("20.00"),
                null
        ));

        String sameSlugDifferentStorePayload = objectMapper.writeValueAsString(new ProductRequest(
                null,
                "Produto Outro Store",
                "produto-ativo",
                "SKU-002",
                "Permitido em outra store",
                ProductStatus.ACTIVE,
                new BigDecimal("30.00"),
                null
        ));

        mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inactiveProductPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activeProductPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sameSlugDifferentStorePayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/products")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activeProductPayload))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/public/products")
                        .header(STORE_HEADER, "store-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug", is("produto-ativo")));

        mockMvc.perform(get("/api/v1/public/products")
                        .header(STORE_HEADER, "store-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug", is("produto-ativo")))
                .andExpect(jsonPath("$[0].storeId", not(storeA.getId().toString())));
    }

    @Test
    void storeAccessShouldBeIsolatedForAdminRequests() throws Exception {
        Store storeA = createStore("store-a");
        createStore("store-b");

        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, storeA);
        String token = tokenFor(admin.getEmail());

        mockMvc.perform(get("/api/v1/admin/categories")
                        .header(STORE_HEADER, "store-b")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
