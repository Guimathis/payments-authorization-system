package com.payments.authorization.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resultado da solicitação de autorização de pagamento")
public class PaymentAuthorizationResponseDto implements Serializable {

    @JsonAlias({"paymentId", "payment_id"})
    @Schema(description = "Identificador único do pagamento gerado", example = "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    private UUID paymentId;

    @Schema(description = "Status final da autorização", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"})
    private String status;

    @JsonAlias({"authorizationCode", "authorization_code"})
    @Schema(description = "Código de autorização gerado em caso de aprovação", example = "AUTH-892147")
    private String authorizationCode;

    @Schema(description = "Valor autorizado", example = "250.00")
    private BigDecimal amount;

    @Schema(description = "Moeda da transação", example = "BRL")
    private String currency;

    @JsonAlias({"createdAt", "created_at"})
    @Schema(description = "Data e hora de criação da transação", example = "2026-09-05T19:30:00Z")
    private Instant createdAt;
}
