package com.payments.authorization.outbox;

import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.exception.OutboxExceptionClassifier;
import com.payments.authorization.exception.OutboxExceptionClassifier.ErrorCategory;
import com.payments.authorization.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxEventProcessor outboxEventProcessor;
    private final OutboxExceptionClassifier exceptionClassifier;

    @Value("${app.outbox.batch-size:50}")
    private int defaultBatchSize;

    @Value("${app.outbox.max-retries:5}")
    private int maxRetries;

    public void publishPendingEvents() {
        publishPendingEvents(defaultBatchSize);
    }

    public int publishPendingEvents(int batchSize) {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingForUpdate(batchSize);
        if (pendingEvents == null || pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("Encontrados {} eventos pendentes na outbox para publicação", pendingEvents.size());

        List<PublishResult> results = outboxEventPublisher.publishBatchPipelined(pendingEvents);

        int successCount = 0;

        for (PublishResult result : results) {
            OutboxEvent event = result.event();
            Throwable error = result.error();

            if (result.isSuccess()) {
                outboxEventProcessor.markAsSent(event.getId());
                successCount++;
                continue;
            }

            ErrorCategory category = exceptionClassifier.classify(error);
            String errorMsg = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

            switch (category) {
                case FATAL_POISON_PILL -> {
                    log.error("Evento outbox [id={}, aggregateId={}] identificado como POISON_PILL. Marcando como FAILED. Erro: {}",
                            event.getId(), event.getAggregateId(), errorMsg);
                    outboxEventProcessor.markAsPoisonPill(event.getId(), errorMsg);
                }
                case TRANSIENT_INFRASTRUCTURE, SYSTEMIC_CONFIGURATION -> {
                    log.warn("Falha de infraestrutura no Kafka ao publicar evento outbox [id={}]. Interrompendo lote atual. Erro: {}",
                            event.getId(), errorMsg);
                    outboxEventProcessor.recordTransientFailure(event.getId(), errorMsg);
                    return successCount;
                }
                case UNKNOWN -> {
                    log.warn("Erro ao publicar evento outbox [id={}]. Registrando retry. Erro: {}",
                            event.getId(), errorMsg);
                    outboxEventProcessor.markForRetry(event.getId(), maxRetries, errorMsg);
                }
            }
        }

        return successCount;
    }
}
