package com.payments.authorization.service;

import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
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
    @DisplayName("Deve marcar evento como SENT e preencher data de processamento")
    void shouldMarkEventAsSentSuccessfully() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markAsSent(eventId);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(event.getProcessedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Deve incrementar retry_count e manter PENDING quando abaixo do limite máximo")
    void shouldIncrementRetryCountAndRemainPendingWhenBelowMaxRetries() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(1)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markAsFailedOrRetry(eventId, 5);

        assertThat(event.getRetryCount()).isEqualTo(2);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
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

        outboxEventProcessor.markAsFailedOrRetry(eventId, 5);

        assertThat(event.getRetryCount()).isEqualTo(5);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Deve tratar retry_count nulo iniciando a contagem em 1")
    void shouldHandleNullRetryCountGracefully() {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(eventId)
                .status(OutboxStatus.PENDING)
                .retryCount(null)
                .build();

        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));

        outboxEventProcessor.markAsFailedOrRetry(eventId, 3);

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Não deve executar save caso o evento não seja localizado por ID")
    void shouldDoNothingWhenEventNotFound() {
        UUID eventId = UUID.randomUUID();
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.empty());

        outboxEventProcessor.markAsSent(eventId);
        outboxEventProcessor.markAsFailedOrRetry(eventId, 5);

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
