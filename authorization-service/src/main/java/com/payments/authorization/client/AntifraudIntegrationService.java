package com.payments.authorization.client;

import com.payments.authorization.client.dto.AntifraudEvaluationRequestDto;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AntifraudIntegrationService {

    private static final BigDecimal CONTINGENCY_MAX_AMOUNT = new BigDecimal("500.00");
    private final AntifraudClient antifraudClient;

    @CircuitBreaker(name = "antifraud", fallbackMethod = "evaluateFallback")
    @Retry(name = "antifraud")
    @Observed(name = "antifraud.evaluate", contextualName = "avaliar-antifraude")
    public AntifraudEvaluationResponseDto evaluate(AntifraudEvaluationRequestDto request) {
        log.info("Enviando requisição de avaliação para o antifraud-service. Conta: {}, Valor: {}", 
                request.getAccountId(), request.getAmount());
        return antifraudClient.evaluate(null, request);
    }

    public AntifraudEvaluationResponseDto evaluateFallback(AntifraudEvaluationRequestDto request, Throwable ex) {
        log.warn("Falha ou timeout na comunicação com antifraud-service. Acionando fallback contingencial. Causa: {}",
                ex.getMessage());

        boolean approvedInContingency = request.getAmount().compareTo(CONTINGENCY_MAX_AMOUNT) <= 0;

        if (approvedInContingency) {
            log.info("Autorização contingencial APROVADA para valor <= R$ 500,00 (valor: {})", request.getAmount());
            return AntifraudEvaluationResponseDto.builder()
                    .evaluationId(UUID.randomUUID())
                    .recommendation("APPROVED")
                    .riskScore(0)
                    .evaluatedAt(Instant.now())
                    .build();
        } else {
            log.warn("Autorização contingencial REJEITADA preventivamente para valor > R$ 500,00 (valor: {})", request.getAmount());
            return AntifraudEvaluationResponseDto.builder()
                    .evaluationId(UUID.randomUUID())
                    .recommendation("REJECTED")
                    .riskScore(99)
                    .evaluatedAt(Instant.now())
                    .build();
        }
    }
}
