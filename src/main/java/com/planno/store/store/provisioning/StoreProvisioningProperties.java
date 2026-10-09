package com.planno.store.store.provisioning;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.store-provisioning")
public record StoreProvisioningProperties(String serviceToken, String domainSuffix) {
}
