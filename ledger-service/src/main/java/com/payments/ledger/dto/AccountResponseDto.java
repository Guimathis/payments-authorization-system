package com.payments.ledger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
@Schema(description = "Detalhes da conta contábil")
public class AccountResponseDto implements Serializable {

    @Schema(description = "Identificador único da conta", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("owner_name")
    @Schema(description = "Nome do titular", example = "João da Silva")
    private String ownerName;

    @Schema(description = "Saldo atual da conta", example = "400.00")
    private BigDecimal balance;

    @Schema(description = "Código ISO da moeda", example = "BRL")
    private String currency;

    @JsonProperty("updated_at")
    @Schema(description = "Data e hora da última atualização", example = "2026-09-07T16:00:00Z")
    private Instant updatedAt;
}
