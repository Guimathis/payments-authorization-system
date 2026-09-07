package com.payments.notification.consumer;

import com.payments.notification.event.PaymentAuthorizedEvent;
import com.payments.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    private PaymentNotificationConsumer consumer;

    @Test
    @DisplayName("Deve encaminhar evento recebido para o NotificationService")
    void shouldDelegateEventToNotificationService() {
        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        consumer.consume(event);

        verify(notificationService).dispatchPaymentNotification(event);
    }
}
