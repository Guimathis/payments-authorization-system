package com.payments.authorization.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class AntifraudEvaluationRequestDto implements Serializable {
    @JsonAlias({"accountId", "account_id"})
    private UUID accountId;
    private BigDecimal amount;
    @JsonAlias({"paymentMethod", "payment_method"})
    private String paymentMethod;
    private Boolean suspicious;
}
