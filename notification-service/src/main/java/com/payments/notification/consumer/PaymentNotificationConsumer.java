package com.payments.notification.consumer;

import com.payments.notification.event.PaymentAuthorizedEvent;
import com.payments.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka.topics.transacao-autorizada:transacao-autorizada}",
            groupId = "${spring.kafka.consumer.group-id:notification-group}"
    )
    public void consume(PaymentAuthorizedEvent event) {
        log.info("Recebido evento PaymentAuthorizedEvent no NotificationService: paymentId={}, accountId={}",
                event.getPaymentId(), event.getAccountId());
        notificationService.dispatchPaymentNotification(event);
    }
}
