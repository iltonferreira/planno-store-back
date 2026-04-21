package com.planno.store.order.dto;

import com.planno.store.order.domain.OrderStatusHistory;
import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderStatusHistoryResponse(
        UUID id,
        String previousStatus,
        String newStatus,
        OffsetDateTime changedAt,
        String changedBy,
        String reason,
        String source
) {
    public static OrderStatusHistoryResponse from(OrderStatusHistory history) {
        return new OrderStatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus() != null ? history.getPreviousStatus().name() : null,
                history.getNewStatus().name(),
                history.getChangedAt(),
                history.getChangedBy(),
                history.getReason(),
                history.getSource().name()
        );
    }
}

