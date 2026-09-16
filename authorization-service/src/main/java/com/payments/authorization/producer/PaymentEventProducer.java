package com.payments.authorization.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.service.OpenTelemetryService;
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

    public void  sendPaymentAuthorizedEventSync(PaymentAuthorizedEvent event) throws Exception {

        String partitionKey = event.getAccountId() != null ? event.getAccountId().toString() : event.getPaymentId().toString();

        log.info("Publicando evento síncrono PaymentAuthorizedEvent no tópico {}. Chave: {}, PaymentId: {}",
                transacaoAutorizadaTopic, partitionKey, event.getPaymentId());

        SendResult<String, PaymentAuthorizedEvent> result;

        ProducerRecord<String, PaymentAuthorizedEvent> record =
                new ProducerRecord<>(transacaoAutorizadaTopic, partitionKey, event);

        openTelemetryService.captureTraceContext().forEach((k, v) ->
                record.headers().add(new RecordHeader(k, v.getBytes())));

        result = kafkaTemplate.send(record).get(sendTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("Confirmação de ACK recebida do broker Kafka para paymentId {}. Offset: {}, Partição: {}",
                event.getPaymentId(), result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
    }

    public void sendPaymentAuthorizedEvent(PaymentAuthorizedEvent event) {
        String partitionKey = event.getAccountId() != null ? event.getAccountId().toString() : event.getPaymentId().toString();

        log.info("Publicando evento PaymentAuthorizedEvent no tópico {}. Chave: {}, PaymentId: {}",
                transacaoAutorizadaTopic, partitionKey, event.getPaymentId());

        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> future =
                kafkaTemplate.send(transacaoAutorizadaTopic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Evento PaymentAuthorizedEvent publicado com sucesso. Offset: {}, Partição: {}",
                        result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
            } else {
                log.error("Erro ao publicar evento PaymentAuthorizedEvent para paymentId {}: {}",
                        event.getPaymentId(), ex.getMessage(), ex);
            }
        });
    }
}
