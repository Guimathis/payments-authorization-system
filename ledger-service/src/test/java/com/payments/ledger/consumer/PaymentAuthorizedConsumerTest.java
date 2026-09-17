package com.payments.ledger.consumer;

import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.service.LedgerService;
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
class PaymentAuthorizedConsumerTest {

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private PaymentAuthorizedConsumer consumer;

    @Test
    @DisplayName("Consumer deve repassar evento recebido do Kafka para o LedgerService")
    void shouldDelegateEventToLedgerService() {
        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        consumer.consume(event);

        verify(ledgerService).processPaymentDebit(event);
    }
}
