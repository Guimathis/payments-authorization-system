package com.payments.authorization.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenTelemetryService {
    private final OpenTelemetry openTelemetry;

    public Map<String, String> captureTraceContext() {
        Map<String, String> trace_headers  = new HashMap<>();
        openTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(Context.current(), trace_headers , (map, key, value) -> map.put(key, value));
        return trace_headers ;
    }

    public Context extractTraceContext(Map<String, String> savedTraceContext) {
        return openTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.root(), savedTraceContext, new TextMapGetter<Map<String, String>>() {
                    @Override public Iterable<String> keys(Map<String, String> c) { return c.keySet(); }
                    @Override public String get(Map<String, String> c, String key) { return c.get(key); }
                });
    }

}
