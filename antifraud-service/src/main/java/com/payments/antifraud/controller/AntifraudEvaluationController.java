package com.payments.antifraud.controller;

import com.payments.antifraud.dto.AntifraudEvaluationRequestDto;
import com.payments.antifraud.dto.AntifraudEvaluationResponseDto;
import com.payments.antifraud.service.AntifraudEvaluationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/antifraud/evaluations")
@RequiredArgsConstructor
@Tag(name = "Antifraude", description = "Operações para avaliação e análise de risco de transações")
public class AntifraudEvaluationController {

    private final AntifraudEvaluationService antifraudEvaluationService;

    @Operation(
            summary = "Avaliar risco da transação",
            description = "Executa a análise de risco de pagamento determinística e retorna recomendação de aprovação ou rejeição."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Avaliação realizada com sucesso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AntifraudEvaluationResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados da requisição inválidos",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno no servidor de antifraude",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    public ResponseEntity<AntifraudEvaluationResponseDto> evaluate(
            @Parameter(description = "Latência simulada em milissegundos para testes de resiliência", example = "500")
            @RequestHeader(value = "X-Simulate-Delay-Ms", required = false) Long delayMs,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payload com dados da transação para avaliação de fraude",
                    required = true
            )
            @RequestBody @Valid AntifraudEvaluationRequestDto requestDto) {

        AntifraudEvaluationResponseDto response = antifraudEvaluationService.evaluate(requestDto, delayMs);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
