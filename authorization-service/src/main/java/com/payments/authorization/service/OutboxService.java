package com.payments.authorization.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.producer.PaymentEventProducer;
import com.payments.authorization.repository.OutboxEventRepository;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventProducer paymentEventProducer;
    private final OpenTelemetryService openTelemetryService;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingForUpdate(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Encontrados {} eventos pendentes na outbox para publicação", pendingEvents.size());

        int publishedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            try {
                String traceContext = event.getTraceContext();

                Context parent_context = openTelemetryService.extractTraceContext(objectMapper.readValue(
                        traceContext,
                        new TypeReference<Map<String, String>>() {
                        }
                ));

                Span span = tracer.spanBuilder("outbox.publish." + event.getType())
                        .setParent(parent_context)
                        .setAttribute("messaging.system", "kafka")
                        .setSpanKind(SpanKind.PRODUCER)
                        .setAttribute("outbox.retry_count", event.getRetryCount())
                        .startSpan();
                try (Scope scope = span.makeCurrent()) {
                    if (event.getRetryCount() > 0) {
                        span.addEvent("outbox.retry", Attributes.of(AttributeKey.longKey("Attempt"), event.getRetryCount().longValue()));
                    }
                    PaymentAuthorizedEvent payloadEvent = objectMapper.readValue(event.getPayload(), PaymentAuthorizedEvent.class);

                    paymentEventProducer.sendPaymentAuthorizedEventSync(payloadEvent);
                    event.setStatus(OutboxStatus.SENT);
                    event.setProcessedAt(Instant.now());
                    outboxEventRepository.save(event);
                    publishedCount++;

                } catch (Exception e) {
                    span.recordException(e);
                    span.setStatus(StatusCode.ERROR);
                    throw e;
                } finally {
                    span.end();
                }
                log.info("Evento outbox {} publicado com sucesso e marcado como SENT", event.getId());
            } catch (Exception ex) {
                log.error("Falha ao publicar evento outbox [id={}, aggregateId={}]: {}",
                        event.getId(), event.getAggregateId(), ex.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                outboxEventRepository.save(event);

                if (isBrokerCommunicationError(ex)) {
                    log.warn("Broker Kafka parece indisponível. Interrompendo lote atual para nova tentativa no próximo ciclo.");
                    break;
                }
            }
        }
        return publishedCount;
    }

    private boolean isBrokerCommunicationError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current instanceof KafkaException
                    || current.getClass().getName().contains("DisconnectException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
