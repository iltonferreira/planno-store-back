package com.planno.store;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class PlannoStoreBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlannoStoreBackApplication.class, args);
    }
}
