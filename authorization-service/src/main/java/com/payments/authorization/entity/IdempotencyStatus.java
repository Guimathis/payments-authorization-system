package com.payments.authorization.entity;

public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
