package com.payments.authorization.producer;

import com.payments.authorization.event.PaymentAuthorizedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.transacao-autorizada:transacao-autorizada}")
    private String transacaoAutorizadaTopic;

    @Value("${app.kafka.producer.send-timeout-ms:3000}")
    private long sendTimeoutMs;

    @CircuitBreaker(name = "kafkaProducer")
    public CompletableFuture<SendResult<String, Object>> sendPaymentAuthorizedEvent(PaymentAuthorizedEvent payloadEvent) {
        String partitionKey = payloadEvent.getAccountId() != null
                ? payloadEvent.getAccountId().toString()
                : payloadEvent.getPaymentId().toString();

        log.debug("Publicando evento no tópico {}. Chave: {}, PaymentId: {}",
                transacaoAutorizadaTopic, partitionKey, payloadEvent.getPaymentId());

        return kafkaTemplate.send(transacaoAutorizadaTopic, partitionKey, payloadEvent);
    }

}
