package com.payments.authorization.client;

import com.payments.authorization.client.dto.AntifraudEvaluationRequestDto;
import com.payments.authorization.client.dto.AntifraudEvaluationResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "antifraud-service", url = "${antifraud.service.url:http://localhost:8082}")
public interface AntifraudClient {

    @PostMapping("/api/v1/antifraud/evaluations")
    AntifraudEvaluationResponseDto evaluate(
            @RequestHeader(value = "X-Simulate-Delay-Ms", required = false) Long delayMs,
            @RequestBody AntifraudEvaluationRequestDto request);
}
