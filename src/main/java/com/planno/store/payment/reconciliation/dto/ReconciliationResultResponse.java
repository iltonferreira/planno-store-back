package com.planno.store.payment.reconciliation.dto;

import java.time.OffsetDateTime;

public record ReconciliationResultResponse(
        String targetType,
        String targetId,
        boolean changed,
        String result,
        OffsetDateTime executedAt
) {
    public static ReconciliationResultResponse of(String targetType, String targetId, boolean changed, String result) {
        return new ReconciliationResultResponse(targetType, targetId, changed, result, OffsetDateTime.now());
    }
}
