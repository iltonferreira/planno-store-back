package com.planno.store.order.service;

import com.planno.store.order.config.OrderReservationProperties;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.order-reservation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderReservationExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(OrderReservationExpirationScheduler.class);
    private final OrderReservationExpirationService service;
    private final Clock clock;
    private final OrderReservationProperties properties;
    private volatile boolean stopping;

    public OrderReservationExpirationScheduler(OrderReservationExpirationService service,
            @Qualifier("orderReservationClock") Clock clock, OrderReservationProperties properties) {
        this.service = service;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.order-reservation.fixed-delay:PT1M}")
    public void expireReservations() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock);
        UUID afterId = null;
        int expired = 0;
        int failed = 0;
        while (!stopping && !Thread.currentThread().isInterrupted()) {
            var candidates = service.findCandidates(cutoff, afterId);
            if (candidates.isEmpty()) {
                break;
            }
            for (var candidate : candidates) {
                if (stopping || Thread.currentThread().isInterrupted()) {
                    return;
                }
                afterId = candidate.getId();
                try {
                    if (service.expire(candidate.getStoreId(), candidate.getId())) {
                        expired++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    // IDs are sufficient for follow-up; provider/customer payloads never belong in this log.
                    log.warn("Order reservation expiration failed storeId={} orderId={}",
                            candidate.getStoreId(), candidate.getId());
                }
            }
            if (candidates.size() < properties.batchSize()) {
                break;
            }
        }
        if (expired > 0 || failed > 0) {
            log.info("Order reservation expiration completed expired={} failed={}", expired, failed);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stop() {
        stopping = true;
    }
}
