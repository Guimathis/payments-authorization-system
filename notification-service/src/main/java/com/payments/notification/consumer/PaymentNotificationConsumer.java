package com.payments.notification.consumer;

import com.payments.notification.event.PaymentAuthorizedEvent;
import com.payments.notification.service.KafkaHeadersService;
import com.payments.notification.service.NotificationService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
