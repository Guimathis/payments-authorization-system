package com.payments.authorization.producer;

import com.payments.authorization.event.PaymentAuthorizedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentAuthorizedEvent> kafkaTemplate;

    @Value("${app.kafka.topics.transacao-autorizada:transacao-autorizada}")
    private String transacaoAutorizadaTopic;

    public void sendPaymentAuthorizedEvent(PaymentAuthorizedEvent event) {
        String partitionKey = event.getAccountId() != null ? event.getAccountId().toString() : event.getPaymentId().toString();

        log.info("Publicando evento PaymentAuthorizedEvent no tópico {}. Chave: {}, PaymentId: {}",
                transacaoAutorizadaTopic, partitionKey, event.getPaymentId());

        CompletableFuture<SendResult<String, PaymentAuthorizedEvent>> future =
                kafkaTemplate.send(transacaoAutorizadaTopic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Evento PaymentAuthorizedEvent publicado com sucesso. Offset: {}, Partição: {}",
                        result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
            } else {
                log.error("Erro ao publicar evento PaymentAuthorizedEvent para paymentId {}: {}",
                        event.getPaymentId(), ex.getMessage(), ex);
            }
        });
    }
}
