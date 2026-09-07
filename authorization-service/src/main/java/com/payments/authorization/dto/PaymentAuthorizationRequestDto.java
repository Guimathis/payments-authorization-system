package com.payments.authorization.dto;

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
@Schema(description = "Dados para solicitação de autorização de pagamento")
public class PaymentAuthorizationRequestDto implements Serializable {

    @NotNull(message = "O ID da conta é obrigatório")
    @JsonAlias({"accountId", "account_id"})
    @Schema(description = "Identificador único da conta de origem", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID accountId;

    @NotNull(message = "O ID do recebedor (merchant) é obrigatório")
    @JsonAlias({"merchantId", "merchant_id"})
    @Schema(description = "Identificador único do estabelecimento comercial", example = "7ca85f64-5717-4562-b3fc-2c963f66afb7")
    private UUID merchantId;

    @NotNull(message = "O valor do pagamento é obrigatório")
    @Positive(message = "O valor do pagamento deve ser maior que zero")
    @Schema(description = "Valor monetário a ser debitado/autorizado", example = "250.00")
    private BigDecimal amount;

    @NotBlank(message = "A moeda é obrigatória")
    @Schema(description = "Código ISO 4217 da moeda", example = "BRL")
    private String currency;

    @NotBlank(message = "O método de pagamento é obrigatório")
    @JsonAlias({"paymentMethod", "payment_method"})
    @Schema(description = "Método de pagamento a ser utilizado", example = "CREDIT_CARD", allowableValues = {"CREDIT_CARD", "DEBIT_CARD", "PIX"})
    private String paymentMethod;

    @NotBlank(message = "O token do cartão é obrigatório")
    @JsonAlias({"cardToken", "card_token"})
    @Schema(description = "Token seguro representativo dos dados do cartão", example = "tok_visa_1234_sandbox")
    private String cardToken;
}
