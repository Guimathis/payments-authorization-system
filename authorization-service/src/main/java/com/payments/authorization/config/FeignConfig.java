package com.payments.authorization.config;

import feign.Logger;
import feign.Request;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Slf4j
@Configuration
public class FeignConfig {

    @Bean
    public Logger feignLogger() {
        return new CustomFeignLogger();
    }

    public static class CustomFeignLogger extends Logger {

        @Override
        protected void log(String configKey, String format, Object... args) {
            if (log.isDebugEnabled()) {
                log.debug(String.format(methodTag(configKey) + format, args));
            }
        }

        @Override
        protected void logRequest(String configKey, Level logLevel, Request request) {
            if (log.isDebugEnabled()) {
                super.logRequest(configKey, logLevel, request);
            }
        }

        @Override
        protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
            if (response.status() >= 400) {
                log.error("{} <--- HTTP {} falha na comunicação com serviço externo ({}ms)",
                        methodTag(configKey), response.status(), elapsedTime);
            }
            return super.logAndRebufferResponse(configKey, logLevel, response, elapsedTime);
        }

        @Override
        protected IOException logIOException(String configKey, Level logLevel, IOException ioe, long elapsedTime) {
            log.error("{} <--- Falha na chamada externa ({}ms). Causa: {}",
                    methodTag(configKey), elapsedTime, ioe.getMessage(), ioe);
            return ioe;
        }
    }
}
