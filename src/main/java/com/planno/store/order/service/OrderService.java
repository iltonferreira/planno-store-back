package com.planno.store.order.service;

import com.planno.store.cart.domain.Cart;
import com.planno.store.cart.domain.CartItem;
import com.planno.store.cart.service.CartService;
import com.planno.store.catalog.domain.ProductStatus;
import com.planno.store.core.exception.BusinessException;
import com.planno.store.core.exception.NotFoundException;
import com.planno.store.core.security.CurrentActorProvider;
import com.planno.store.inventory.service.InventoryService;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.domain.OrderStatusChangeSource;
import com.planno.store.order.domain.OrderStatusHistory;
import com.planno.store.order.dto.CreateOrderRequest;
import com.planno.store.order.dto.StoreOrderResponse;
import com.planno.store.order.dto.UpdateOrderStatusRequest;
import com.planno.store.order.repository.OrderRepository;
import com.planno.store.payment.repository.PaymentRepository;
import com.planno.store.pricing.coupon.domain.Coupon;
import com.planno.store.pricing.coupon.service.CouponService;
import com.planno.store.pricing.dto.CartPricingSummary;
import com.planno.store.pricing.service.PricingService;
import com.planno.store.shipping.domain.Address;
import com.planno.store.shipping.domain.ShippingMethod;
import com.planno.store.shipping.service.ShippingService;
import com.planno.store.store.service.CurrentStoreProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ShippingService shippingService;
    private final PricingService pricingService;
    private final CouponService couponService;
    private final InventoryService inventoryService;
    private final CurrentStoreProvider currentStoreProvider;
    private final CurrentActorProvider currentActorProvider;
    private final OrderCodeGenerator orderCodeGenerator;
    private final PaymentRepository paymentRepository;

    public OrderService(
            OrderRepository orderRepository,
            CartService cartService,
            ShippingService shippingService,
            PricingService pricingService,
            CouponService couponService,
            InventoryService inventoryService,
            CurrentStoreProvider currentStoreProvider,
            CurrentActorProvider currentActorProvider,
            OrderCodeGenerator orderCodeGenerator,
            PaymentRepository paymentRepository
    ) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.shippingService = shippingService;
        this.pricingService = pricingService;
        this.couponService = couponService;
        this.inventoryService = inventoryService;
        this.currentStoreProvider = currentStoreProvider;
        this.currentActorProvider = currentActorProvider;
        this.orderCodeGenerator = orderCodeGenerator;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public StoreOrderResponse create(CreateOrderRequest request) {
        Cart cart = cartService.getCurrentActiveCart();
        if (!cart.getId().equals(request.cartId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Requested cart does not match the active cart context");
        }
        if (cart.getItems().isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Cannot create an order from an empty cart");
        }

        ShippingMethod shippingMethod = shippingService.getSelectedMethod(request.cartId(), request.shippingMethodId(), request.address());

        for (CartItem item : cart.getItems()) {
            if (item.getProduct().getStatus() != ProductStatus.ACTIVE) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "Inactive products cannot be ordered");
            }
            inventoryService.assertAvailableQuantity(item.getProduct().getId(), item.getQuantity());
        }

        BigDecimal shipping = scale(shippingMethod.getPrice());
        CartPricingSummary pricingSummary = pricingService.calculateCartPricing(cart, shipping);
        BigDecimal subtotal = pricingSummary.subtotalBeforeDiscount();
        BigDecimal discount = pricingSummary.discountAmount();
        BigDecimal total = pricingSummary.totalAmount();
        BigDecimal configuredCommissionRate = currentStoreProvider.getCurrentStore().getCommissionRate();
        BigDecimal commissionRate = scale(configuredCommissionRate);
        BigDecimal commissionAmount = scale(total.multiply(configuredCommissionRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        BigDecimal sellerNetAmount = scale(total.subtract(commissionAmount));
        Coupon consumedCoupon = couponService.consumeForOrder(cart.getCouponId(), pricingSummary.subtotalAfterItemPromotions());

        Order order = Order.create(
                currentStoreProvider.getCurrentStore(),
                cart.getId(),
                orderCodeGenerator.generate(),
                OrderStatus.WAITING_PAYMENT,
                subtotal,
                shipping,
                discount,
                consumedCoupon != null ? consumedCoupon.getId() : null,
                consumedCoupon != null ? consumedCoupon.getCode() : cart.getCouponCodeSnapshot(),
                total,
                commissionRate,
                commissionAmount,
                sellerNetAmount,
                request.customerName(),
                request.customerEmail(),
                request.customerDocument(),
                shippingMethod.getId(),
                shippingMethod.getName(),
                shippingMethod.getType().name(),
                shippingMethod.getEstimatedDays()
        );

        for (CartItem item : cart.getItems()) {
            order.addItem(OrderItem.create(
                    order,
                    item.getProduct(),
                    item.getProduct().getName(),
                    item.getProduct().getSlug(),
                    item.getProduct().getSku(),
                    item.getQuantity(),
                    item.getUnitFinalPriceSnapshot(),
                    item.getTotalFinalPriceSnapshot()
            ));
        }

        order.addHistory(OrderStatusHistory.create(
                order,
                null,
                OrderStatus.WAITING_PAYMENT,
                currentActorProvider.currentActor(),
                "Order created from checkout",
                OrderStatusChangeSource.CHECKOUT
        ));

        Order savedOrder = orderRepository.save(order);
        Address address = shippingService.saveOrderAddress(cart.getId(), request.address(), savedOrder);

        for (CartItem item : cart.getItems()) {
            inventoryService.reserveQuantity(item.getProduct().getId(), item.getQuantity());
        }

        cart.markCheckedOut();
        return StoreOrderResponse.from(savedOrder, address, null);
    }

    @Transactional(readOnly = true)
    public List<StoreOrderResponse> listAdmin() {
        return orderRepository.findAllByStoreIdOrderByCreatedAtDesc(currentStoreProvider.getCurrentStoreId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoreOrderResponse getAdmin(UUID orderId) {
        return toResponse(getOrderEntity(orderId));
    }

    @Transactional
    public StoreOrderResponse updateStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = getOrderEntity(orderId);
        OrderStatus previousStatus = order.getStatus();

        if (request.status() != OrderStatus.CANCELLED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only cancellation is allowed manually in this phase");
        }
        if (previousStatus != OrderStatus.CREATED && previousStatus != OrderStatus.WAITING_PAYMENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "This order status cannot be changed manually");
        }

        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservedQuantity(item.getProduct().getId(), item.getQuantity());
        }
        couponService.releaseUsage(order.getCouponId());

        order.updateStatus(OrderStatus.CANCELLED);
        order.addHistory(OrderStatusHistory.create(
                order,
                previousStatus,
                OrderStatus.CANCELLED,
                currentActorProvider.currentActor(),
                request.reason(),
                OrderStatusChangeSource.ADMIN
        ));
        return toResponse(order);
    }

    @Transactional
    public void markPaid(Order order, String changedBy, String reason) {
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }
        if (order.getStatus() != OrderStatus.WAITING_PAYMENT) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only waiting payment orders can be marked as paid");
        }

        OrderStatus previousStatus = order.getStatus();
        order.updateStatus(OrderStatus.PAID);
        order.addHistory(OrderStatusHistory.create(
                order,
                previousStatus,
                OrderStatus.PAID,
                changedBy,
                reason,
                OrderStatusChangeSource.PAYMENT_WEBHOOK
        ));
    }

    @Transactional
    public void markRefunded(Order order, String changedBy, String reason, OrderStatusChangeSource source) {
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return;
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Only paid orders can be marked as refunded");
        }

        OrderStatus previousStatus = order.getStatus();
        order.updateStatus(OrderStatus.REFUNDED);
        order.addHistory(OrderStatusHistory.create(
                order,
                previousStatus,
                OrderStatus.REFUNDED,
                changedBy,
                reason,
                source
        ));
    }

    @Transactional(readOnly = true)
    public Order getOrderEntity(UUID orderId) {
        return orderRepository.findByIdAndStoreId(orderId, currentStoreProvider.getCurrentStoreId())
                .orElseThrow(() -> new NotFoundException("Order not found"));
    }

    @Transactional(readOnly = true)
    public StoreOrderResponse getPublic(UUID orderId) {
        UUID activeCartId = cartService.getCurrentActiveCart().getId();
        Order order = orderRepository.findByIdAndStoreIdAndCartId(orderId, currentStoreProvider.getCurrentStoreId(), activeCartId)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        return toResponse(order);
    }

    private StoreOrderResponse toResponse(Order order) {
        Address address = order.getStore().getId() != null
                ? shippingService.findOrderAddress(order.getId())
                : null;
        String latestPaymentStatus = paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                .map(payment -> payment.getStatus().name())
                .orElse(null);
        return StoreOrderResponse.from(order, address, latestPaymentStatus);
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
