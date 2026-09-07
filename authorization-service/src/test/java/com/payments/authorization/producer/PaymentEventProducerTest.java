package com.payments.authorization.producer;

import com.payments.authorization.event.PaymentAuthorizedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentEventProducerTest {

    @Mock
    private KafkaTemplate<String, PaymentAuthorizedEvent> kafkaTemplate;

    @InjectMocks
    private PaymentEventProducer paymentEventProducer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentEventProducer, "transacaoAutorizadaTopic", "transacao-autorizada");
    }

    @Test
    @DisplayName("Deve enviar evento PaymentAuthorizedEvent com o account_id como chave de partição")
    void shouldSendPaymentAuthorizedEventWithAccountIdAsKey() {
        UUID accountId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(paymentId)
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("150.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        RecordMetadata metadata = new RecordMetadata(new TopicPartition("transacao-autorizada", 0), 0, 0, 0, 0, 0);
        SendResult<String, PaymentAuthorizedEvent> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq("transacao-autorizada"), eq(accountId.toString()), any(PaymentAuthorizedEvent.class)))
                .thenReturn(future);

        paymentEventProducer.sendPaymentAuthorizedEvent(event);

        ArgumentCaptor<PaymentAuthorizedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentAuthorizedEvent.class);
        verify(kafkaTemplate).send(eq("transacao-autorizada"), eq(accountId.toString()), eventCaptor.capture());

        PaymentAuthorizedEvent captured = eventCaptor.getValue();
        assertThat(captured.getPaymentId()).isEqualTo(paymentId);
        assertThat(captured.getAccountId()).isEqualTo(accountId);
        assertThat(captured.getAmount()).isEqualTo(new BigDecimal("150.00"));
    }
}
