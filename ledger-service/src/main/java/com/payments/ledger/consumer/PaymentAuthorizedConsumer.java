package com.payments.ledger.consumer;

import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAuthorizedConsumer {

    private final LedgerService ledgerService;

    @KafkaListener(
            topics = "${app.kafka.topics.transacao-autorizada:transacao-autorizada}",
            groupId = "${spring.kafka.consumer.group-id:ledger-group}"
    )
    public void consume(PaymentAuthorizedEvent event) {
        log.info("Recebido evento PaymentAuthorizedEvent no LedgerService: paymentId={}, accountId={}, amount={}",
                event.getPaymentId(), event.getAccountId(), event.getAmount());
        ledgerService.processPaymentDebit(event);
    }
}
