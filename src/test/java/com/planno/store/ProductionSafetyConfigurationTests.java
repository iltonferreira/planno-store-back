package com.planno.store;

import com.planno.store.core.config.ProductionSafetyConfiguration;
import com.planno.store.media.config.SupabaseStorageProperties;
import com.planno.store.payment.config.PaymentProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSafetyConfigurationTests {

    private PaymentProperties payment(boolean mock) {
        return new PaymentProperties(null, new PaymentProperties.OAuthProperties(10),
                new PaymentProperties.MercadoPagoProperties(mock, null, null, null, null, null, null, null, null, null, null, false));
    }

    private ApplicationContextRunner context(boolean mockPayment, boolean mockStorage) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=")
                .withUserConfiguration(ProductionSafetyConfiguration.class)
                .withBean(PaymentProperties.class, () -> payment(mockPayment))
                .withBean(SupabaseStorageProperties.class,
                        () -> new SupabaseStorageProperties(mockStorage, null, null, null, null, null));
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,true", "true,true"})
    void rejectsMockIntegrationsOutsideLocalProfiles(boolean mockPayment, boolean mockStorage) {
        context(mockPayment, mockStorage).run(context -> {
            var runner = context.getBean(ApplicationRunner.class);
            var exception = assertThrows(IllegalStateException.class,
                    () -> runner.run(new DefaultApplicationArguments()));
            assertEquals("Mock integrations require an explicit test, dev or local profile", exception.getMessage());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "staging"})
    void allowsRealIntegrationsOutsideLocalProfiles(String profile) {
        context(false, false).withPropertyValues("spring.profiles.active=" + profile).run(context ->
                assertDoesNotThrow(() -> context.getBean(ApplicationRunner.class).run(new DefaultApplicationArguments())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test", "dev", "local"})
    void permitsMockIntegrationsWithExplicitLocalProfiles(String profile) {
        context(true, true).withPropertyValues("spring.profiles.active=" + profile).run(context ->
                assertTrue(context.getBeansOfType(ApplicationRunner.class).isEmpty()));
    }
}
