package com.payments.authorization.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.errors.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxExceptionClassifierTest {

    private final OutboxExceptionClassifier classifier = new OutboxExceptionClassifier();

    @Test
    @DisplayName("Deve classificar RecordTooLargeException, SerializationException e JsonProcessingException como FATAL_POISON_PILL")
    void shouldClassifyPoisonPills() {
        assertThat(classifier.classify(new RecordTooLargeException("Message too big")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.FATAL_POISON_PILL);

        assertThat(classifier.classify(new SerializationException("Failed to serialize")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.FATAL_POISON_PILL);

        JsonProcessingException jsonException = new JsonParseException(null, "Bad JSON format");
        assertThat(classifier.classify(jsonException))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.FATAL_POISON_PILL);

        // Caso encapsulado
        RuntimeException wrapped = new RuntimeException("Outer error", jsonException);
        assertThat(classifier.classify(wrapped))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.FATAL_POISON_PILL);
    }

    @Test
    @DisplayName("Deve classificar falhas de autenticação, autorização e tópicos inválidos como SYSTEMIC_CONFIGURATION")
    void shouldClassifySystemicConfigurationErrors() {
        assertThat(classifier.classify(new AuthenticationException("Invalid SASL credentials") {}))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.SYSTEMIC_CONFIGURATION);

        assertThat(classifier.classify(new AuthorizationException("Not authorized to write to topic") {}))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.SYSTEMIC_CONFIGURATION);

        assertThat(classifier.classify(new InvalidTopicException("Topic contains illegal characters")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.SYSTEMIC_CONFIGURATION);

        assertThat(classifier.classify(new UnknownTopicOrPartitionException("Topic does not exist")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.SYSTEMIC_CONFIGURATION);
    }

    @Test
    @DisplayName("Deve classificar erros de rede, timeout e RetriableException como TRANSIENT_INFRASTRUCTURE")
    void shouldClassifyTransientInfrastructureErrors() {
        assertThat(classifier.classify(new TimeoutException("Kafka request timed out")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.TRANSIENT_INFRASTRUCTURE);

        assertThat(classifier.classify(new DisconnectException("Connection to broker disconnected")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.TRANSIENT_INFRASTRUCTURE);

        assertThat(classifier.classify(new NetworkException("Network packet loss")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.TRANSIENT_INFRASTRUCTURE);

        assertThat(classifier.classify(new ConnectException("Connection refused")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.TRANSIENT_INFRASTRUCTURE);

        assertThat(classifier.classify(new SocketTimeoutException("Read timeout on socket")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.TRANSIENT_INFRASTRUCTURE);
    }

    @Test
    @DisplayName("Deve classificar erros não catalogados como UNKNOWN")
    void shouldClassifyUnknownErrors() {
        assertThat(classifier.classify(new NullPointerException("Null reference")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.UNKNOWN);

        assertThat(classifier.classify(new IllegalStateException("Wrong state")))
                .isEqualTo(OutboxExceptionClassifier.ErrorCategory.UNKNOWN);
    }
}
