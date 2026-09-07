package com.payments.antifraud.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.antifraud.dto.AntifraudEvaluationRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AntifraudEvaluationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve aprovar transação com valor menor ou igual a R$ 5.000,00 e sem suspeita")
    void shouldApproveLowAmountTransaction() throws Exception {
        AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .paymentMethod("CREDIT_CARD")
                .suspicious(false)
                .build();

        mockMvc.perform(post("/api/v1/antifraud/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId", notNullValue()))
                .andExpect(jsonPath("$.recommendation", is("APPROVED")))
                .andExpect(jsonPath("$.riskScore", is(15)))
                .andExpect(jsonPath("$.evaluatedAt", notNullValue()));
    }

    @Test
    @DisplayName("Deve rejeitar transação com valor superior a R$ 5.000,00")
    void shouldRejectHighAmountTransaction() throws Exception {
        AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("5000.01"))
                .paymentMethod("CREDIT_CARD")
                .suspicious(false)
                .build();

        mockMvc.perform(post("/api/v1/antifraud/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evaluationId", notNullValue()))
                .andExpect(jsonPath("$.recommendation", is("REJECTED")))
                .andExpect(jsonPath("$.riskScore", is(95)));
    }

    @Test
    @DisplayName("Deve rejeitar transação com flag suspicious = true mesmo com valor baixo")
    void shouldRejectSuspiciousTransaction() throws Exception {
        AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .paymentMethod("CREDIT_CARD")
                .suspicious(true)
                .build();

        mockMvc.perform(post("/api/v1/antifraud/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation", is("REJECTED")))
                .andExpect(jsonPath("$.riskScore", is(95)));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando payload for inválido")
    void shouldReturnBadRequestForInvalidPayload() throws Exception {
        AntifraudEvaluationRequestDto request = AntifraudEvaluationRequestDto.builder()
                .accountId(null)
                .amount(new BigDecimal("-10.00"))
                .paymentMethod("")
                .build();

        mockMvc.perform(post("/api/v1/antifraud/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")))
                .andExpect(jsonPath("$.type", is("urn:problem-type:validation-error")));
    }
}
