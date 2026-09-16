package com.payments.antifraud.service;

import com.payments.antifraud.dto.AntifraudEvaluationRequestDto;
import com.payments.antifraud.dto.AntifraudEvaluationResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AntifraudEvaluationService {

    private static final BigDecimal MAX_SAFE_AMOUNT = new BigDecimal("5000.00");
    private static final int APPROVED_SCORE = 15;
    private static final int REJECTED_SCORE = 95;

    public AntifraudEvaluationResponseDto evaluate(AntifraudEvaluationRequestDto request, Long delayMs) {
        log.info("Recebida requisição de análise de risco para conta: {}, valor: {}", request.getAccountId(), request.getAmount());
        if (delayMs != null && delayMs > 0) {
            try {
                log.info("Simulando atraso de resposta de {} ms no antifraude", delayMs);
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Simulação de delay interrompida", e);
            }
        }

        boolean isHighRisk = request.getAmount().compareTo(MAX_SAFE_AMOUNT) > 0 
                || Boolean.TRUE.equals(request.getSuspicious());

        String recommendation;
        int riskScore;

        if (isHighRisk) {
            recommendation = "REJECTED";
            riskScore = REJECTED_SCORE;
            log.warn("Transação avaliada como ALTO RISCO (REJECTED): conta: {}, valor: {}, suspicious: {}",
                    request.getAccountId(), request.getAmount(), request.getSuspicious());
        } else {
            recommendation = "APPROVED";
            riskScore = APPROVED_SCORE;
            log.info("Transação avaliada como BAIXO RISCO (APPROVED): conta: {}, valor: {}",
                    request.getAccountId(), request.getAmount());
        }

        return AntifraudEvaluationResponseDto.builder()
                .evaluationId(UUID.randomUUID())
                .recommendation(recommendation)
                .riskScore(riskScore)
                .evaluatedAt(Instant.now())
                .build();
    }
}
