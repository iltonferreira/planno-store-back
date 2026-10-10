package com.planno.store.store.provisioning;

import java.util.UUID;

public record StoreProvisioningResponse(UUID operationId, UUID storeId, String status, String provisionalDomain) {
}
