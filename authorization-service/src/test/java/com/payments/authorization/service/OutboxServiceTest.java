package com.payments.authorization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.producer.PaymentEventProducer;
import com.payments.authorization.repository.OutboxEventRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxService, "batchSize", 50);
    }

    @Test
    @DisplayName("Deve publicar evento pendente, marcar como SENT e registrar data de processamento")
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
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("Deve incrementar retry_count e manter PENDING em caso de falha na publicação")
    void shouldIncrementRetryCountOnPublishFailure() throws Exception {
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
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("Deve retornar 0 quando não houver eventos pendentes")
    void shouldReturnZeroWhenNoPendingEvents() throws Exception {
        when(outboxEventRepository.findPendingForUpdate(50)).thenReturn(Collections.emptyList());

        int published = outboxService.publishPendingEvents();

        assertThat(published).isEqualTo(0);
        verify(paymentEventProducer, never()).sendPaymentAuthorizedEventSync(any());
    }
}
