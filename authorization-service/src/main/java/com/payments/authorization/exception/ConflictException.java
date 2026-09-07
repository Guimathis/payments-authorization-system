package com.payments.authorization.exception;

public class ConflictException extends DomainException {
    public ConflictException(String message) {
        super(message);
    }
}
