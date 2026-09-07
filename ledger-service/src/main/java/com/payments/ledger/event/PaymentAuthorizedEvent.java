package com.payments.ledger.event;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class PaymentAuthorizedEvent implements Serializable {

    @JsonProperty("event_id")
    private UUID eventId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("payment_id")
    private UUID paymentId;

    @JsonProperty("account_id")
    private UUID accountId;

    @JsonProperty("merchant_id")
    private UUID merchantId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("timestamp")
    private Instant timestamp;
}
