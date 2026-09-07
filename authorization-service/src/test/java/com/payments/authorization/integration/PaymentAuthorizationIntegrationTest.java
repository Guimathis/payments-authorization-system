package com.payments.authorization.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.authorization.client.AntifraudClient;
import com.payments.authorization.client.dto.AntifraudEvaluationRequestDto;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.repository.IdempotencyRecordRepository;
import com.payments.authorization.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentAuthorizationIntegrationTest {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payments_auth_test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
        }
    }

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

    @BeforeEach
    void setUp() {
        if (isDockerAvailable()) {
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
    }

    @Test
    @EnabledIf("isDockerAvailable")
    @DisplayName("Integração com Testcontainers PostgreSQL: autoriza pagamento com Flyway e PostgreSQL real")
    void shouldAuthorizePaymentWithPostgresContainer() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentAuthorizationRequestDto request = PaymentAuthorizationRequestDto.builder()
                .accountId(UUID.randomUUID())
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("250.00"))
                .currency("BRL")
                .paymentMethod("CREDIT_CARD")
                .cardToken("tok_visa_1234_sandbox")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.amount", is(250.00)));

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(idempotencyRecordRepository.findById(idempotencyKey)).isPresent();
    }
}
