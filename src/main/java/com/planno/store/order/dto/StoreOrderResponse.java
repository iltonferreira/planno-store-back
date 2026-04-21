package com.planno.store.order.dto;

import com.planno.store.order.domain.Order;
import com.planno.store.shipping.dto.AddressResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record StoreOrderResponse(
        UUID id,
        UUID storeId,
        UUID cartId,
        String code,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String latestPaymentStatus,
        String customerName,
        String customerEmail,
        String customerDocument,
        UUID shippingMethodId,
        String shippingMethodName,
        String shippingMethodType,
        int shippingEstimatedDays,
        AddressResponse address,
        List<OrderItemResponse> items,
        List<OrderStatusHistoryResponse> statusHistory,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static StoreOrderResponse from(Order order, com.planno.store.shipping.domain.Address address, String latestPaymentStatus) {
        return new StoreOrderResponse(
                order.getId(),
                order.getStore().getId(),
                order.getCartId(),
                order.getCode(),
                order.getStatus().name(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                latestPaymentStatus,
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerDocument(),
                order.getShippingMethodId(),
                order.getShippingMethodName(),
                order.getShippingMethodType(),
                order.getShippingEstimatedDays(),
                AddressResponse.from(address),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.getStatusHistory().stream().map(OrderStatusHistoryResponse::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
