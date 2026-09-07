package com.payments.authorization.service;

import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.dto.PaymentAuthorizationResponseDto;
import com.payments.authorization.entity.Transaction;
import com.payments.authorization.entity.TransactionStatus;
import com.payments.authorization.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

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

        return responseDto;
    }
}
