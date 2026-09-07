package com.payments.authorization.service;

import com.payments.authorization.client.AntifraudIntegrationService;
import com.payments.authorization.client.dto.AntifraudEvaluationRequestDto;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.dto.PaymentAuthorizationResponseDto;
import com.payments.authorization.dto.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAuthorizationService {

    private final IdempotencyService idempotencyService;
    private final AntifraudIntegrationService antifraudIntegrationService;
    private final PaymentTransactionService paymentTransactionService;

    public PaymentResult processPayment(String idempotencyKey, PaymentAuthorizationRequestDto request) {
        log.info("Processando autorização de pagamento. Idempotency-Key: {}, Conta: {}, Valor: {}",
                idempotencyKey, request.getAccountId(), request.getAmount());

        // 1. Verificação e reserva de idempotência (Transação isolada REQUIRES_NEW)
        Optional<PaymentResult> replayResult = idempotencyService.checkOrReserve(idempotencyKey);
        if (replayResult.isPresent()) {
            return replayResult.get();
        }

        try {
            // 2. Avaliação de risco síncrona com Antifraude (Resilience4j + Fallback) sem prender conexão de banco
            AntifraudEvaluationRequestDto antifraudRequest = AntifraudEvaluationRequestDto.builder()
                    .accountId(request.getAccountId())
                    .amount(request.getAmount())
                    .paymentMethod(request.getPaymentMethod())
                    .suspicious(false)
                    .build();

            AntifraudEvaluationResponseDto evaluationResponse = antifraudIntegrationService.evaluate(antifraudRequest);

            // 3. Persistência da transação e finalização da idempotência (Transação atômica @Transactional)
            PaymentAuthorizationResponseDto responseDto = paymentTransactionService.saveTransactionAndCompleteIdempotency(
                    idempotencyKey, request, evaluationResponse);

            return new PaymentResult(responseDto, false);

        } catch (Exception ex) {
            log.error("Falha durante o processamento da transação com chave {}: {}", idempotencyKey, ex.getMessage());
            idempotencyService.markAsFailed(idempotencyKey);
            throw ex;
        }
    }
}
