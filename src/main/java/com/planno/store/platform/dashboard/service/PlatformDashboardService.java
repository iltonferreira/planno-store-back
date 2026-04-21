package com.planno.store.platform.dashboard.service;

import com.planno.store.core.time.DashboardDateRange;
import com.planno.store.core.time.DashboardDateRangeResolver;
import com.planno.store.core.time.DashboardPeriodPreset;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.repository.OrderRepository;
import com.planno.store.platform.dashboard.dto.PlatformCommissionByStoreResponse;
import com.planno.store.platform.dashboard.dto.PlatformDashboardSummaryResponse;
import com.planno.store.platform.dashboard.dto.PlatformSalesByDayResponse;
import com.planno.store.platform.dashboard.dto.PlatformTopStoreResponse;
import com.planno.store.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformDashboardService {

    private static final ZoneId PLATFORM_ZONE_ID = ZoneId.of("America/Sao_Paulo");

    private final DashboardDateRangeResolver dateRangeResolver;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;

    public PlatformDashboardService(
            DashboardDateRangeResolver dateRangeResolver,
            StoreRepository storeRepository,
            OrderRepository orderRepository
    ) {
        this.dateRangeResolver = dateRangeResolver;
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public PlatformDashboardSummaryResponse summary(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        List<Order> orders = ordersInRange(range);
        List<Order> paidOrders = paidOrders(orders);

        BigDecimal grossRevenue = sum(paidOrders.stream().map(Order::getTotalAmount).toList());
        BigDecimal platformCommissionTotal = sum(paidOrders.stream().map(Order::getCommissionAmount).toList());

        return new PlatformDashboardSummaryResponse(
                range.fromDate(),
                range.toDate(),
                grossRevenue,
                platformCommissionTotal,
                storeRepository.count(),
                paidOrders.size(),
                countByStatus(orders, OrderStatus.WAITING_PAYMENT),
                countByStatus(orders, OrderStatus.CANCELLED),
                averageTicket(grossRevenue, paidOrders.size()),
                topStores(from, to, preset),
                commissionByStore(from, to, preset),
                salesByDay(from, to, preset)
        );
    }

    @Transactional(readOnly = true)
    public List<PlatformCommissionByStoreResponse> commissionByStore(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        Map<UUID, StoreAccumulator> grouped = new LinkedHashMap<>();

        for (Order order : paidOrders(ordersInRange(range))) {
            grouped.computeIfAbsent(order.getStore().getId(), ignored -> new StoreAccumulator(
                    order.getStore().getId(),
                    order.getStore().getName()
            )).add(order);
        }

        return grouped.values().stream()
                .sorted((left, right) -> right.platformCommissionTotal.compareTo(left.platformCommissionTotal))
                .map(StoreAccumulator::toCommissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformSalesByDayResponse> salesByDay(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        Map<LocalDate, SalesAccumulator> grouped = new LinkedHashMap<>();

        for (Order order : paidOrders(ordersInRange(range)).stream()
                .sorted(java.util.Comparator.comparing(Order::getCreatedAt))
                .toList()) {
            LocalDate day = order.getCreatedAt().atZoneSameInstant(PLATFORM_ZONE_ID).toLocalDate();
            grouped.computeIfAbsent(day, ignored -> new SalesAccumulator()).add(order);
        }

        return grouped.entrySet().stream()
                .map(entry -> new PlatformSalesByDayResponse(
                        entry.getKey(),
                        scale(entry.getValue().grossRevenue),
                        scale(entry.getValue().platformCommissionTotal),
                        entry.getValue().paidOrdersCount
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformTopStoreResponse> topStores(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        DashboardDateRange range = resolveRange(from, to, preset);
        Map<UUID, StoreAccumulator> grouped = new LinkedHashMap<>();

        for (Order order : paidOrders(ordersInRange(range))) {
            grouped.computeIfAbsent(order.getStore().getId(), ignored -> new StoreAccumulator(
                    order.getStore().getId(),
                    order.getStore().getName()
            )).add(order);
        }

        return grouped.values().stream()
                .sorted((left, right) -> right.grossRevenue.compareTo(left.grossRevenue))
                .limit(5)
                .map(StoreAccumulator::toTopStoreResponse)
                .toList();
    }

    private DashboardDateRange resolveRange(LocalDate from, LocalDate to, DashboardPeriodPreset preset) {
        return dateRangeResolver.resolve(from, to, preset, PLATFORM_ZONE_ID);
    }

    private List<Order> ordersInRange(DashboardDateRange range) {
        return orderRepository.findAllByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
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

    private static BigDecimal averageTicket(BigDecimal grossRevenue, long paidOrdersCount) {
        if (paidOrdersCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return grossRevenue.divide(BigDecimal.valueOf(paidOrdersCount), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return scale(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class SalesAccumulator {
        private BigDecimal grossRevenue = BigDecimal.ZERO;
        private BigDecimal platformCommissionTotal = BigDecimal.ZERO;
        private long paidOrdersCount;

        private void add(Order order) {
            grossRevenue = grossRevenue.add(order.getTotalAmount());
            platformCommissionTotal = platformCommissionTotal.add(order.getCommissionAmount());
            paidOrdersCount++;
        }
    }

    private static final class StoreAccumulator {
        private final UUID storeId;
        private final String storeName;
        private BigDecimal grossRevenue = BigDecimal.ZERO;
        private BigDecimal platformCommissionTotal = BigDecimal.ZERO;
        private BigDecimal sellerNetTotal = BigDecimal.ZERO;
        private long paidOrdersCount;

        private StoreAccumulator(UUID storeId, String storeName) {
            this.storeId = storeId;
            this.storeName = storeName;
        }

        private void add(Order order) {
            grossRevenue = grossRevenue.add(order.getTotalAmount());
            platformCommissionTotal = platformCommissionTotal.add(order.getCommissionAmount());
            sellerNetTotal = sellerNetTotal.add(order.getSellerNetAmount());
            paidOrdersCount++;
        }

        private PlatformCommissionByStoreResponse toCommissionResponse() {
            return new PlatformCommissionByStoreResponse(
                    storeId,
                    storeName,
                    scale(grossRevenue),
                    scale(platformCommissionTotal),
                    scale(sellerNetTotal),
                    paidOrdersCount
            );
        }

        private PlatformTopStoreResponse toTopStoreResponse() {
            return new PlatformTopStoreResponse(
                    storeId,
                    storeName,
                    scale(grossRevenue),
                    scale(platformCommissionTotal),
                    scale(sellerNetTotal),
                    paidOrdersCount
            );
        }
    }
}
