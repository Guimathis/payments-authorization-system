package com.payments.authorization.metrics;

import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxEventRepository;

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("outbox.pending.gauge", outboxEventRepository, repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Número de eventos pendentes na tabela outbox_events")
                .register(meterRegistry);
        log.info("Métrica 'outbox.pending.gauge' registrada com sucesso no MeterRegistry");
    }

    public void incrementAuthorizationCount(String status, String paymentMethod) {
        meterRegistry.counter(
                "payments.authorized.count",
                "status", status != null ? status : "UNKNOWN",
                "payment_method", paymentMethod != null ? paymentMethod : "UNKNOWN"
        ).increment();
    }

    public <T> T recordAntifraudEvaluation(Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return supplier.get();
        } finally {
            sample.stop(meterRegistry.timer("antifraud.evaluation.duration"));
        }
    }

    public void recordAntifraudEvaluation(Runnable runnable) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            runnable.run();
        } finally {
            sample.stop(meterRegistry.timer("antifraud.evaluation.duration"));
        }
    }
}
