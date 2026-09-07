package com.payments.authorization.controller;

import com.payments.authorization.dto.PaymentAuthorizationRequestDto;
import com.payments.authorization.dto.PaymentAuthorizationResponseDto;
import com.payments.authorization.dto.PaymentResult;
import com.payments.authorization.service.PaymentAuthorizationService;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Pagamentos", description = "Operações para autorização e liquidação de transações de pagamento")
public class PaymentAuthorizationController {

    private final PaymentAuthorizationService paymentAuthorizationService;

    @Operation(
            summary = "Autorizar pagamento",
            description = "Submete uma transação financeira para autorização com garantia estrita de idempotência e análise antifraude."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Pagamento autorizado com sucesso pela primeira vez",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentAuthorizationResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "200",
                    description = "Replay idempotente (mesma Idempotency-Key reenviada após conclusão)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentAuthorizationResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Dados da requisição inválidos ou header obrigatório ausente",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Requisição concorrente com a mesma Idempotency-Key já em processamento",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erro interno no servidor de autorização",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    public ResponseEntity<PaymentAuthorizationResponseDto> authorizePayment(
            @Parameter(
                    description = "Chave única de idempotência no formato UUID para evitar duplicações de cobrança",
                    required = true,
                    example = "8f3d1b2a-4c5e-49b8-a123-9c8e7b6a5d4f"
            )
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payload contendo os dados da transação de pagamento a ser autorizada",
                    required = true
            )
            @RequestBody @Valid PaymentAuthorizationRequestDto requestDto) {

        PaymentResult result = paymentAuthorizationService.processPayment(idempotencyKey, requestDto);
        HttpStatus status = result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.responseDto());
    }
}
