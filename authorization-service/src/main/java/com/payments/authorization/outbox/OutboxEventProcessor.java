package com.payments.authorization.outbox;

import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsSent(UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.SENT);
            event.setProcessedAt(Instant.now());
            event.setLastErrorMessage(null);
            event.setNextRetryAt(null);
            outboxEventRepository.save(event);
            log.debug("Evento outbox [id={}] atualizado com status SENT", eventId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAllAsSent(List<UUID> eventIds) {
        List<OutboxEvent> allById = outboxEventRepository.findAllById(eventIds);
        Instant now = Instant.now();
        allById.forEach(outboxEvent -> {
            log.debug("Evento outbox [id={}] atualizado com status SENT", outboxEvent.getId());
            outboxEvent.setStatus(OutboxStatus.SENT);
            outboxEvent.setProcessedAt(now);
            outboxEvent.setLastErrorMessage(null);
            outboxEvent.setNextRetryAt(null);
        });
        outboxEventRepository.saveAll(allById);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPoisonPill(UUID eventId, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.FAILED);
            event.setProcessedAt(Instant.now());
            event.setLastErrorMessage(truncate("POISON_PILL: " + errorMessage));
            event.setNextRetryAt(null);
            outboxEventRepository.save(event);
            log.warn("Evento outbox [id={}] marcado imediatamente como FAILED por ser POISON_PILL. Erro: {}",
                    eventId, errorMessage);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markForRetry(UUID eventId, int maxRetries, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
            int newRetryCount = currentRetry + 1;
            event.setRetryCount(newRetryCount);
            event.setLastErrorMessage(truncate(errorMessage));

            if (newRetryCount >= maxRetries) {
                event.setStatus(OutboxStatus.FAILED);
                event.setProcessedAt(Instant.now());
                event.setNextRetryAt(null);
                log.warn("Evento outbox [id={}] excedeu o teto de {} tentativas e foi definido como FAILED. Último erro: {}",
                        eventId, maxRetries, errorMessage);
            } else {
                event.setStatus(OutboxStatus.PENDING);
                long backoffSeconds = (long) Math.min(Math.pow(2, newRetryCount) * 2, 300);
                event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
                log.debug("Evento outbox [id={}] agendado para nova tentativa ({} de {}) em {}s",
                        eventId, newRetryCount, maxRetries, backoffSeconds);
            }

            outboxEventRepository.save(event);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransientFailure(UUID eventId, String errorMessage) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setLastErrorMessage(truncate(errorMessage));
            event.setStatus(OutboxStatus.PENDING);
            // Atrasa a próxima tentativa em 15s para dar tempo da infraestrutura se restabelecer
            event.setNextRetryAt(Instant.now().plusSeconds(15));
            outboxEventRepository.save(event);
            log.debug("Evento outbox [id={}] registrou falha transitória de infraestrutura sem incrementar retryCount", eventId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailedOrRetry(UUID eventId, int maxRetries) {
        markForRetry(eventId, maxRetries, "Falha de processamento");
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_ERROR_MESSAGE_LENGTH ? text.substring(0, MAX_ERROR_MESSAGE_LENGTH) : text;
    }
}
