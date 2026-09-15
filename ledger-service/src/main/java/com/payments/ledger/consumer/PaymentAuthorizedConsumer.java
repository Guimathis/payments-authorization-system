package com.payments.ledger.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.service.KafkaHeadersService;
import com.payments.ledger.service.LedgerService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentAuthorizedConsumer {

    private final LedgerService ledgerService;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    @KafkaListener(
            topics = "${app.kafka.topics.transacao-autorizada:transacao-autorizada}",
            groupId = "${spring.kafka.consumer.group-id:ledger-group}"
    )
    public void consume(ConsumerRecord<String, PaymentAuthorizedEvent> record) {


        Context extractedContext = openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), record.headers(), new KafkaHeadersService());

        Span span = tracer.spanBuilder("ledger.process")
                .setParent(extractedContext)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute("messaging.system", "kafka")
                .setAttribute("messaging.destination", record.topic())
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            PaymentAuthorizedEvent event = record.value();
            log.info("Recebido evento PaymentAuthorizedEvent no LedgerService: paymentId={}, accountId={}, amount={}",
                    event.getPaymentId(), event.getAccountId(), event.getAmount());
            ledgerService.processPaymentDebit(event);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
