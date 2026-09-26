package com.payments.authorization.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.producer.PaymentEventProducer;
import com.payments.authorization.service.OpenTelemetryService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.context.Context;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private OpenTelemetryService openTelemetryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks
    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "sendTimeoutMs", 1000L);
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando a lista de eventos de entrada for vazia ou nula")
    void shouldReturnEmptyListWhenInputIsEmptyOrNull() {
        assertThat(publisher.publishBatchPipelined(null)).isEmpty();
        assertThat(publisher.publishBatchPipelined(Collections.emptyList())).isEmpty();
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    @DisplayName("Deve publicar eventos em paralelo e coletar os resultados de sucesso")
    void shouldPublishBatchPipelinedSuccessfully() {
        OutboxEvent event1 = createEvent();
        OutboxEvent event2 = createEvent();

        when(paymentEventProducer.sendPaymentAuthorizedEvent(any(PaymentAuthorizedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        List<PublishResult> results = publisher.publishBatchPipelined(List.of(event1, event2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).isSuccess()).isTrue();
        assertThat(results.get(1).isSuccess()).isTrue();
        verify(paymentEventProducer, times(2)).sendPaymentAuthorizedEvent(any());
    }

    @Test
    @DisplayName("Deve capturar CallNotPermittedException quando o Circuit Breaker estiver ABERTO")
    void shouldCatchCallNotPermittedExceptionWhenCircuitBreakerIsOpen() {
        OutboxEvent event = createEvent();
        CircuitBreaker cb = CircuitBreaker.ofDefaults("kafkaProducer");
        CallNotPermittedException exception = CallNotPermittedException.createCallNotPermittedException(cb);

        when(paymentEventProducer.sendPaymentAuthorizedEvent(any(PaymentAuthorizedEvent.class)))
                .thenThrow(exception);

        List<PublishResult> results = publisher.publishBatchPipelined(List.of(event));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).error()).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("Deve extrair e propagar trace context do OpenTelemetry quando presente")
    void shouldExtractAndPropagateTraceContextWhenPresent() {
        OutboxEvent event = createEvent();
        event.setTraceContext("{\"traceparent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\"}");

        when(openTelemetryService.extractTraceContext(any())).thenReturn(Context.root());
        when(paymentEventProducer.sendPaymentAuthorizedEvent(any(PaymentAuthorizedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        List<PublishResult> results = publisher.publishBatchPipelined(List.of(event));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isTrue();
        verify(openTelemetryService).extractTraceContext(any());
    }

    @Test
    @DisplayName("Deve mapear TimeoutException se a future não concluir dentro do tempo limite")
    void shouldMapTimeoutExceptionWhenFutureDoesNotCompleteInTime() {
        OutboxEvent event = createEvent();
        CompletableFuture<SendResult<String, Object>> uncompletedFuture = new CompletableFuture<>();

        when(paymentEventProducer.sendPaymentAuthorizedEvent(any(PaymentAuthorizedEvent.class)))
                .thenReturn(uncompletedFuture);

        ReflectionTestUtils.setField(publisher, "sendTimeoutMs", 50L);

        List<PublishResult> results = publisher.publishBatchPipelined(List.of(event));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).error()).isInstanceOf(TimeoutException.class);
    }

    private OutboxEvent createEvent() {
        PaymentAuthorizedEvent payloadEvent = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .eventType("PAYMENT_AUTHORIZED")
                .timestamp(Instant.now())
                .build();

        String json;
        try {
            json = objectMapper.writeValueAsString(payloadEvent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TRANSACTION")
                .aggregateId(payloadEvent.getPaymentId().toString())
                .type("PAYMENT_AUTHORIZED")
                .payload(json)
                .build();
    }
}
