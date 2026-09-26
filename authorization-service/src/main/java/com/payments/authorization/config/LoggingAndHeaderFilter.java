package com.payments.authorization.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoggingAndHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingAndHeaderFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        try {
            // Executa a cadeia (outros filtros e o controller)
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("URI: {} {} - Status: {} ({} ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);

            // Adiciona cabeçalho personalizado na resposta
            response.addHeader("X-Response-Time-Ms", String.valueOf(duration));
        }
    }

    // Opcional: define rotas que devem ignorar este filtro
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/actuator");
    }
}