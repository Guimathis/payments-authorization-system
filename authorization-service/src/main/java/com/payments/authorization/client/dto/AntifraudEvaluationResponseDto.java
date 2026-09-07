package com.payments.authorization.client.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class AntifraudEvaluationResponseDto implements Serializable {
    @JsonAlias({"evaluationId", "evaluation_id"})
    private UUID evaluationId;
    private String recommendation;
    @JsonAlias({"riskScore", "risk_score"})
    private Integer riskScore;
    @JsonAlias({"evaluatedAt", "evaluated_at"})
    private Instant evaluatedAt;
}
