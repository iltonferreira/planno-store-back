package com.planno.store.order.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OrderReservationProperties.class)
public class OrderReservationConfiguration {
    @Bean("orderReservationClock")
    Clock orderReservationClock() {
        return Clock.systemUTC();
    }
}
