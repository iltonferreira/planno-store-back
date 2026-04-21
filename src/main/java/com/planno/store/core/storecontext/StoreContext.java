package com.planno.store.core.storecontext;

import java.util.UUID;

public record StoreContext(
        UUID storeId,
        String storeSlug,
        StoreContextSource source
) {
}

