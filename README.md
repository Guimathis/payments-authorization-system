# Sistema de Autorização de Pagamentos com Antifraude

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot 3.3.5](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud 2023.0.3](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-Circuit%20Breaker-red.svg)](https://resilience4j.readme.io/)

Plataforma distribuída de autorização de pagamentos financeiros de alta resiliência, com garantia estrita de **Idempotência**, **Tolerância a Falhas com Circuit Breaker (Resilience4j)**, **API Gateway Reativo** e modelagem em conformidade com o Nível 2 do Modelo de Maturidade de Richardson.

---

## 🏛️ Arquitetura — Fase 1 (Núcleo Síncrono do Domínio)

```
[ Cliente / Consumer ]
        │
        │ POST /api/v1/payments (Header: Idempotency-Key)
        ▼
┌───────────────────────────────┐
│        gateway-service        │  (Spring Cloud Gateway :8080)
│   (Roteamento + Forwarding)   │
└──────────────┬────────────────┘
               │
               ▼
┌───────────────────────────────┐
│     authorization-service     │  (Spring Boot :8081)
│  - Idempotency State Machine  │
│  - PostgreSQL 16 (Flyway)     │
│  - Feign Client + Resilience4j│
└──────────────┬────────────────┘
               │
               │ POST /api/v1/antifraud/evaluations (Timeout 800ms + Retry + Fallback)
               ▼
┌───────────────────────────────┐
│       antifraud-service       │  (Spring Boot :8082)
│  - Avaliação Determinística   │
│  - Simulação de Latência      │
└───────────────────────────────┘
```

---

## 🚀 Como Executar

### Pré-requisitos
- Java 21 LTS
- Maven 3.9+ (ou utilizar `./mvnw`)
- Docker & Docker Compose

### Opção 1: Executando tudo via Docker Compose

```bash
docker compose up --build
```

Serviços iniciados:
- `postgres-auth`: PostgreSQL 16 na porta `5432`
- `antifraud-service`: porta `8082`
- `authorization-service`: porta `8081`
- `gateway-service`: porta `8080`

### Opção 2: Executando localmente via Maven

1. Suba o banco PostgreSQL:
   ```bash
   docker compose up -d postgres-auth
   ```
2. Inicie os microsserviços em terminais separados:
   ```bash
   # Terminal 1: Antifraude
   ./mvnw spring-boot:run -pl antifraud-service

   # Terminal 2: Autorizador
   ./mvnw spring-boot:run -pl authorization-service

   # Terminal 3: Gateway
   ./mvnw spring-boot:run -pl gateway-service
   ```

---

## 🧪 Suíte de Testes Automatizados

Para rodar todos os testes de todos os microsserviços do monorepo:

```bash
./mvnw test
```

### Cenários Cobertos nos Testes:
- **Garantia de Idempotência:**
  - 1ª requisição: retorna `201 Created` e grava transação no banco.
  - 2ª e 3ª requisições (replay): retornam `200 OK` com o exato mesmo corpo da resposta sem gerar novas linhas na tabela `transactions`.
  - Requisição concorrente com status `PROCESSING`: retorna `409 Conflict` (`RFC 7807 ProblemDetail`).
- **Resiliência e Circuit Breaker:**
  - Queda ou timeout no `antifraud-service` aciona o fallback contingencial sem quebrar o autorizador (sem `500 Internal Server Error`).
  - Valores $\le$ R$ 500,00 aprovados em contingência.
  - Valores $>$ R$ 500,00 rejeitados preventivamente.
- **Validação de Payload:**
  - Validações Bean Validation e header `Idempotency-Key` ausente retornando `400 Bad Request`.
- **Gateway:**
  - Registro e resolução de rotas para autorizador e antifraude.

---

## 📡 Exemplos de Uso da API

### 1. Autorizar Pagamento (via Gateway :8080)

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 8f3d1b2a-4c5e-49b8-a123-9c8e7b6a5d4f" \
  -d '{
    "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "merchantId": "7ca85f64-5717-4562-b3fc-2c963f66afb7",
    "amount": 250.00,
    "currency": "BRL",
    "paymentMethod": "CREDIT_CARD",
    "cardToken": "tok_visa_1234_sandbox"
  }'
```

**Resposta (HTTP 201 Created na primeira chamada / HTTP 200 OK nos replays):**
```json
{
  "paymentId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "status": "APPROVED",
  "authorizationCode": "AUTH-4F9A1B2C",
  "amount": 250.00,
  "currency": "BRL",
  "createdAt": "2026-09-06T11:05:00Z"
}
```

### 2. Avaliação de Fraude Direta (Antifraude :8082)

```bash
curl -X POST http://localhost:8082/api/v1/antifraud/evaluations \
  -H "Content-Type: application/json" \
  -H "X-Simulate-Delay-Ms: 200" \
  -d '{
    "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "amount": 250.00,
    "paymentMethod": "CREDIT_CARD",
    "suspicious": false
  }'
```

**Resposta (HTTP 200 OK):**
```json
{
  "evaluationId": "5fa85f64-5717-4562-b3fc-2c963f66afe1",
  "recommendation": "APPROVED",
  "riskScore": 15,
  "evaluatedAt": "2026-09-06T11:05:00.120Z"
}
```

---

## 📚 Documentação Swagger / OpenAPI

- **Gateway (Swagger UI Centralizado / Unificado):** `http://localhost:8080/swagger-ui.html`
  - Permite visualizar e testar interativamente a documentação de todos os microsserviços via seletor (*Select a definition*):
    - `authorization-service` (`/v3/api-docs/authorization-service`)
    - `antifraud-service` (`/v3/api-docs/antifraud-service`)
- **Autorizador (Direto):** `http://localhost:8081/swagger-ui.html`
- **Antifraude (Direto):** `http://localhost:8082/swagger-ui.html`
