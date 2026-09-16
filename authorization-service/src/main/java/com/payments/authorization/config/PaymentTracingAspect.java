package com.payments.authorization.config;

import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import io.opentelemetry.api.trace.Span;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PaymentTracingAspect {
    @Before(value = "execution(* com.payments.authorization.controller.PaymentAuthorizationController.authorizePayment(..)) " +
            "&& args(idempotencyKey, requestDto)", argNames = "idempotencyKey,requestDto")
    public void enrichPaymentSpan(String idempotencyKey, PaymentAuthorizationRequestDto requestDto) {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            if (requestDto.getAccountId() != null) {
                span.setAttribute("payment.account_id", requestDto.getAccountId().toString());
            }
            if (requestDto.getAmount() != null) {
                span.setAttribute("payment.amount", requestDto.getAmount().doubleValue());
            }
            if (idempotencyKey != null) {
                span.setAttribute("payment.idempotency_key", idempotencyKey);
            }
        }
    }
}
