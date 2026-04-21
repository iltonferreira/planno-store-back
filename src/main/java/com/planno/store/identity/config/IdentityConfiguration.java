package com.planno.store.identity.config;

import com.planno.store.identity.bootstrap.AdminBootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class IdentityConfiguration {
}

