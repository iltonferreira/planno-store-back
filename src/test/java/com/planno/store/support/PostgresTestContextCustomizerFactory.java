package com.planno.store.support;

import java.util.List;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/** Reuses the existing test profile and mocks with a database owned by this test JVM. */
public final class PostgresTestContextCustomizerFactory implements ContextCustomizerFactory {
    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
            List<ContextConfigurationAttributes> attributes) {
        return Boolean.getBoolean("planno.test.postgres") ? new PostgresCustomizer() : null;
    }

    private record PostgresCustomizer() implements ContextCustomizer {
        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration configuration) {
            if (!List.of(configuration.getActiveProfiles()).contains("test")) {
                throw new IllegalStateException("Disposable PostgreSQL requires the test profile");
            }
            if (!context.getEnvironment().getProperty("spring.datasource.jndi-name", "").isBlank()) {
                throw new IllegalStateException("External JNDI datasource is forbidden for disposable tests");
            }
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("owned-disposable-postgres", PostgresTestDatabase.properties()));
        }
    }
}
