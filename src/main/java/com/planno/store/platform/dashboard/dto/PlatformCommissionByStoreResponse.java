package com.planno.store.platform.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PlatformCommissionByStoreResponse(
        UUID storeId,
        String storeName,
        BigDecimal grossRevenue,
        BigDecimal platformCommissionTotal,
        BigDecimal sellerNetTotal,
        long paidOrdersCount
) {
}
