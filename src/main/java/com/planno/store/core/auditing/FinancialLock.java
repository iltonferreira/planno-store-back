package com.planno.store.core.auditing;

import com.planno.store.order.domain.Order;
import com.planno.store.payment.domain.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FinancialLock {
    @PersistenceContext private EntityManager entities;

    @Transactional(propagation = Propagation.MANDATORY)
    public Order order(Order order) {
        Order managed = entities.find(Order.class, order.getId());
        // Acquire the row lock before refreshing. Follow-on locking can otherwise refresh
        // an old PostgreSQL MVCC snapshot, wait for a writer, and retain the obsolete state.
        entities.lock(managed, LockModeType.PESSIMISTIC_WRITE);
        entities.refresh(managed);
        return managed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Payment payment(Payment payment) {
        // Always lock the order before the payment, including refunds and webhook retries.
        order(payment.getOrder());
        Payment managed = entities.find(Payment.class, payment.getId());
        entities.lock(managed, LockModeType.PESSIMISTIC_WRITE);
        entities.refresh(managed);
        return managed;
    }
}
