package com.payments.authorization.producer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.service.OpenTelemetryService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentAuthorizedEvent> kafkaTemplate;
    private final OpenTelemetryService openTelemetryService;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.transacao-autorizada:transacao-autorizada}")
    private String transacaoAutorizadaTopic;

    @Value("${app.kafka.producer.send-timeout-ms:3000}")
    private long sendTimeoutMs;

    public SendResult<String, PaymentAuthorizedEvent> sendPaymentAuthorizedEventSync(OutboxEvent event) throws Exception {
        PaymentAuthorizedEvent payloadEvent = objectMapper.readValue(event.getPayload(), PaymentAuthorizedEvent.class);

        String partitionKey = payloadEvent.getAccountId() != null ? payloadEvent.getAccountId().toString() : payloadEvent.getPaymentId().toString();

        log.info("Publicando evento síncrono PaymentAuthorizedEvent no tópico {}. Chave: {}, PaymentId: {}",
                transacaoAutorizadaTopic, partitionKey, payloadEvent.getPaymentId());

        String traceContext = event.getTraceContext();

        Context parent_context = openTelemetryService.extractTraceContext(objectMapper.readValue(
                traceContext,
                new TypeReference<Map<String, String>>() {
                }
        ));

        Span span = tracer.spanBuilder("outbox.publish")
                .setParent(parent_context)
                .startSpan();

        SendResult<String, PaymentAuthorizedEvent> result;
        try (Scope scope = span.makeCurrent()) {

            ProducerRecord<String, PaymentAuthorizedEvent> record =
                    new ProducerRecord<>(transacaoAutorizadaTopic, partitionKey, payloadEvent);

            openTelemetryService.captureTraceContext().forEach((k, v) ->
                    record.headers().add(new RecordHeader(k, v.getBytes())));

            result = kafkaTemplate.send(record).get(sendTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
        log.info("Confirmação de ACK recebida do broker Kafka para paymentId {}. Offset: {}, Partição: {}",
                payloadEvent.getPaymentId(), result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
        return result;
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
