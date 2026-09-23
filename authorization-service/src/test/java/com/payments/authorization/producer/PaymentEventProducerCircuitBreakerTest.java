package com.payments.authorization.producer;

import com.payments.authorization.event.PaymentAuthorizedEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class PaymentEventProducerCircuitBreakerTest {

    @Autowired
    private PaymentEventProducer paymentEventProducer;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private KafkaTemplate<String, PaymentAuthorizedEvent> kafkaTemplate;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("kafkaProducer");
        circuitBreaker.reset();
    }

    @Test
    @DisplayName("Deve registrar sucesso e manter o circuito FECHADO ao publicar com sucesso")
    void shouldKeepCircuitClosedWhenPublishesSucceed() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        PaymentAuthorizedEvent event = createEvent();

        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> future =
                paymentEventProducer.sendPaymentAuthorizedEvent(event);

        assertThat(future).isCompleted();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isPositive();
    }

    @Test
    @DisplayName("Deve abrir o circuito (OPEN) ao atingir taxa de falha com erros de Timeout do Kafka e retornar CallNotPermittedException")
    void shouldOpenCircuitWhenFailureThresholdExceeded() {
        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> failedFuture1 = new CompletableFuture<>();
        failedFuture1.completeExceptionally(new TimeoutException("Kafka broker timeout 1"));

        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> failedFuture2 = new CompletableFuture<>();
        failedFuture2.completeExceptionally(new TimeoutException("Kafka broker timeout 2"));

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(failedFuture1)
                .thenReturn(failedFuture2);

        PaymentAuthorizedEvent event = createEvent();

        // 1ª chamada falha
        paymentEventProducer.sendPaymentAuthorizedEvent(event);
        // 2ª chamada falha (mínimo de chamadas configurado = 2, taxa de falha = 100% >= 50%)
        paymentEventProducer.sendPaymentAuthorizedEvent(event);

        // O circuito deve ter transitado para OPEN
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // A próxima chamada deve retornar future completada excepcionalmente com CallNotPermittedException
        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> blockedFuture =
                paymentEventProducer.sendPaymentAuthorizedEvent(event);

        assertThat(blockedFuture).isCompletedExceptionally();
        assertThatThrownBy(blockedFuture::join)
                .hasCauseInstanceOf(CallNotPermittedException.class);

        // KafkaTemplate só foi chamado 2 vezes (a 3ª foi bloqueada pelo Circuit Breaker!)
        verify(kafkaTemplate, times(2)).send(any(), any(), any());
    }

    private PaymentAuthorizedEvent createEvent() {
        return PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .paymentId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .eventType("PAYMENT_AUTHORIZED")
                .timestamp(Instant.now())
                .build();
    }
}
