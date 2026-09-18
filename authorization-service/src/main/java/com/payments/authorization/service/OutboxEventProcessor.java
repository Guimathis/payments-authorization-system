package com.payments.authorization.service;

import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsSent(UUID eventId) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(OutboxStatus.SENT);
            event.setProcessedAt(Instant.now());
            outboxEventRepository.save(event);
            log.debug("Evento outbox [id={}] atualizado com status SENT", eventId);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailedOrRetry(UUID eventId, int maxRetries) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            int currentRetry = event.getRetryCount() != null ? event.getRetryCount() : 0;
            int newRetryCount = currentRetry + 1;
            event.setRetryCount(newRetryCount);

            if (newRetryCount >= maxRetries) {
                event.setStatus(OutboxStatus.FAILED);
                log.warn("Evento outbox [id={}] excedeu o teto de {} tentativas e foi definido como FAILED", eventId, maxRetries);
            } else {
                event.setStatus(OutboxStatus.PENDING);
                log.debug("Evento outbox [id={}] registrou tentativa {} de {}", eventId, newRetryCount, maxRetries);
            }

            outboxEventRepository.save(event);
        });
    }
}
