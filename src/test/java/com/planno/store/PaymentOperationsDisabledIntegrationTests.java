package com.planno.store;

import com.planno.store.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "app.payment.operations-enabled=false")
class PaymentOperationsDisabledIntegrationTests extends AbstractIntegrationTest {
    @Test
    void disabledCheckoutRejectsOrdersAndPaymentsBeforeAnySideEffects() throws Exception {
        createStore("demo-disabled");
        for (String path : java.util.List.of("/api/v1/public/orders", "/api/v1/public/payments",
                "/api/v1/public/payments/transparent")) {
            mockMvc.perform(post(path).header(STORE_HEADER, "demo-disabled")
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("Checkout indisponível: pagamentos aguardam homologação."));
        }
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(paymentRepository.count()).isZero();
        mockMvc.perform(get("/api/v1/public/payments/mercado-pago/oauth/callback")
                .header(STORE_HEADER, "demo-disabled").param("code", "unused").param("state", "unused"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/v1/public/cart").header(STORE_HEADER, "demo-disabled"))
                .andExpect(status().isCreated());
    }
}
