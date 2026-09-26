package com.payments.ledger.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsConfigTest {

    private final MetricsConfig config = new MetricsConfig();
    private final MeterFilter filter = config.ignoreActuatorMeterFilter();

    @Test
    @DisplayName("Deve negar métricas com tag uri iniciando com /actuator")
    void shouldDenyActuatorMetrics() {
        Meter.Id actuatorId = new Meter.Id(
                "http.server.requests",
                Tags.of("uri", "/actuator/prometheus"),
                null,
                null,
                Meter.Type.TIMER
        );

        assertThat(filter.accept(actuatorId)).isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    @DisplayName("Deve aceitar métricas de negócio")
    void shouldAcceptBusinessMetrics() {
        Meter.Id businessId = new Meter.Id(
                "http.server.requests",
                Tags.of("uri", "/api/v1/accounts/123/balance"),
                null,
                null,
                Meter.Type.TIMER
        );

        assertThat(filter.accept(businessId)).isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("Deve impedir registro de timer do actuator no MeterRegistry")
    void shouldNotRegisterActuatorTimerInRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(filter);

        registry.timer("http.server.requests", "uri", "/actuator/prometheus").record(() -> {});
        registry.timer("http.server.requests", "uri", "/api/v1/accounts/123/balance").record(() -> {});

        assertThat(registry.find("http.server.requests").tag("uri", "/actuator/prometheus").timer()).isNull();
        assertThat(registry.find("http.server.requests").tag("uri", "/api/v1/accounts/123/balance").timer()).isNotNull();
    }
}
