package com.payments.authorization.service;

import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.exception.OutboxExceptionClassifier;
import com.payments.authorization.exception.OutboxExceptionClassifier.ErrorCategory;
import com.payments.authorization.outbox.OutboxEventProcessor;
import com.payments.authorization.outbox.OutboxEventPublisher;
import com.payments.authorization.outbox.OutboxService;
import com.payments.authorization.outbox.PublishResult;
import com.payments.authorization.repository.OutboxEventRepository;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private OutboxEventProcessor outboxEventProcessor;

    @Mock
    private OutboxExceptionClassifier exceptionClassifier;

    @InjectMocks
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxService, "defaultBatchSize", 50);
        ReflectionTestUtils.setField(outboxService, "maxRetries", 5);
    }

    @Test
    @DisplayName("Deve publicar evento pendente e marcar como SENT via processador de eventos")
    void shouldPublishPendingEventsAndMarkAsSentOnSuccess() {
        OutboxEvent event = createTestEvent("{\"paymentId\":\"123\"}");

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        when(outboxEventPublisher.publishBatchPipelined(List.of(event)))
                .thenReturn(List.of(new PublishResult(event, null)));

        int published = outboxService.publishPendingEvents(50);

        assertThat(published).isEqualTo(1);
        verify(outboxEventProcessor).markAsSent(event.getId());
        verify(outboxEventProcessor, never()).markAsPoisonPill(any(), any());
        verify(outboxEventProcessor, never()).markForRetry(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Deve tratar POISON_PILL: marcar imediatamente como FAILED sem retry")
    void shouldMarkAsPoisonPillWhenPoisonPillOccurs() {
        OutboxEvent event = createTestEvent("corrupted payload");
        SerializationException exception = new SerializationException("Corrupted payload bytes");

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        when(outboxEventPublisher.publishBatchPipelined(List.of(event)))
                .thenReturn(List.of(new PublishResult(event, exception)));
        when(exceptionClassifier.classify(exception)).thenReturn(ErrorCategory.FATAL_POISON_PILL);

        int published = outboxService.publishPendingEvents(50);

        assertThat(published).isEqualTo(0);
        verify(outboxEventProcessor).markAsPoisonPill(event.getId(), "Corrupted payload bytes");
        verify(outboxEventProcessor, never()).markForRetry(any(), anyInt(), any());
    }

    @Test
    @DisplayName("Deve interromper o lote imediatamente e registrar falha transitória quando o broker Kafka estiver indisponível")
    void shouldStopBatchAndRecordTransientFailureWhenInfrastructureFails() {
        OutboxEvent event1 = createTestEvent("{\"paymentId\":\"1\"}");
        OutboxEvent event2 = createTestEvent("{\"paymentId\":\"2\"}");
        TimeoutException exception = new TimeoutException("Kafka broker unreachable");

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event1, event2));
        when(outboxEventPublisher.publishBatchPipelined(List.of(event1, event2)))
                .thenReturn(List.of(
                        new PublishResult(event1, exception),
                        new PublishResult(event2, null)
                ));
        when(exceptionClassifier.classify(exception)).thenReturn(ErrorCategory.TRANSIENT_INFRASTRUCTURE);

        int published = outboxService.publishPendingEvents(50);

        assertThat(published).isEqualTo(0);
        verify(outboxEventProcessor).recordTransientFailure(event1.getId(), "Kafka broker unreachable");
        // Fast-break: o segundo evento nem chega a ser marcado como SENT
        verify(outboxEventProcessor, never()).markAsSent(event2.getId());
    }

    @Test
    @DisplayName("Deve agendar nova tentativa (retry) com backoff quando erro for categorizado como UNKNOWN")
    void shouldMarkForRetryWhenUnknownErrorOccurs() {
        OutboxEvent event = createTestEvent("{\"paymentId\":\"3\"}");
        RuntimeException exception = new RuntimeException("Unexpected error during processing");

        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(List.of(event));
        when(outboxEventPublisher.publishBatchPipelined(List.of(event)))
                .thenReturn(List.of(new PublishResult(event, exception)));
        when(exceptionClassifier.classify(exception)).thenReturn(ErrorCategory.UNKNOWN);

        int published = outboxService.publishPendingEvents(50);

        assertThat(published).isEqualTo(0);
        verify(outboxEventProcessor).markForRetry(event.getId(), 5, "Unexpected error during processing");
        verify(outboxEventProcessor, never()).markAsSent(any());
    }

    @Test
    @DisplayName("Deve retornar 0 sem interagir com publisher quando não houver eventos pendentes")
    void shouldReturnZeroWhenNoEventsFound() {
        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(Collections.emptyList());

        int published = outboxService.publishPendingEvents(50);

        assertThat(published).isEqualTo(0);
        verifyNoInteractions(outboxEventPublisher);
        verifyNoInteractions(outboxEventProcessor);
    }

    @Test
    @DisplayName("Deve usar o tamanho padrão de lote ao invocar publishPendingEvents sem parâmetros")
    void shouldDefaultToConfiguredBatchSizeWhenCalledWithoutArguments() {
        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(Collections.emptyList());

        outboxService.publishPendingEvents();

        verify(outboxEventRepository).findPendingForUpdate(50);
    }

    private OutboxEvent createTestEvent(String payload) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TRANSACTION")
                .aggregateId(UUID.randomUUID().toString())
                .type("PAYMENT_AUTHORIZED")
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }
}
