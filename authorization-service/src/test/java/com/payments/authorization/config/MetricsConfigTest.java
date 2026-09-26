package com.payments.authorization.config;

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
        Meter.Id actuatorPrometheusId = new Meter.Id(
                "http.server.requests",
                Tags.of("uri", "/actuator/prometheus", "status", "200"),
                null,
                null,
                Meter.Type.TIMER
        );

        Meter.Id actuatorHealthId = new Meter.Id(
                "http.server.requests",
                Tags.of("uri", "/actuator/health", "status", "200"),
                null,
                null,
                Meter.Type.TIMER
        );

        assertThat(filter.accept(actuatorPrometheusId)).isEqualTo(MeterFilterReply.DENY);
        assertThat(filter.accept(actuatorHealthId)).isEqualTo(MeterFilterReply.DENY);
    }

    @Test
    @DisplayName("Deve permitir métricas com tag uri que não iniciam com /actuator")
    void shouldAcceptNonActuatorMetrics() {
        Meter.Id businessApiId = new Meter.Id(
                "http.server.requests",
                Tags.of("uri", "/api/v1/payments", "status", "200"),
                null,
                null,
                Meter.Type.TIMER
        );

        assertThat(filter.accept(businessApiId)).isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("Deve permitir métricas que não possuem a tag uri")
    void shouldAcceptMetricsWithoutUriTag() {
        Meter.Id customMetricId = new Meter.Id(
                "payments.authorized.count",
                Tags.of("status", "APPROVED"),
                null,
                null,
                Meter.Type.COUNTER
        );

        assertThat(filter.accept(customMetricId)).isEqualTo(MeterFilterReply.NEUTRAL);
    }

    @Test
    @DisplayName("Deve impedir registro de timer do actuator no MeterRegistry")
    void shouldNotRegisterActuatorTimerInRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(filter);

        registry.timer("http.server.requests", "uri", "/actuator/prometheus").record(() -> {});
        registry.timer("http.server.requests", "uri", "/api/v1/payments").record(() -> {});

        assertThat(registry.find("http.server.requests").tag("uri", "/actuator/prometheus").timer()).isNull();
        assertThat(registry.find("http.server.requests").tag("uri", "/api/v1/payments").timer()).isNotNull();
    }
}
