package com.payments.notification.metrics;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter ignoreActuatorMetrics() {
        return MeterFilter.deny(id -> {
            String uri = id.getTag("uri");
            return uri != null && uri.startsWith("/actuator");
        });
    }
}