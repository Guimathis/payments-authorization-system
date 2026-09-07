package com.payments.ledger.integration;

import com.payments.ledger.dto.AccountResponseDto;
import com.payments.ledger.dto.CreateAccountRequestDto;
import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Test
    @DisplayName("Cenário de Integração: Criar conta com saldo 500 e processar débito de 100 via evento, resultando em saldo 400")
    void shouldCreateAccountAndProcessDebitCorrectly() {
        UUID accountId = UUID.randomUUID();
        CreateAccountRequestDto createDto = CreateAccountRequestDto.builder()
                .id(accountId)
                .ownerName("Test User")
                .balance(new BigDecimal("500.00"))
                .currency("BRL")
                .build();

        ledgerService.createAccount(createDto);

        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        ledgerService.processPaymentDebit(event);

        AccountResponseDto updated = ledgerService.getAccountById(accountId);
        assertThat(updated.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
    }
}
