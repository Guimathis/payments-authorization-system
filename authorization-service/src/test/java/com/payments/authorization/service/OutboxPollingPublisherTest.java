package com.payments.authorization.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPollingPublisherTest {

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private OutboxPollingPublisher publisher;

    @Test
    @DisplayName("Deve invocar publishPendingEvents ao executar polling")
    void shouldInvokePublishPendingEvents() {
        when(outboxService.publishPendingEvents()).thenReturn(3);

        publisher.pollAndPublish();

        verify(outboxService).publishPendingEvents();
    }
}
