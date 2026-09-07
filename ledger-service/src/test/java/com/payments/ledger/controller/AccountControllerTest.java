package com.payments.ledger.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.ledger.dto.AccountResponseDto;
import com.payments.ledger.dto.CreateAccountRequestDto;
import com.payments.ledger.exception.ResourceNotFoundException;
import com.payments.ledger.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LedgerService ledgerService;

    @Test
    @DisplayName("Deve criar conta com sucesso retornando status 201")
    void shouldCreateAccountSuccessfully() throws Exception {
        UUID accountId = UUID.randomUUID();
        CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                .id(accountId)
                .ownerName("Carlos Lima")
                .balance(new BigDecimal("500.00"))
                .currency("BRL")
                .build();

        AccountResponseDto response = AccountResponseDto.builder()
                .id(accountId)
                .ownerName("Carlos Lima")
                .balance(new BigDecimal("500.00"))
                .currency("BRL")
                .updatedAt(Instant.now())
                .build();

        when(ledgerService.createAccount(any(CreateAccountRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.owner_name").value("Carlos Lima"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request ao tentar criar conta com saldo negativo")
    void shouldReturnBadRequestWhenBalanceIsNegative() throws Exception {
        CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                .ownerName("Carlos Lima")
                .balance(new BigDecimal("-100.00"))
                .currency("BRL")
                .build();

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("Deve obter conta existente retornando status 200")
    void shouldGetExistingAccount() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountResponseDto response = AccountResponseDto.builder()
                .id(accountId)
                .ownerName("Carlos Lima")
                .balance(new BigDecimal("400.00"))
                .currency("BRL")
                .updatedAt(Instant.now())
                .build();

        when(ledgerService.getAccountById(accountId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId.toString()))
                .andExpect(jsonPath("$.balance").value(400.00));
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found quando a conta não existir")
    void shouldReturnNotFoundWhenAccountDoesNotExist() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(ledgerService.getAccountById(accountId))
                .thenThrow(new ResourceNotFoundException("Conta", accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }
}
