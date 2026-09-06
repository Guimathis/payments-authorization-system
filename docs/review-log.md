# Review Log & Retrospectiva — Sistema de Autorização de Pagamentos

## 1. Multi-Role Review (Revisão Multidisciplinar)

* **🎯 Produto:**
  - [x] O problema central (cobranças duplicadas, falhas em cascata e dual-write) está formulado de forma inequívoca.
  - [x] O escopo foi reajustado com precisão: 5 fases técnicas ativas, excluindo a divulgação/IA.
  - [x] Métricas de sucesso são mensuráveis (latência p95 $\le$ 200ms, 100% de idempotência, 0 eventos perdidos no Outbox).
* **🎨 Design / DX (Developer Experience):**
  - [x] Como o sistema é estritamente backend, a experiência foi focada na DX: contratos OpenAPI documentados, códigos HTTP semânticos (201, 200, 400, 409, 422, 500) e mensagens de erro padronizadas via RFC 7807 (`ProblemDetail`).
  - [x] Cenários de resposta de erro detalhados e mapeados para cada exceção de domínio.
* **🔧 Engenharia:**
  - [x] Contratos de API síncronos e eventos assíncronos no Kafka especificados com schemas e exemplos JSON.
  - [x] Estratégia de Transactional Outbox detalhada via Polling Publisher (`@Scheduled` + `FOR UPDATE SKIP LOCKED`).
  - [x] Resiliência síncrona com Resilience4j (timeouts, retries e circuit breaker) e tolerância a falhas assíncrona com Dead Letter Queue (DLQ).
  - [x] Rastreabilidade ponta a ponta com OpenTelemetry, Tempo, Prometheus e Grafana.

---

## 2. Retrospectiva da Elaboração de Requisitos

* **Seções mais sintéticas:**
  - A modelagem de usuários/clientes finais no SRD/PRD é predominantemente orientada a IDs (`account_id`, `merchant_id`), dado que o sistema opera como infraestrutura intermediária de pagamento (B2B/Core Banking).
* **Perguntas que poderiam ter sido antecipadas:**
  - Estratégia de expiração/limpeza da tabela `idempotency_records` (TTL de chaves de idempotência, ex: 24h ou 7 dias) e particionamento da tabela `outbox_events` caso o volume em produção cresça significativamente. Para o contexto local de portfólio, a modelagem atual atende perfeitamente.
