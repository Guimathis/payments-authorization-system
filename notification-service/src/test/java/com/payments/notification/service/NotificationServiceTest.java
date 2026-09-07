package com.payments.notification.service;

import com.payments.notification.event.PaymentAuthorizedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }

    @Test
    @DisplayName("Deve registrar e formatar envio de notificação push/SMS")
    void shouldDispatchNotificationCorrectly() {
        UUID accountId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(paymentId)
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        notificationService.dispatchPaymentNotification(event);

        assertThat(notificationService.getDispatchedNotifications()).hasSize(1);
        PaymentAuthorizedEvent dispatched = notificationService.getDispatchedNotifications().get(0);
        assertThat(dispatched.getPaymentId()).isEqualTo(paymentId);
        assertThat(dispatched.getAccountId()).isEqualTo(accountId);
        assertThat(dispatched.getAmount()).isEqualByComparingTo(new BigDecimal("250.00"));
    }
}
