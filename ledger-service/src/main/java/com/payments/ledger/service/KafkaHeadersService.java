package com.payments.ledger.service;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class KafkaHeadersService implements TextMapGetter<Headers> {

    @Override
    public Iterable<String> keys(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map(Header::key)
                .collect(Collectors.toList());
    }

    @Override
    public String get(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}