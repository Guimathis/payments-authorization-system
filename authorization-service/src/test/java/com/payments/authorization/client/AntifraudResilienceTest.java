package com.payments.authorization.client;

import com.payments.authorization.client.dto.AntifraudEvaluationRequestDto;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.dto.PaymentResult;
import com.payments.authorization.service.PaymentAuthorizationService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AntifraudResilienceTest {

    @Autowired
    private AntifraudIntegrationService antifraudIntegrationService;

    @Autowired
    private PaymentAuthorizationService paymentAuthorizationService;

    @MockBean
    private AntifraudClient antifraudClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("antifraud");
        circuitBreaker.reset(); // Garante estado limpo antes de cada teste
    }

    @Test
    @DisplayName("Deve iniciar FECHADO (CLOSED) e transitar para ABERTO (OPEN) após taxa de falhas excedida")
    void shouldOpenCircuitBreakerWhenFailureThresholdIsExceeded() {
        // 1. Verifica estado inicial
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // 2. Simula falha/timeout na chamada Feign
        Request feignRequest = Request.create(Request.HttpMethod.POST, "/api/v1/antifraud/evaluations",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(antifraudClient.evaluate(any(), any()))
                .thenThrow(new FeignException.InternalServerError("Timeout/Down", feignRequest, null, null));

        AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .paymentMethod("CREDIT_CARD")
                .build();

        // 3. Executa as chamadas com falha (conforme configurado em application-test.yml: minimum-number-of-calls: 2)
        antifraudIntegrationService.evaluate(request); // 1ª falha
        antifraudIntegrationService.evaluate(request); // 2ª falha

        // 4. Valida se o circuito mudou de CLOSED para OPEN
        CircuitBreaker.State estadoAtual = circuitBreaker.getState();
        System.out.println("Estado atual do Circuit Breaker: " + estadoAtual);

        assertThat(estadoAtual).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isPositive();
    }

@Test
@DisplayName("Critério 3: Quando o antifraude falha/cai, aciona fallback contingencial aprovando valores <= R$ 500,00")
void shouldApproveInContingencyWhenAntifraudFailsForLowAmount() {
    Request feignRequest = Request.create(Request.HttpMethod.POST, "/api/v1/antifraud/evaluations",
            Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
    when(antifraudClient.evaluate(any(), any()))
            .thenThrow(new FeignException.InternalServerError("Antifraud offline", feignRequest, null, null));

    AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
            .accountId(UUID.randomUUID())
            .amount(new BigDecimal("350.00"))
            .paymentMethod("CREDIT_CARD")
            .build();

    AntifraudEvaluationResponseDto response = antifraudIntegrationService.evaluate(request);

    assertThat(response).isNotNull();
    assertThat(response.getRecommendation()).isEqualTo("APPROVED");
    assertThat(response.getRiskScore()).isEqualTo(0);
}

@Test
@DisplayName("Critério 3: Quando o antifraude falha/cai, aciona fallback contingencial rejeitando valores > R$ 500,00")
void shouldRejectInContingencyWhenAntifraudFailsForHighAmount() {
    Request feignRequest = Request.create(Request.HttpMethod.POST, "/api/v1/antifraud/evaluations",
            Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
    when(antifraudClient.evaluate(any(), any()))
            .thenThrow(new FeignException.InternalServerError("Antifraud offline", feignRequest, null, null));

    AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
            .accountId(UUID.randomUUID())
            .amount(new BigDecimal("1200.00"))
            .paymentMethod("CREDIT_CARD")
            .build();

    AntifraudEvaluationResponseDto response = antifraudIntegrationService.evaluate(request);

    assertThat(response).isNotNull();
    assertThat(response.getRecommendation()).isEqualTo("REJECTED");
    assertThat(response.getRiskScore()).isEqualTo(99);
}

@Test
@DisplayName("Ponta a ponta no Service: Antifraude offline não quebra o autorizador (sem 500) e aprova em contingência")
void shouldProcessPaymentInContingencyWithoutThrowingException() {
    Request feignRequest = Request.create(Request.HttpMethod.POST, "/api/v1/antifraud/evaluations",
            Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
    when(antifraudClient.evaluate(any(), any()))
            .thenThrow(new FeignException.ServiceUnavailable("Service Down", feignRequest, null, null));

    PaymentAuthorizationRequestDto request = PaymentAuthorizationRequestDto.builder()
            .accountId(UUID.randomUUID())
            .merchantId(UUID.randomUUID())
            .amount(new BigDecimal("450.00"))
            .currency("BRL")
            .paymentMethod("CREDIT_CARD")
            .cardToken("tok_123")
            .build();

    PaymentResult result = paymentAuthorizationService.processPayment(UUID.randomUUID().toString(), request);

    assertThat(result).isNotNull();
    assertThat(result.responseDto().getStatus()).isEqualTo("APPROVED");
    assertThat(result.responseDto().getAuthorizationCode()).isNotNull();
}
}
