package com.planno.store;

import com.planno.store.identity.domain.RoleName;
import com.planno.store.identity.domain.User;
import com.planno.store.store.domain.Store;
import com.planno.store.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTests extends AbstractIntegrationTest {

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
}
