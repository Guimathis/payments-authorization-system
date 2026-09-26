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
    public void registerMetrics() {
        Gauge.builder("outbox.pending.gauge", outboxEventRepository, repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("Quantidade de eventos pendentes na tabela Outbox")
                .register(meterRegistry);

        Gauge.builder("outbox.failed.events", outboxEventRepository, repo -> repo.countByStatus(OutboxStatus.FAILED))
                .description("Quantidade de eventos com falha na tabela Outbox")
                .register(meterRegistry);

        Gauge.builder("outbox.sent.events", outboxEventRepository, repo -> repo.countByStatus(OutboxStatus.SENT))
                .description("Quantidade de eventos enviados na tabela Outbox")
                .register(meterRegistry);
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
}
