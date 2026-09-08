package com.payments.ledger.config;

import com.payments.ledger.event.PaymentAuthorizedEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaDlqErrorHandlerTest {

    @Mock
    private KafkaTemplate<String, Object> dlqKafkaTemplate;

    private DefaultErrorHandler defaultErrorHandler;

    @BeforeEach
    void setUp() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLQ", -1)
        );

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(10L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(40L);

        defaultErrorHandler = new DefaultErrorHandler(recoverer, backOff);
    }

    @Test
    @DisplayName("Cenário de Resiliência: Após 3 retries com falha, mensagem deve ser publicada no tópico transacao-autorizada.DLQ")
    void shouldForwardToDlqAfterExhaustingRetries() {
        UUID eventId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(eventId)
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "transacao-autorizada", 0, 42L, accountId.toString(), event
        );

        when(dlqKafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Consumer<?, ?> mockConsumer = mock(Consumer.class);
        MessageListenerContainer mockContainer = mock(MessageListenerContainer.class);
        Exception simulatedDbError = new RuntimeException("Falha de conexão com o banco de dados do Ledger");

        // Executa as tentativas de processamento com falha
        // Tentativa 1 (falha inicial -> agenda retry 1)
        defaultErrorHandler.handleOne(simulatedDbError, record, mockConsumer, mockContainer);
        // Tentativa 2 (retry 1 falha -> agenda retry 2)
        defaultErrorHandler.handleOne(simulatedDbError, record, mockConsumer, mockContainer);
        // Tentativa 3 (retry 2 falha -> agenda retry 3)
        defaultErrorHandler.handleOne(simulatedDbError, record, mockConsumer, mockContainer);
        // Tentativa 4 (retry 3 falha -> esgota retries e aciona o recoverer para a DLQ)
        defaultErrorHandler.handleOne(simulatedDbError, record, mockConsumer, mockContainer);

        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(dlqKafkaTemplate).send(captor.capture());

        ProducerRecord<String, Object> capturedRecord = captor.getValue();
        assertThat(capturedRecord.topic()).isEqualTo("transacao-autorizada.DLQ");
        assertThat(capturedRecord.key()).isEqualTo(accountId.toString());
        assertThat(capturedRecord.value()).isEqualTo(event);
    }
}
