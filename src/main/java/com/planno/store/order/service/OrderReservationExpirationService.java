package com.planno.store.order.service;

import com.planno.store.core.auditing.FinancialLock;
import com.planno.store.inventory.service.InventoryService;
import com.planno.store.order.config.OrderReservationProperties;
import com.planno.store.order.domain.Order;
import com.planno.store.order.domain.OrderStatus;
import com.planno.store.order.domain.OrderStatusChangeSource;
import com.planno.store.order.domain.OrderStatusHistory;
import com.planno.store.order.repository.OrderRepository;
import com.planno.store.payment.domain.PaymentStatus;
import com.planno.store.payment.service.PaymentInitiationJournalService;
import com.planno.store.pricing.coupon.service.CouponService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderReservationExpirationService {
    private static final Set<PaymentStatus> TERMINAL_WITHOUT_PAYMENT = Set.of(
            PaymentStatus.REJECTED, PaymentStatus.CANCELLED, PaymentStatus.FAILED);

    private final OrderRepository orders;
    private final FinancialLock locks;
    private final InventoryService inventory;
    private final CouponService coupons;
    private final PaymentInitiationJournalService initiationJournal;
    private final Clock clock;
    private final OrderReservationProperties properties;

    public OrderReservationExpirationService(OrderRepository orders, FinancialLock locks,
            InventoryService inventory, CouponService coupons, PaymentInitiationJournalService initiationJournal,
            @Qualifier("orderReservationClock") Clock clock, OrderReservationProperties properties) {
        this.orders = orders;
        this.locks = locks;
        this.inventory = inventory;
        this.coupons = coupons;
        this.initiationJournal = initiationJournal;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<OrderRepository.ReservationCandidate> findCandidates(OffsetDateTime cutoff, UUID afterId) {
        return orders.findReservationExpirationCandidates(cutoff, afterId, TERMINAL_WITHOUT_PAYMENT,
                PageRequest.of(0, properties.batchSize()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(UUID storeId, UUID orderId) {
        var found = orders.findByIdAndStoreId(orderId, storeId);
        if (found.isEmpty()) {
            return false;
        }
        // The same order lock serializes checkout initiation, cancellation and payment webhooks.
        Order order = locks.order(found.get());
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (order.getStatus() != OrderStatus.WAITING_PAYMENT || order.getReservationExpiresAt() == null
                || order.getReservationExpiresAt().isAfter(now)
                || orders.countReservationProtectingPayments(orderId, storeId, TERMINAL_WITHOUT_PAYMENT) != 0
                || initiationJournal.hasUnresolvedInitiation(storeId, orderId)) {
            return false;
        }
        for (var item : order.getItems()) {
            inventory.releaseReservedQuantity(storeId, item.getProduct().getId(), item.getQuantity());
        }
        coupons.releaseUsage(storeId, order.getCouponId());
        order.updateStatus(OrderStatus.CANCELLED);
        order.addHistory(OrderStatusHistory.create(order, OrderStatus.WAITING_PAYMENT, OrderStatus.CANCELLED,
                "system:reservation-expiration", "Unpaid checkout reservation expired",
                OrderStatusChangeSource.RESERVATION_EXPIRATION));
        // Flush inside this transaction so optimistic conflicts also roll back stock, coupon and history.
        orders.flush();
        return true;
    }
}
