package com.payments.notification.consumer;

import com.payments.notification.event.PaymentAuthorizedEvent;
import com.payments.notification.service.NotificationService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentNotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OpenTelemetry openTelemetry;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Tracer tracer;

    @InjectMocks
    private PaymentNotificationConsumer consumer;

    @Test
    @DisplayName("Deve encaminhar evento recebido para o NotificationService")
    void shouldDelegateEventToNotificationService() {
        UUID accountId = UUID.randomUUID();
        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();
        ConsumerRecord<String, PaymentAuthorizedEvent> record = new ConsumerRecord<>(
                "transacao-autorizada",
                0,
                0L,
                accountId.toString(),
                event
        );
        consumer.consume(record);

        verify(notificationService).dispatchPaymentNotification(event);
    }
}
