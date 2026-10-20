package com.planno.store.core.config;

import com.planno.store.payment.config.PaymentProperties;
import com.planno.store.media.config.SupabaseStorageProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!test & !dev & !local")
public class ProductionSafetyConfiguration {
    @Bean ApplicationRunner preventMockIntegrations(PaymentProperties payment, SupabaseStorageProperties media) {
        return arguments -> {
            if (payment.mercadoPago().mockEnabled() || media.mockEnabled()) {
                throw new IllegalStateException("Mock integrations require an explicit test, dev or local profile");
            }
        };
    }
}
