package com.payments.ledger.service;

import com.payments.ledger.dto.AccountResponseDto;
import com.payments.ledger.dto.CreateAccountRequestDto;
import com.payments.ledger.entity.Account;
import com.payments.ledger.entity.LedgerEntry;
import com.payments.ledger.entity.OperationType;
import com.payments.ledger.entity.ProcessedEvent;
import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.exception.BusinessRuleException;
import com.payments.ledger.exception.ResourceNotFoundException;
import com.payments.ledger.repository.AccountRepository;
import com.payments.ledger.repository.LedgerEntryRepository;
import com.payments.ledger.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private LedgerService ledgerService;

    private UUID accountId;
    private Account account;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = Account.builder()
                .id(accountId)
                .ownerName("João da Silva")
                .balance(new BigDecimal("500.00"))
                .currency("BRL")
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Critério de Aceitação: Deve debitar R$ 100 de uma conta com saldo de R$ 500, resultando em saldo de R$ 400")
    void shouldDebitAmountFromExistingAccountCorrectly() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(eventId)
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        ledgerService.processPaymentDebit(event);

        // Verifica que o saldo foi atualizado para 400.00
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
        verify(accountRepository).save(account);

        // Verifica o registro contábil
        ArgumentCaptor<LedgerEntry> entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(entryCaptor.capture());
        LedgerEntry entry = entryCaptor.getValue();
        assertThat(entry.getAccountId()).isEqualTo(accountId);
        assertThat(entry.getOperationType()).isEqualTo(OperationType.DEBIT);
        assertThat(entry.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(entry.getPaymentId()).isEqualTo(event.getPaymentId());

        // Verifica que o evento foi registrado como processado
        ArgumentCaptor<ProcessedEvent> processedCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(processedCaptor.capture());
        assertThat(processedCaptor.getValue().getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("Idempotência: Deve ignorar evento duplicado sem alterar saldo nem criar novo ledger entry")
    void shouldIgnoreDuplicateEventWhenAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(eventId)
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        ledgerService.processPaymentDebit(event);

        verify(accountRepository, never()).findById(any());
        verify(accountRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Regra Estrita: Deve lançar BusinessRuleException quando regra estrita estiver ativa e saldo for insuficiente")
    void shouldThrowBusinessRuleExceptionWhenStrictBalanceCheckEnabledAndInsufficientBalance() {
        ReflectionTestUtils.setField(ledgerService, "strictBalanceCheck", true);

        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsById(eventId)).thenReturn(false);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account)); // Saldo é 500.00

        PaymentAuthorizedEvent event = PaymentAuthorizedEvent.builder()
                .eventId(eventId)
                .eventType("PAYMENT_AUTHORIZED")
                .paymentId(UUID.randomUUID())
                .accountId(accountId)
                .merchantId(UUID.randomUUID())
                .amount(new BigDecimal("600.00")) // Débito maior que saldo
                .currency("BRL")
                .timestamp(Instant.now())
                .build();

        assertThatThrownBy(() -> ledgerService.processPaymentDebit(event))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Saldo insuficiente");

        verify(ledgerEntryRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve criar nova conta e retornar dados cadastrados")
    void shouldCreateAccountSuccessfully() {
        CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                .id(accountId)
                .ownerName("Maria Souza")
                .balance(new BigDecimal("1000.00"))
                .currency("BRL")
                .build();

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponseDto response = ledgerService.createAccount(request);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getOwnerName()).isEqualTo("Maria Souza");
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(response.getCurrency()).isEqualTo("BRL");
    }

    @Test
    @DisplayName("Deve lançar ResourceNotFoundException ao buscar conta inexistente")
    void shouldThrowNotFoundWhenAccountDoesNotExist() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ledgerService.getAccountById(accountId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }
}
