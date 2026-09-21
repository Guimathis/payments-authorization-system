package com.payments.authorization.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.common.errors.*;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.function.Predicate;

@Component
public class OutboxExceptionClassifier {

    public enum ErrorCategory {
        /** Rede/broker fora: mantém PENDING, pausa o job, não conta tentativa. */
        TRANSIENT_INFRASTRUCTURE,
        /** Config/credencial/tópico: mantém PENDING, pausa o job, não conta tentativa, ALERTA. */
        SYSTEMIC_CONFIGURATION,
        /** Culpa do próprio evento (payload): vai direto para FAILED. */
        FATAL_POISON_PILL,
        /** Não reconhecido: conta tentativa; FAILED ao estourar o limite. */
        UNKNOWN
    }

    public ErrorCategory classify(Throwable throwable) {
        List<Throwable> chain = causeChain(throwable);

        // A ordem importa: JsonProcessingException é um IOException,
        // então poison precisa vir antes de transient.
        if (matches(chain, this::isPoison))    return ErrorCategory.FATAL_POISON_PILL;
        if (matches(chain, this::isSystemic))  return ErrorCategory.SYSTEMIC_CONFIGURATION;
        if (matches(chain, this::isTransient)) return ErrorCategory.TRANSIENT_INFRASTRUCTURE;
        return ErrorCategory.UNKNOWN;
    }

    private boolean isPoison(Throwable t) {
        return t instanceof RecordTooLargeException
                || t instanceof SerializationException
                || t instanceof JsonProcessingException;
    }

    private boolean isSystemic(Throwable t) {
        // UnknownTopicOrPartitionException é RetriableException: precisa ser avaliada antes de isTransient
        return t instanceof AuthenticationException
                || t instanceof AuthorizationException
                || t instanceof InvalidTopicException
                || t instanceof UnknownTopicOrPartitionException;
    }

    private boolean isTransient(Throwable t) {
        return t instanceof RetriableException // inclui o TimeoutException do Kafka
                || t instanceof java.util.concurrent.TimeoutException
                || t instanceof SocketTimeoutException
                || t instanceof ConnectException;
    }

    private boolean matches(List<Throwable> chain, Predicate<Throwable> p) {
        return chain.stream().anyMatch(p);
    }

    private List<Throwable> causeChain(Throwable t) {
        List<Throwable> chain = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (t != null && seen.add(t)) {
            chain.add(t);
            t = t.getCause();
        }
        return chain;
    }
}