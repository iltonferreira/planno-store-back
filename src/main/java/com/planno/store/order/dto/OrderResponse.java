package com.planno.store.order.dto;

import com.planno.store.order.domain.Order;
import com.planno.store.shipping.dto.AddressResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID storeId,
        UUID cartId,
        String code,
        String status,
        BigDecimal subtotalAmount,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal sellerNetAmount,
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
    public static OrderResponse from(Order order, com.planno.store.shipping.domain.Address address) {
        return new OrderResponse(
                order.getId(),
                order.getStore().getId(),
                order.getCartId(),
                order.getCode(),
                order.getStatus().name(),
                order.getSubtotalAmount(),
                order.getShippingAmount(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCommissionRate(),
                order.getCommissionAmount(),
                order.getSellerNetAmount(),
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
