package com.payments.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.producer.PaymentEventProducer;
import com.payments.authorization.repository.OutboxEventRepository;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;
    private final PaymentEventProducer paymentEventProducer;
    private final OpenTelemetryService openTelemetryService;
    private final ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    @Value("${app.outbox.max-retries:3}")
    private int maxRetries;

    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingForUpdate(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Encontrados {} eventos pendentes na outbox para publicação", pendingEvents.size());

        int publishedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            try {

                PaymentAuthorizedEvent payloadEvent = objectMapper.readValue(event.getPayload(), PaymentAuthorizedEvent.class);

                Context eventTraceContext = fetchEventTraceContext(event.getTraceContext());

                try (Scope scope = eventTraceContext.makeCurrent()) {
                    paymentEventProducer.sendPaymentAuthorizedEventSync(payloadEvent);
                }

                // Persistência pontual com commit imediato em transação isolada
                outboxEventProcessor.markAsSent(event.getId());

                publishedCount++;
            } catch (RetriableException ex) {
                log.error("Falha ao processar evento outbox [id={}, aggregateId={}]: {}",
                        event.getId(), event.getAggregateId(), ex.getMessage());
            } catch (Exception ex) {

                log.error("Falha ao publicar evento outbox [id={}, aggregateId={}]: {}",
                        event.getId(), event.getAggregateId(), ex.getMessage());

                // Atualização pontual do erro em transação isolada
                outboxEventProcessor.markAsFailedOrRetry(event.getId(), maxRetries);

                int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
                int updatedRetry = currentRetry + 1;
                event.setRetryCount(updatedRetry);
                if (updatedRetry >= maxRetries) {
                    event.setStatus(OutboxStatus.FAILED);
                }

                if (isBrokerCommunicationError(ex)) {
                    log.warn("Broker Kafka parece indisponível. Interrompendo lote atual para nova tentativa no próximo ciclo.");
                    break;
                }
            }
        }
        return publishedCount;
    }

    private Context fetchEventTraceContext(String traceContext) throws JsonProcessingException {
        Context parentContext = Context.current();
        if (traceContext != null && !traceContext.isBlank()) {
            Map<String, String> traceHeaders = objectMapper.readValue(
                    traceContext,
                    new TypeReference<Map<String, String>>() {
                    }
            );
            if (openTelemetryService != null) {
                parentContext = openTelemetryService.extractTraceContext(traceHeaders);
            }
        }
        return parentContext;
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
