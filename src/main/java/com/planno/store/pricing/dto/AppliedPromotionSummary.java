package com.planno.store.pricing.dto;

import com.planno.store.pricing.promotion.domain.PromotionType;
import java.util.UUID;

public record AppliedPromotionSummary(
        UUID promotionId,
        String promotionName,
        PromotionType promotionType
) {
}
