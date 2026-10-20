package com.planno.store;

import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTests extends AbstractIntegrationTest {

    @Test
    void cloudflarePagesPreviewCanReadButCannotMutateStoreApi() throws Exception {
        createStore("preview-store");
        String previewOrigin = "https://change-ui.planno-store-preview-store.pages.dev";
        String productionOrigin = "https://planno-store-preview-store.pages.dev";

        mockMvc.perform(get("/api/v1/public/store/current")
                        .header(STORE_HEADER, "preview-store")
                        .header("Origin", previewOrigin)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/public/cart")
                        .header(STORE_HEADER, "preview-store")
                        .header("Origin", previewOrigin))
                .andExpect(status().isForbidden());

        mockMvc.perform(options("/api/v1/public/cart")
                        .header("Origin", previewOrigin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "x-store-slug"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/public/cart")
                        .header(STORE_HEADER, "preview-store")
                        .header("Origin", productionOrigin))
                .andExpect(status().isCreated());
    }

    @Test
    void readinessProbeShouldBePublicAndCheckDatabaseAvailability() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void publicRoutesShouldBeAccessibleAndProtectedRoutesShouldRespectRoles() throws Exception {
        Store store = createStore("store-a");

        User admin = createUser("admin@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);

        User customer = createUser("customer@example.com", "password123");
        assignRole(customer, RoleName.CUSTOMER, store);

        mockMvc.perform(get("/api/v1/public/store/current")
                        .header(STORE_HEADER, "store-a")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("store-a"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/store/current")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + tokenFor(customer.getEmail())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/store/current")
                        .header(STORE_HEADER, "store-a")
                        .header("Authorization", "Bearer " + tokenFor(admin.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("store-a"));
    }
    @Test
    void inactiveAndDeletedAccountsCannotReuseAccessToken() throws Exception {
        Store store = createStore("status-store");
        User admin = createUser("status@example.com", "password123");
        assignRole(admin, RoleName.ADMIN, store);
        String token = tokenFor(admin.getEmail());
        jdbcTemplate.update("update users set status = 'INACTIVE' where id = ?", admin.getId());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        assignmentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminRoleDoesNotCarryOverToAnotherStore() throws Exception {
        Store a = createStore("scope-a"), b = createStore("scope-b");
        User user = createUser("scoped@example.com", "password123");
        assignRole(user, RoleName.ADMIN, a);
        assignRole(user, RoleName.CUSTOMER, b);
        String token = tokenFor(user.getEmail());
        mockMvc.perform(get("/api/v1/admin/store/current").header(STORE_HEADER, "scope-a")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/store/current").header(STORE_HEADER, "scope-b")
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/store/current")
                .header(STORE_HEADER, "scope-b").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/store/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassignedAdminCannotFallBackToFirstStore() throws Exception {
        createStore("unassigned-store");
        User user = createUser("unassigned@example.com", "password123");
        assignRole(user, RoleName.ADMIN, null);
        mockMvc.perform(get("/api/v1/admin/store/current").header(STORE_HEADER, "unassigned-store")
                .header("Authorization", "Bearer " + tokenFor(user.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeInvalidatesExistingAccessToken() throws Exception {
        Store store = createStore("password-store");
        User user = createUser("password@example.com", "password123");
        assignRole(user, RoleName.ADMIN, store);
        String token = tokenFor(user.getEmail());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password123\",\"newPassword\":\"new-password-12345\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokenFor(user.getEmail())))
                .andExpect(status().isOk());
    }

    @Test
    void malformedLoginAndWrongCurrentPasswordHaveClientErrorStatus() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        User user = createUser("wrong-password@example.com", "password123");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/change-password")
                .header("Authorization", "Bearer " + tokenFor(user.getEmail())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-12345\"}"))
                .andExpect(status().isUnauthorized());
    }

}
