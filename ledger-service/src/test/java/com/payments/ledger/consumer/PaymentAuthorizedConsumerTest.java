package com.payments.ledger.consumer;

import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.service.LedgerService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorizedConsumerTest {

    @Mock
    private LedgerService ledgerService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OpenTelemetry openTelemetry;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Tracer tracer;

    @InjectMocks
    private PaymentAuthorizedConsumer consumer;

    @Test
    @DisplayName("Consumer deve repassar evento recebido do Kafka para o LedgerService")
    void shouldDelegateEventToLedgerService() {
        // Arrange
        UUID accountId = UUID.randomUUID();

        PaymentAuthorizedEvent paymentEvent = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        ConsumerRecord<String, PaymentAuthorizedEvent> record = new ConsumerRecord<>(
                "transacao-autorizada",
                0,
                0L,
                accountId.toString(),
                paymentEvent
        );

        // Act
        consumer.consume(record);

        // Assert
        verify(ledgerService).processPaymentDebit(paymentEvent);
    }
}