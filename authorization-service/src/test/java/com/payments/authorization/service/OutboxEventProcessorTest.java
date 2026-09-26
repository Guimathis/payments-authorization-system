package com.payments.authorization.service;

import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.outbox.OutboxEventProcessor;
import com.payments.authorization.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventProcessor outboxEventProcessor;

    @Test
    @DisplayName("Deve marcar evento como SENT e preencher data de processamento limpando erros e retry")
    void shouldMarkEventAsSentSuccessfully() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .lastErrorMessage("Erro antigo")
                .nextRetryAt(Instant.now().plusSeconds(60))
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markAsSent(eventId);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getLastErrorMessage()).isNull();
        assertThat(event.getNextRetryAt()).isNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Deve marcar evento como POISON_PILL: status FAILED imediato, data de processamento e mensagem de erro")
    void shouldMarkAsPoisonPillSuccessfully() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markAsPoisonPill(eventId, "Malformed JSON syntax");

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getLastErrorMessage()).startsWith("POISON_PILL: Malformed JSON syntax");
        assertThat(event.getNextRetryAt()).isNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Deve incrementar retry_count, calcular backoff em next_retry_at e manter PENDING quando abaixo do limite")
    void shouldIncrementRetryCountAndRemainPendingWithBackoff() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(1)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markForRetry(eventId, 5, "Connection refused");

        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getLastErrorMessage()).isEqualTo("Connection refused");
        assertThat(event.getNextRetryAt()).isAfter(Instant.now());
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Deve definir status como FAILED quando retry_count atingir o limite máximo")
    void shouldMarkEventAsFailedWhenRetryCountReachesMaxRetries() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(4)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markForRetry(eventId, 5, "Timeout persistent");

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getNextRetryAt()).isNull();
        assertThat(event.getLastErrorMessage()).isEqualTo("Timeout persistent");
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Deve registrar falha transitória de infraestrutura sem incrementar retry_count e adiando next_retry_at")
    void shouldRecordTransientFailureWithoutIncrementingRetries() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(1)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.recordTransientFailure(eventId, "Kafka broker timeout");

        assertThat(event.getRetryCount()).isEqualTo(1); // Não incrementou!
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getLastErrorMessage()).isEqualTo("Kafka broker timeout");
        assertThat(event.getNextRetryAt()).isAfter(Instant.now());
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Não deve executar save caso o evento não seja localizado por ID")
    void shouldDoNothingWhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.empty());

        outboxEventProcessor.markAsSent(eventId);
        outboxEventProcessor.markAsPoisonPill(eventId, "Error");
        outboxEventProcessor.markForRetry(eventId, 5, "Error");
        outboxEventProcessor.recordTransientFailure(eventId, "Error");

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
