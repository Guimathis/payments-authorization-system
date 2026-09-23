package com.payments.authorization.outbox;

import com.payments.authorization.entity.OutboxEvent;

public record PublishResult(OutboxEvent event, Throwable error) {
    public boolean isSuccess() {
        return error == null;
    }

    public boolean isOk() {
        return isSuccess();
    }
}