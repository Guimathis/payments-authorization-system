package com.payments.authorization.metrics;

import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMetricsTest {

    private MeterRegistry meterRegistry;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private PaymentMetrics paymentMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentMetrics = new PaymentMetrics(meterRegistry, outboxEventRepository);
        paymentMetrics.registerGauges();
    }

    @Test
    @DisplayName("Deve incrementar contador payments.authorized.count com tags status e payment_method")
    void shouldIncrementPaymentAuthorizedCounter() {
        paymentMetrics.incrementAuthorizationCount("APPROVED", "CREDIT_CARD");
        paymentMetrics.incrementAuthorizationCount("APPROVED", "CREDIT_CARD");
        paymentMetrics.incrementAuthorizationCount("REJECTED", "PIX");

        double approvedCreditCount = meterRegistry.get("payments.authorized.count")
                .tag("status", "APPROVED")
                .tag("payment_method", "CREDIT_CARD")
                .counter()
                .count();

        double rejectedPixCount = meterRegistry.get("payments.authorized.count")
                .tag("status", "REJECTED")
                .tag("payment_method", "PIX")
                .counter()
                .count();

        assertThat(approvedCreditCount).isEqualTo(2.0);
        assertThat(rejectedPixCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Deve registrar latência no timer antifraud.evaluation.duration")
    void shouldRecordAntifraudEvaluationDuration() {
        String result = paymentMetrics.recordAntifraudEvaluation(() -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {}
            return "SUCCESS";
        });

        assertThat(result).isEqualTo("SUCCESS");
        double timerCount = meterRegistry.get("antifraud.evaluation.duration").timer().count();
        assertThat(timerCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Deve refletir valor correto no gauge outbox.pending.gauge")
    void shouldReflectOutboxPendingGauge() {
        when(outboxEventRepository.countByStatus(OutboxStatus.PENDING)).thenReturn(5L);

        double pendingGauge = meterRegistry.get("outbox.pending.gauge").gauge().value();
        assertThat(pendingGauge).isEqualTo(5.0);
    }
}
