package com.payments.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.dto.PaymentAuthorizationResponseDto;
import com.payments.authorization.entity.OutboxEvent;
import com.payments.authorization.entity.OutboxStatus;
import com.payments.authorization.entity.Transaction;
import com.payments.authorization.entity.TransactionStatus;
import com.payments.authorization.event.PaymentAuthorizedEvent;
import com.payments.authorization.repository.OutboxEventRepository;
import com.payments.authorization.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;
    private final OutboxEventRepository outboxEventRepository;
    private final OpenTelemetryService openTelemetryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentAuthorizationResponseDto saveTransactionAndCompleteIdempotency(
            String idempotencyKey,
            PaymentAuthorizationRequestDto request,
            AntifraudEvaluationResponseDto evaluationResponse) {

        boolean isApproved = "APPROVED".equalsIgnoreCase(evaluationResponse.getRecommendation());
        TransactionStatus status = isApproved ? TransactionStatus.APPROVED : TransactionStatus.REJECTED;
        String authCode = isApproved ? "AUTH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase() : null;

        Transaction transaction = Transaction.builder()
                .accountId(request.getAccountId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethod(request.getPaymentMethod())
                .status(status)
                .authorizationCode(authCode)
                .antifraudScore(evaluationResponse.getRiskScore())
                .createdAt(Instant.now())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        PaymentAuthorizationResponseDto responseDto = PaymentAuthorizationResponseDto.builder()
                .paymentId(savedTx.getId())
                .status(savedTx.getStatus().name())
                .authorizationCode(savedTx.getAuthorizationCode())
                .amount(savedTx.getAmount())
                .currency(savedTx.getCurrency())
                .createdAt(savedTx.getCreatedAt())
                .build();

        idempotencyService.complete(idempotencyKey, responseDto);

        if (isApproved) {
            PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType("PAYMENT_AUTHORIZED")
                    .paymentId(savedTx.getId())
                    .accountId(savedTx.getAccountId())
                    .merchantId(savedTx.getMerchantId())
                    .amount(savedTx.getAmount())
                    .currency(savedTx.getCurrency())
                    .timestamp(savedTx.getCreatedAt())
                    .build();

            try {
                String traceHeaders = objectMapper.writeValueAsString(openTelemetryService.captureTraceContext());

                String payloadJson = objectMapper.writeValueAsString(event);
                OutboxEvent outboxEvent = OutboxEvent.builder()
                        .aggregateType("TRANSACTION")
                        .aggregateId(savedTx.getId().toString())
                        .type("PAYMENT_AUTHORIZED")
                        .payload(payloadJson)
                        .traceContext(traceHeaders)
                        .status(OutboxStatus.PENDING)
                        .retryCount(0)
                        .createdAt(savedTx.getCreatedAt())
                        .build();

                outboxEventRepository.save(outboxEvent);
                log.info("Evento salvo na outbox com status PENDING para paymentId {}", savedTx.getId());
            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar evento para outbox_events para transação {}: {}", savedTx.getId(), e.getMessage());
                throw new IllegalStateException("Falha ao serializar evento de outbox", e);
            }
        }

        return responseDto;
    }
}
