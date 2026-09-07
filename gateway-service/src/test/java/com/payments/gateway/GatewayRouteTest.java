package com.payments.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRouteTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("Deve carregar o contexto e registrar as rotas do gateway corretamente")
    void shouldLoadRoutesCorrectly() {
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        assertThat(routes).anyMatch(route -> route.getId().equals("authorization-payments-route"));
        assertThat(routes).anyMatch(route -> route.getId().equals("antifraud-evaluations-route"));
        assertThat(routes).anyMatch(route -> route.getId().equals("authorization-service"));
        assertThat(routes).anyMatch(route -> route.getId().equals("antifraud-service"));
    }
}
