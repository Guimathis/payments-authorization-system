package com.payments.authorization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.producer.PaymentEventProducer;
import com.payments.authorization.repository.OutboxEventRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventProcessor outboxEventProcessor;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Mock
    private OpenTelemetryService openTelemetryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxService, "batchSize", 50);
        ReflectionTestUtils.setField(outboxService, "maxRetries", 5);
    }

    @Test
    @DisplayName("Deve publicar evento pendente, marcar como SENT e registrar data de processamento via processador")
    void shouldPublishPendingEventAndMarkAsSent() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentAuthorizedEvent payloadEvent = PaymentAuthorizedEvent.builder()
                .eventId(eventId)
                .paymentId(paymentId)
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .build();

        String payloadJson = objectMapper.writeValueAsString(payloadEvent);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TRANSACTION")
                .aggregateId(paymentId.toString())
                .type("PAYMENT_AUTHORIZED")
                .payload(payloadJson)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(outboxEvent));

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(outboxEvent.getProcessedAt()).isNotNull();
        verify(paymentEventProducer).sendPaymentAuthorizedEventSync(any(PaymentAuthorizedEvent.class));
        verify(outboxEventProcessor).markAsSent(outboxEvent.getId());
    }

    @Test
    @DisplayName("Deve delegar incremento de tentativa ao processador em caso de falha na publicação")
    void shouldDelegateFailureToProcessorWhenPublishFails() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentAuthorizedEvent payloadEvent = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(paymentId)
                .amount(new BigDecimal("50.00"))
                .currency("BRL")
                .build();

        String payloadJson = objectMapper.writeValueAsString(payloadEvent);

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TRANSACTION")
                .aggregateId(paymentId.toString())
                .type("PAYMENT_AUTHORIZED")
                .payload(payloadJson)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(outboxEvent));
        doThrow(new TimeoutException("Kafka broker unreachable"))
                .when(paymentEventProducer).sendPaymentAuthorizedEventSync(any(PaymentAuthorizedEvent.class));

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(0);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        verify(outboxEventProcessor).markAsFailedOrRetry(outboxEvent.getId(), 5);
        verify(outboxEventProcessor, never()).markAsSent(any());
    }

    @Test
    @DisplayName("Deve interromper o lote imediatamente em falha de conexão do broker sem processar os próximos")
    void shouldStopBatchWhenBrokerCommunicationErrorOccurs() throws Exception {
        UUID paymentId1 = UUID.randomUUID();
        UUID paymentId2 = UUID.randomUUID();

        OutboxEvent event1 = createOutboxEvent(paymentId1, "10.00");
        OutboxEvent event2 = createOutboxEvent(paymentId2, "20.00");

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event1, event2));
        doThrow(new TimeoutException("Kafka timeout"))
                .when(paymentEventProducer).sendPaymentAuthorizedEventSync(any(PaymentAuthorizedEvent.class));

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(0);
        // Apenas o primeiro evento foi tentado; o segundo nem foi chamado por conta do fast-break
        verify(paymentEventProducer, times(1)).sendPaymentAuthorizedEventSync(any(PaymentAuthorizedEvent.class));
        verify(outboxEventProcessor).markAsFailedOrRetry(event1.getId(), 5);
        verify(outboxEventProcessor, never()).markAsFailedOrRetry(eq(event2.getId()), any(Integer.class));
    }

    @Test
    @DisplayName("Deve preservar o primeiro evento comitado caso o segundo evento sofra falha")
    void shouldSaveProgressAndNotRollbackFirstEventWhenSecondEventFails() throws Exception {
        UUID paymentId1 = UUID.randomUUID();
        UUID paymentId2 = UUID.randomUUID();

        OutboxEvent event1 = createOutboxEvent(paymentId1, "100.00");
        OutboxEvent event2 = createOutboxEvent(paymentId2, "200.00");

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event1, event2));

        // Evento 1 publica com sucesso; evento 2 falha com erro não relacionado à queda do broker
        PaymentAuthorizedEvent payload1 = objectMapper.readValue(event1.getPayload(), PaymentAuthorizedEvent.class);
        PaymentAuthorizedEvent payload2 = objectMapper.readValue(event2.getPayload(), PaymentAuthorizedEvent.class);

        doAnswer(invocation -> null)
                .when(paymentEventProducer).sendPaymentAuthorizedEventSync(argThatMatchingPayment(payload1.getPaymentId()));
        doThrow(new RuntimeException("Serialization payload issue"))
                .when(paymentEventProducer).sendPaymentAuthorizedEventSync(argThatMatchingPayment(payload2.getPaymentId()));

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        // Evento 1 foi confirmado de forma isolada
        verify(outboxEventProcessor).markAsSent(event1.getId());
        assertThat(event1.getStatus()).isEqualTo(OutboxStatus.SENT);

        // Evento 2 foi tratado sem anular o evento 1
        verify(outboxEventProcessor).markAsFailedOrRetry(event2.getId(), 5);
        assertThat(event2.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deve extrair e propagar contexto do OpenTelemetry quando presente")
    void shouldExtractTraceContextWhenPresent() throws Exception {
        UUID paymentId = UUID.randomUUID();
        String traceContextJson = "{\"traceparent\":\"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01\"}";

        OutboxEvent event = createOutboxEvent(paymentId, "75.00");
        event.setTraceContext(traceContextJson);

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        when(openTelemetryService.extractTraceContext(any())).thenReturn(Context.root());

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        verify(openTelemetryService).extractTraceContext(any());
        verify(outboxEventProcessor).markAsSent(event.getId());
    }

    @Test
    @DisplayName("Deve tratar trace_context nulo ou em branco com segurança")
    void shouldHandleNullTraceContextWithoutErrors() throws Exception {
        UUID paymentId = UUID.randomUUID();
        OutboxEvent event = createOutboxEvent(paymentId, "30.00");
        event.setTraceContext(null);

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event));

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(1);
        verify(openTelemetryService, never()).extractTraceContext(any());
        verify(outboxEventProcessor).markAsSent(event.getId());
    }

    @Test
    @DisplayName("Deve retornar 0 quando não houver eventos pendentes")
    void shouldReturnZeroWhenNoPendingEvents() throws Exception {
        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(Collections.emptyList());

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(0);
        verify(paymentEventProducer, never()).sendPaymentAuthorizedEventSync(any());
        verify(outboxEventProcessor, never()).markAsSent(any());
    }

    private OutboxEvent createOutboxEvent(UUID paymentId, String amount) throws Exception {
        PaymentAuthorizedEvent payloadEvent = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(paymentId)
                .amount(new BigDecimal(amount))
                .currency("BRL")
                .build();

        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TRANSACTION")
                .aggregateId(paymentId.toString())
                .type("PAYMENT_AUTHORIZED")
                .payload(objectMapper.writeValueAsString(payloadEvent))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }

    private PaymentAuthorizedEvent argThatMatchingPayment(UUID paymentId) {
        return org.mockito.ArgumentMatchers.argThat(argument ->
                argument != null && paymentId.equals(argument.getPaymentId())
        );
    }
}
