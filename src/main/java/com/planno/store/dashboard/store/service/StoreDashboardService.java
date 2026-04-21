package com.planno.store.dashboard.store.service;

import com.planno.store.core.time.DashboardDateRange;
import com.planno.store.core.time.DashboardDateRangeResolver;
import com.planno.store.core.time.DashboardPeriodPreset;
import com.planno.store.dashboard.store.dto.StoreDashboardSummaryResponse;
import com.planno.store.dashboard.store.dto.StoreRecentOrderResponse;
import com.planno.store.dashboard.store.dto.StoreSalesByDayResponse;
import com.planno.store.dashboard.store.dto.StoreTopProductResponse;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderItem;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.repository.OrderRepository;
import com.planno.store.payment.repository.PaymentRepository;
import com.planno.store.store.domain.Store;
import com.planno.store.store.service.CurrentStoreProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreDashboardService {

    private final CurrentStoreProvider currentStoreProvider;
    private final DashboardDateRangeResolver dateRangeResolver;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public StoreDashboardService(
            CurrentStoreProvider currentStoreProvider,
            DashboardDateRangeResolver dateRangeResolver,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository
    ) {
        this.currentStoreProvider = currentStoreProvider;
        this.dateRangeResolver = dateRangeResolver;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional(readOnly = true)
    public StoreDashboardSummaryResponse summary(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        UUID storeId = currentStoreProvider.getCurrentStoreId();
        List<Order> orders = ordersInRange(storeId, range);
        List<Order> paidOrders = paidOrders(orders);

        BigDecimal totalRevenue = sumOrders(paidOrders);
        long paidOrdersCount = paidOrders.size();
        long waitingPaymentOrdersCount = countByStatus(orders, OrderStatus.WAITING_PAYMENT);
        long cancelledOrdersCount = countByStatus(orders, OrderStatus.CANCELLED);
        long ordersCount = orders.size();
        long itemsSoldCount = paidOrders.stream()
                .flatMap(order -> order.getItems().stream())
                .mapToLong(OrderItem::getQuantity)
                .sum();

        return new StoreDashboardSummaryResponse(
                range.fromDate(),
                range.toDate(),
                totalRevenue,
                paidOrdersCount,
                waitingPaymentOrdersCount,
                cancelledOrdersCount,
                averageTicket(totalRevenue, paidOrdersCount),
                ordersCount,
                itemsSoldCount,
                recentOrders(from, to, preset),
                topProducts(from, to, preset),
                salesByDay(from, to, preset)
        );
    }

    @Transactional(readOnly = true)
    public List<StoreSalesByDayResponse> salesByDay(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        List<Order> paidOrders = paidOrders(ordersInRange(currentStoreProvider.getCurrentStoreId(), range));

        Map<LocalDate, SalesAccumulator> grouped = new LinkedHashMap<>();
        for (Order order : paidOrders.stream().sorted(Comparator.comparing(Order::getCreatedAt)).toList()) {
            LocalDate day = order.getCreatedAt().atZoneSameInstant(ZoneId.of(currentStoreProvider.getCurrentStore().getTimezone())).toLocalDate();
            grouped.computeIfAbsent(day, ignored -> new SalesAccumulator())
                    .add(order.getTotalAmount());
        }

        return grouped.entrySet().stream()
                .map(entry -> new StoreSalesByDayResponse(
                        entry.getKey(),
                        scale(entry.getValue().totalRevenue),
                        entry.getValue().paidOrdersCount
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreTopProductResponse> topProducts(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        Map<UUID, ProductAccumulator> grouped = new LinkedHashMap<>();

        for (Order order : paidOrders(ordersInRange(currentStoreProvider.getCurrentStoreId(), range))) {
            for (OrderItem item : order.getItems()) {
                grouped.computeIfAbsent(item.getProduct().getId(), ignored -> new ProductAccumulator(
                        item.getProduct().getId(),
                        item.getProductNameSnapshot(),
                        item.getProductSlugSnapshot(),
                        item.getSkuSnapshot()
                )).add(item);
            }
        }

        return grouped.values().stream()
                .sorted(Comparator
                        .comparingLong(ProductAccumulator::quantitySold).reversed()
                        .thenComparing(ProductAccumulator::grossRevenue, Comparator.reverseOrder()))
                .limit(5)
                .map(ProductAccumulator::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreRecentOrderResponse> recentOrders(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        return ordersInRange(currentStoreProvider.getCurrentStoreId(), range).stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .limit(5)
                .map(order -> new StoreRecentOrderResponse(
                        order.getId(),
                        order.getCode(),
                        order.getStatus().name(),
                        order.getSubtotalAmount(),
                        order.getShippingAmount(),
                        order.getTotalAmount(),
                        order.getCustomerName(),
                        order.getCustomerEmail(),
                        paymentRepository.findTopByOrderIdOrderByCreatedAtDesc(order.getId())
                                .map(payment -> payment.getStatus().name())
                                .orElse(null),
                        order.getCreatedAt()
                ))
                .toList();
    }

    private DashboardDateRange resolveRange(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        Store store = currentStoreProvider.getCurrentStore();
        ZoneId zoneId = ZoneId.of(store.getTimezone());
        return dateRangeResolver.resolve(from, to, preset, zoneId);
    }

    private List<Order> ordersInRange(UUID storeId, DashboardDateRange range) {
        return orderRepository.findAllByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                storeId,
                range.fromDateTime(),
                range.toExclusiveDateTime()
        );
    }

    private static List<Order> paidOrders(List<Order> orders) {
        return orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.PAID)
                .toList();
    }

    private static long countByStatus(List<Order> orders, OrderStatus status) {
        return orders.stream().filter(order -> order.getStatus() == status).count();
    }

    private static BigDecimal sumOrders(List<Order> orders) {
        return scale(orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal averageTicket(BigDecimal totalRevenue, long paidOrdersCount) {
        if (paidOrdersCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return totalRevenue.divide(BigDecimal.valueOf(paidOrdersCount), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class SalesAccumulator {
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private long paidOrdersCount;

        private void add(BigDecimal amount) {
            totalRevenue = totalRevenue.add(amount);
            paidOrdersCount++;
        }
    }

    private static final class ProductAccumulator {
        private final UUID productId;
        private final String productName;
        private final String productSlug;
        private final String sku;
        private long quantitySold;
        private BigDecimal grossRevenue = BigDecimal.ZERO;

        private ProductAccumulator(UUID productId, String productName, String productSlug, String sku) {
            this.productId = productId;
            this.productName = productName;
            this.productSlug = productSlug;
            this.sku = sku;
        }

        private void add(OrderItem item) {
            quantitySold += item.getQuantity();
            grossRevenue = grossRevenue.add(item.getTotalPriceSnapshot());
        }

        private long quantitySold() {
            return quantitySold;
        }

        private BigDecimal grossRevenue() {
            return grossRevenue;
        }

        private StoreTopProductResponse toResponse() {
            return new StoreTopProductResponse(
                    productId,
                    productName,
                    productSlug,
                    sku,
                    quantitySold,
                    scale(grossRevenue)
            );
        }
    }
}
