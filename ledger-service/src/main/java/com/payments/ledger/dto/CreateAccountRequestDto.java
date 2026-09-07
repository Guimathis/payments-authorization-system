package com.payments.ledger.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Dados para cadastro de uma conta contábil")
public class CreateAccountRequestDto implements Serializable {

    @Schema(description = "Identificador único da conta (opcional, gerado se omitido)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID id;

    @JsonProperty("owner_name")
    @NotBlank(message = "O nome do titular é obrigatório")
    @Size(max = 150, message = "O nome do titular deve ter no máximo 150 caracteres")
    @Schema(description = "Nome do titular da conta", example = "João da Silva")
    private String ownerName;

    @NotNull(message = "O saldo inicial é obrigatório")
    @DecimalMin(value = "0.00", message = "O saldo inicial não pode ser negativo")
    @Schema(description = "Saldo inicial da conta", example = "500.00")
    private BigDecimal balance;

    @NotBlank(message = "A moeda é obrigatória")
    @Size(min = 3, max = 3, message = "A moeda deve conter exatamente 3 caracteres ISO (ex: BRL)")
    @Schema(description = "Código ISO da moeda", example = "BRL")
    private String currency;
}
