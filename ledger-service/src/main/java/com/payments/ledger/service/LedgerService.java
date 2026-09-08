package com.payments.ledger.service;

import com.payments.ledger.dto.AccountResponseDto;
import com.payments.ledger.dto.CreateAccountRequestDto;
import com.payments.ledger.entity.Account;
import com.payments.ledger.entity.LedgerEntry;
import com.payments.ledger.entity.OperationType;
import com.payments.ledger.event.PaymentAuthorizedEvent;
import com.payments.ledger.exception.ResourceNotFoundException;
import com.payments.ledger.repository.AccountRepository;
import com.payments.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final com.payments.ledger.repository.ProcessedEventRepository processedEventRepository;

    @org.springframework.beans.factory.annotation.Value("${app.ledger.strict-balance-check:false}")
    private boolean strictBalanceCheck;

    @Transactional
    public void processPaymentDebit(PaymentAuthorizedEvent event) {
        log.info("Processando débito contábil para pagamento {}. Conta: {}, Valor: {} {}",
                event.getPaymentId(), event.getAccountId(), event.getAmount(), event.getCurrency());

        // 1. Verificação de idempotência: se o evento já foi processado, ignora
        if (event.getEventId() != null && processedEventRepository.existsById(event.getEventId())) {
            log.info("Evento {} já processado anteriormente. Ignorando para garantir idempotência contábil.", event.getEventId());
            return;
        }

        UUID accountId = event.getAccountId();
        Account account = accountRepository.findById(accountId)
                .orElseGet(() -> {
                    log.warn("Conta {} não encontrada. Criando registro contábil automático com saldo zero.", accountId);
                    Account newAccount = Account.builder()
                            .id(accountId)
                            .ownerName("Conta " + accountId)
                            .balance(BigDecimal.ZERO)
                            .currency(event.getCurrency() != null ? event.getCurrency() : "BRL")
                            .updatedAt(Instant.now())
                            .build();
                    return accountRepository.save(newAccount);
                });

        // 2. Regra estrita de validação de saldo (se ativada)
        if (strictBalanceCheck && account.getBalance().compareTo(event.getAmount()) < 0) {
            log.error("Saldo insuficiente na conta {} para débito de {}. Saldo atual: {}",
                    accountId, event.getAmount(), account.getBalance());
            throw new com.payments.ledger.exception.BusinessRuleException(
                    String.format("Saldo insuficiente na conta %s. Saldo atual: %s, Débito solicitado: %s",
                            accountId, account.getBalance(), event.getAmount()));
        }

        BigDecimal newBalance = account.getBalance().subtract(event.getAmount());
        account.setBalance(newBalance);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        LedgerEntry entry = LedgerEntry.builder()
                .accountId(accountId)
                .paymentId(event.getPaymentId())
                .operationType(OperationType.DEBIT)
                .amount(event.getAmount())
                .createdAt(Instant.now())
                .build();
        ledgerEntryRepository.save(entry);

        // 3. Marca evento como processado para garantir idempotência
        if (event.getEventId() != null) {
            com.payments.ledger.entity.ProcessedEvent processedEvent = com.payments.ledger.entity.ProcessedEvent.builder()
                    .eventId(event.getEventId())
                    .processedAt(Instant.now())
                    .build();
            processedEventRepository.save(processedEvent);
        }

        log.info("Débito contábil concluído com sucesso. Conta: {}, Novo Saldo: {} {}",
                accountId, newBalance, account.getCurrency());
    }

    @Transactional
    public AccountResponseDto createAccount(CreateAccountRequestDto request) {
        UUID accountId = request.getId() != null ? request.getId() : UUID.randomUUID();

        Account account = Account.builder()
                .id(accountId)
                .ownerName(request.getOwnerName())
                .balance(request.getBalance())
                .currency(request.getCurrency())
                .updatedAt(Instant.now())
                .build();

        Account saved = accountRepository.save(account);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponseDto getAccountById(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Conta", accountId));
        return mapToResponse(account);
    }

    private AccountResponseDto mapToResponse(Account account) {
        return AccountResponseDto.builder()
                .id(account.getId())
                .ownerName(account.getOwnerName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
