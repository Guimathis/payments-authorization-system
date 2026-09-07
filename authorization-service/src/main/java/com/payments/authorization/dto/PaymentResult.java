package com.payments.authorization.dto;

public record PaymentResult(
        PaymentAuthorizationResponseDto responseDto,
        boolean isReplay
) {}
