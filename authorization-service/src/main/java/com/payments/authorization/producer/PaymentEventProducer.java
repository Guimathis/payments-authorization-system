package com.payments.authorization.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.service.OpenTelemetryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentAuthorizedEvent> kafkaTemplate;
    private final OpenTelemetryService openTelemetryService;

    @Value("${app.kafka.topics.transacao-autorizada:transacao-autorizada}")
    private String transacaoAutorizadaTopic;

    @Value("${app.kafka.producer.send-timeout-ms:3000}")
    private long sendTimeoutMs;

    @CircuitBreaker(name = "payment-event-producer", fallbackMethod = "sendPaymentAuthorizedEventSyncFallback")
    public void  sendPaymentAuthorizedEventSync(PaymentAuthorizedEvent event) throws Exception {

        String partitionKey = event.getAccountId() != null ? event.getAccountId().toString() : event.getPaymentId().toString();

        log.info("Publicando evento síncrono PaymentAuthorizedEvent no tópico {}. Chave: {}, PaymentId: {}",
                transacaoAutorizadaTopic, partitionKey, event.getPaymentId());

        SendResult<String, PaymentAuthorizedEvent> result = kafkaTemplate.send(transacaoAutorizadaTopic, partitionKey, event).get(sendTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("Confirmação de ACK recebida do broker Kafka para paymentId {}. Offset: {}, Partição: {}",
                event.getPaymentId(), result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
    }

    public void sendPaymentAuthorizedEventSyncFallback(PaymentAuthorizedEvent event, Throwable ex){
        log.warn("Caindo no fallback do PaymentEventProducer.");

    }

}
