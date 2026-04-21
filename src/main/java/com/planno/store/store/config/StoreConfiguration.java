package com.planno.store.store.config;

import com.planno.store.core.storecontext.StoreContextProperties;
import com.planno.store.store.bootstrap.StoreBootstrapProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StoreBootstrapProperties.class, StoreContextProperties.class})
public class StoreConfiguration {
}
