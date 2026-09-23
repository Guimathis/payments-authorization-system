package com.payments.authorization.service;

import com.payments.authorization.outbox.OutboxPollingPublisher;
import com.payments.authorization.outbox.OutboxService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollingPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Mock
    private CircuitBreaker circuitBreaker;

    @InjectMocks
    private OutboxPollingPublisher publisher;

    @BeforeEach
    void setUp() {
        when(circuitBreakerRegistry.circuitBreaker("kafkaProducer")).thenReturn(circuitBreaker);
        ReflectionTestUtils.setField(publisher, "defaultBatchSize", 50);
    }

    @Test
    @DisplayName("Deve invocar publishPendingEvents com lote padrão quando o circuito estiver FECHADO")
    void shouldInvokePublishPendingEventsWhenCircuitClosed() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        when(outboxService.publishPendingEvents(50)).thenReturn(3);

        publisher.pollAndPublish();

        verify(outboxService).publishPendingEvents(50);
    }

    @Test
    @DisplayName("Não deve consultar o banco quando o circuito estiver ABERTO")
    void shouldSkipDatabasePollingWhenCircuitOpen() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        publisher.pollAndPublish();

        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("Deve consultar com lote de teste reduzido (1) quando o circuito estiver HALF_OPEN")
    void shouldProbeWithBatchSizeOneWhenCircuitHalfOpen() {
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.HALF_OPEN);
        when(outboxService.publishPendingEvents(1)).thenReturn(1);

        publisher.pollAndPublish();

        verify(outboxService).publishPendingEvents(1);
    }
}
