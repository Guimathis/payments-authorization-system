package com.payments.ledger.exception;

public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s com identificador '%s' não foi encontrado.", resourceName, identifier));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
