package com.payments.antifraud.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@Schema(description = "Dados para requisição de avaliação de risco antifraude")
public class AntifraudEvaluationRequestDto implements Serializable {

    @NotNull(message = "O ID da conta é obrigatório")
    @JsonAlias({"accountId", "account_id"})
    @Schema(description = "Identificador único da conta solicitante", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID accountId;

    @NotNull(message = "O valor da transação é obrigatório")
    @Positive(message = "O valor da transação deve ser maior que zero")
    @Schema(description = "Valor monetário da transação a ser avaliada", example = "250.00")
    private BigDecimal amount;

    @NotBlank(message = "O método de pagamento é obrigatório")
    @JsonAlias({"paymentMethod", "payment_method"})
    @Schema(description = "Método de pagamento utilizado", example = "CREDIT_CARD", allowableValues = {"CREDIT_CARD", "DEBIT_CARD", "PIX"})
    private String paymentMethod;

    @Schema(description = "Sinalizador booleano de transação suspeita (para fins de simulação de risco)", example = "false")
    private Boolean suspicious;
}
