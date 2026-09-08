package com.payments.authorization.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPollingPublisher {

    private final OutboxService outboxService;

    @Scheduled(fixedDelayString = "${app.outbox.polling-interval-ms:1000}")
    public void pollAndPublish() {
        try {
            int count = outboxService.publishPendingEvents();
            if (count > 0) {
                log.info("Outbox Polling concluído: {} mensagens publicadas.", count);
            }
        } catch (Exception e) {
            log.error("Erro inesperado no agendador OutboxPollingPublisher: {}", e.getMessage(), e);
        }
    }
}
