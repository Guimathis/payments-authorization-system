package com.payments.authorization.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.producer.PaymentEventProducer;
import com.payments.authorization.service.OpenTelemetryService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final PaymentEventProducer paymentEventProducer;
    private final OpenTelemetryService openTelemetryService;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.producer.send-timeout-ms:3000}")
    private long sendTimeoutMs;

    public List<PublishResult> publishBatchPipelined(List<OutboxEvent> pendingEvents) {
        if (pendingEvents == null || pendingEvents.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<PublishResult>> futures = pendingEvents.stream()
                .map(event -> {
                    // 1. Desserialização do payload
                    PaymentAuthorizedEvent payloadEvent;
                    try {
                        payloadEvent = deserialize(event.getPayload());
                    } catch (JsonProcessingException e) {
                        log.error("Falha ao desserializar payload do evento outbox [id={}]: {}", event.getId(), e.getMessage());
                        return CompletableFuture.completedFuture(new PublishResult(event, e));
                    }

                    // 2. Extração e ativação do contexto de rastreio OpenTelemetry
                    Context eventTraceContext = fetchEventTraceContext(event.getTraceContext());
                    try (Scope scope = eventTraceContext.makeCurrent()) {
                        try {
                            return paymentEventProducer.sendPaymentAuthorizedEvent(payloadEvent)
                                    .thenApply(result -> new PublishResult(event, null))
                                    .exceptionally(ex -> new PublishResult(event, unwrap(ex)));
                        } catch (CallNotPermittedException ex) {
                            log.warn("Circuit Breaker 'kafkaProducer' está ABERTO. Envio bloqueado para o evento [id={}]", event.getId());
                            return CompletableFuture.completedFuture(new PublishResult(event, ex));
                        }
                    }
                })
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Timeout de {}ms atingido aguardando conclusão do lote de {} eventos na outbox",
                    sendTimeoutMs, pendingEvents.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Thread interrompida aguardando envio do lote da outbox", e);
        } catch (Exception e) {
            log.error("Exceção inesperada aguardando lote de eventos da outbox", e);
        }

        return IntStream.range(0, pendingEvents.size())
                .mapToObj(i -> {
                    CompletableFuture<PublishResult> future = futures.get(i);
                    if (future.isDone() && !future.isCompletedExceptionally()) {
                        try {
                            return future.join();
                        } catch (Exception ex) {
                            return new PublishResult(pendingEvents.get(i), unwrap(ex));
                        }
                    } else {
                        return new PublishResult(pendingEvents.get(i),
                                new TimeoutException("Tempo limite excedido aguardando confirmação do broker"));
                    }
                })
                .toList();
    }

    private PaymentAuthorizedEvent deserialize(String payload) throws JsonProcessingException {
        return objectMapper.readValue(payload, PaymentAuthorizedEvent.class);
    }

    private Throwable unwrap(Throwable ex) {
        return ex;
    }

    private Context fetchEventTraceContext(String traceContext) {
        Context parentContext = Context.current();
        if (traceContext != null && !traceContext.isBlank()) {
            try {
                Map<String, String> traceHeaders = objectMapper.readValue(
                        traceContext,
                        new TypeReference<Map<String, String>>() {}
                );
                if (openTelemetryService != null) {
                    parentContext = openTelemetryService.extractTraceContext(traceHeaders);
                }
            } catch (JsonProcessingException e) {
                log.warn("Falha ao extrair contexto OpenTelemetry do evento: {}", e.getMessage());
            }
        }
        return parentContext;
    }
}
