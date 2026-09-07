package com.payments.antifraud.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resultado da avaliação de risco antifraude")
public class AntifraudEvaluationResponseDto implements Serializable {

    @Schema(description = "Identificador único da avaliação realizada", example = "5fa85f64-5717-4562-b3fc-2c963f66afe1")
    private UUID evaluationId;

    @Schema(description = "Recomendação do motor antifraude", example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"})
    private String recommendation;

    @Schema(description = "Score de risco calculado (0 a 100)", example = "15")
    private Integer riskScore;

    @Schema(description = "Data e hora do momento da avaliação", example = "2026-09-05T19:30:00.120Z")
    private Instant evaluatedAt;
}
