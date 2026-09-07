package com.payments.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ledger Service API")
                        .description("Serviço de Livro Razão Contábil, Gestão de Contas e Lançamentos de Débito/Crédito.")
                        .version("1.0.0"))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("API Gateway"),
                        new Server().url("http://localhost:8083").description("Servidor Local Direto")
                ));
    }
}
