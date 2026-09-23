package com.payments.authorization.outbox;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollingPublisher {

    private final OutboxService outboxService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${app.outbox.batch-size:50}")
    private int defaultBatchSize;

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:1000}")
    public void pollAndPublish() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("kafkaProducer");

        // Se o circuito estiver ABERTO, não consulta o banco de dados
        if (cb.getState() == CircuitBreaker.State.OPEN) {
            log.debug("Circuit Breaker 'kafkaProducer' está ABERTO. Pulando consulta ao banco de dados.");
            return;
        }

        try {
            // Em HALF_OPEN, reduz o lote para 1 envio de teste (probe)
            int batchSize = (cb.getState() == CircuitBreaker.State.HALF_OPEN) ? 1 : defaultBatchSize;
            int count = outboxService.publishPendingEvents(batchSize);
            if (count > 0) {
                log.info("Outbox Polling concluído: {} mensagens publicadas com sucesso.", count);
            }
        } catch (Exception e) {
            log.error("Erro inesperado no agendador OutboxPollingPublisher: {}", e.getMessage(), e);
        }
    }
}
