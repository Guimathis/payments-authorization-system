package com.payments.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiConfigurationTest {

    @Autowired
    private SwaggerUiConfigParameters swaggerUiConfigParameters;

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Deve verificar a configuração do Swagger UI e as rotas carregadas")
    void testOpenApiConfiguration() {
        assertThat(swaggerUiConfigParameters).isNotNull();
        assertThat(swaggerUiConfigParameters.getUrls()).isNotEmpty();
        assertThat(swaggerUiConfigParameters.getUrls()).anyMatch(u -> "authorization-service".equals(u.getName()));
        assertThat(swaggerUiConfigParameters.getUrls()).anyMatch(u -> "antifraud-service".equals(u.getName()));

        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).anyMatch(r -> "authorization-service".equals(r.getId()));
        assertThat(routes).anyMatch(r -> "antifraud-service".equals(r.getId()));
    }

    @Test
    @DisplayName("Deve verificar o endpoint /v3/api-docs/swagger-config")
    void testSwaggerConfigEndpoint() {
        webTestClient.get()
                .uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls").isArray()
                .jsonPath("$.urls[?(@.name == 'authorization-service')].url").isEqualTo("/authorization-service/v3/api/docs")
                .jsonPath("$.urls[?(@.name == 'antifraud-service')].url").isEqualTo("/antifraud-service/v3/api/docs");
    }
}
