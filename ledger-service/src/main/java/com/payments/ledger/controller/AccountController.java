package com.payments.ledger.controller;

import com.payments.ledger.dto.AccountResponseDto;
import com.payments.ledger.dto.CreateAccountRequestDto;
import com.payments.ledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Contas", description = "Operações para gerenciamento de contas contábeis e saldos")
public class AccountController {

    private final LedgerService ledgerService;

    @Operation(
            summary = "Criar nova conta contábil",
            description = "Cadastra uma conta bancária com saldo inicial para movimentações do ledger."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Conta criada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados da requisição inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno no servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    public ResponseEntity<AccountResponseDto> createAccount(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payload com dados para abertura da conta",
                    required = true
            )
            @Valid @RequestBody CreateAccountRequestDto request) {
        AccountResponseDto response = ledgerService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Obter conta por ID",
            description = "Busca os dados e o saldo atualizado de uma conta pelo seu identificador único."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Conta localizada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conta não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno no servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDto> getAccountById(
            @Parameter(description = "Identificador único da conta", required = true, example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            @PathVariable("id") UUID id) {
        AccountResponseDto response = ledgerService.getAccountById(id);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
