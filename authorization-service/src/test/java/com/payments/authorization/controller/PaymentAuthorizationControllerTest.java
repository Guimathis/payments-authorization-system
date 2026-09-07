package com.payments.authorization.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.client.AntifraudClient;
import com.payments.authorization.client.dto.AntifraudEvaluationRequestDto;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.entity.IdempotencyRecord;
import com.payments.authorization.entity.IdempotencyStatus;
import com.payments.authorization.repository.IdempotencyRecordRepository;
import com.payments.authorization.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentAuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockBean
    private AntifraudClient antifraudClient;

    @MockBean
    private com.payments.authorization.producer.PaymentEventProducer paymentEventProducer;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();

        when(antifraudClient.evaluate(any(), any(AntifraudEvaluationRequestDto.class)))
                .thenReturn(AntifraudEvaluationResponseDto.builder()
                        .evaluationId(UUID.randomUUID())
                        .recommendation("APPROVED")
                        .riskScore(15)
                        .evaluatedAt(Instant.now())
                        .build());
    }

    @Test
    @DisplayName("Critério 1: Três chamadas consecutivas com a mesma chave: 1ª retorna 201 Created, 2ª e 3ª retornam 200 OK com mesmo corpo sem duplicar transação")
    void shouldHandleIdempotentRequestsCorrectly() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentAuthorizationRequestDto request = PaymentAuthorizationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("BRL")
                .paymentMethod("CREDIT_CARD")
                .cardToken("tok_visa_1234_sandbox")
                .build();

        // 1ª Chamada: deve retornar 201 Created
        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId", notNullValue()))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.authorizationCode", notNullValue()))
                .andExpect(jsonPath("$.amount", is(250.00)))
                .andExpect(jsonPath("$.currency", is("BRL")))
                .andReturn();

        String firstResponseBody = firstResult.getResponse().getContentAsString();
        assertThat(transactionRepository.count()).isEqualTo(1);

        // 2ª Chamada: deve retornar 200 OK com mesmo corpo
        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String secondResponseBody = secondResult.getResponse().getContentAsString();
        assertThat(secondResponseBody).isEqualTo(firstResponseBody);
        assertThat(transactionRepository.count()).isEqualTo(1);

        // 3ª Chamada: deve retornar 200 OK com mesmo corpo
        MvcResult thirdResult = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String thirdResponseBody = thirdResult.getResponse().getContentAsString();
        assertThat(thirdResponseBody).isEqualTo(firstResponseBody);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Critério 2: Requisição concorrente imediata com a chave em PROCESSING deve retornar 409 Conflict")
    void shouldReturnConflictWhenKeyIsProcessing() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();

        // Insere registro prévio com status PROCESSING
        idempotencyRecordRepository.save(IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .status(IdempotencyStatus.PROCESSING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        PaymentAuthorizationRequestDto request = PaymentAuthorizationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .paymentMethod("CREDIT_CARD")
                .cardToken("tok_master_9999")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title", is("Resource Conflict")))
                .andExpect(jsonPath("$.type", is("urn:problem-type:conflict")));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request se o header Idempotency-Key estiver ausente")
    void shouldReturnBadRequestWhenMissingIdempotencyKey() throws Exception {
        PaymentAuthorizationRequestDto request = PaymentAuthorizationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("BRL")
                .paymentMethod("CREDIT_CARD")
                .cardToken("tok_visa_1234")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Missing Request Header")))
                .andExpect(jsonPath("$.type", is("urn:problem-type:missing-header")));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando payload contiver dados inválidos")
    void shouldReturnBadRequestForInvalidPayload() throws Exception {
        PaymentAuthorizationRequestDto request = PaymentAuthorizationRequestDto.builder()
                .accountId(null)
                .merchantId(null)
                .amount(new BigDecimal("-50.00"))
                .currency("")
                .paymentMethod("")
                .cardToken("")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Validation Failed")))
                .andExpect(jsonPath("$.type", is("urn:problem-type:validation-error")))
                .andExpect(jsonPath("$.errors", notNullValue()));
    }

    @Test
    @DisplayName("Deve aceitar payload formatado em camelCase com sucesso")
    void shouldAcceptCamelCasePayload() throws Exception {
        String camelCaseJson = """
                {
                  "accountId": "dd96d472-7e8a-47cf-96d4-727e8af7cf77",
                  "amount": 250,
                  "cardToken": "tok_visa_1234_sandbox",
                  "currency": "BRL",
                  "merchantId": "91ff88f6-2664-4287-bf88-f62664f287ef",
                  "paymentMethod": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(camelCaseJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.amount").value(250))
                .andExpect(jsonPath("$.currency", is("BRL")));
    }
}
