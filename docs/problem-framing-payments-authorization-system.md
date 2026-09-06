# Problem Framing — Sistema de Autorização de Pagamentos com Antifraude

## Problem Statement

Engenheiros de software e sistemas de checkout/e-commerce enfrentam o desafio crítico de processar autorizações de pagamentos em tempo real garantindo resiliência, baixa latência e consistência de dados. Em cenários de alta concorrência ou instabilidade de rede, a falta de controle de idempotência leva a cobranças duplicadas indesejadas, a degradação de parceiros de antifraude pode derrubar todo o fluxo de compras em efeito cascata, e a gravação desacoplada entre banco de dados e mensageria pode causar perdas irreparáveis de eventos financeiros (*dual-write problem*). Nossa solução deve entregar aos clientes uma autorização instantânea, segura e tolerante a falhas, garantindo que retries de rede nunca gerem cobranças duplicadas, enquanto fornece à organização financeira consistência eventual auditável com zero perda de eventos e observabilidade em tempo real de latência, taxa de erros e throughput.

---

| Campo | Descrição |
|---|---|
| **WHO** | Aplicações consumidoras de pagamento (e-commerce, gateways, apps móveis), clientes finais pagadores e engenheiros de software responsáveis pela confiabilidade dos serviços bancários. |
| **WHAT Problem** | Fragilidade em sistemas de pagamento distribuídos: (1) cobranças duplicadas causadas por retries sem idempotência; (2) lentidão ou travamento total quando o motor de antifraude fica instável; (3) inconsistência de saldo e perda de eventos entre o banco relacional e a mensageria (*dual-write problem*); e (4) ausência de métricas RED e tracing distribuído para diagnosticar falhas em tempo real. |
| **WHEN** | Durante a etapa síncrona de submissão e autorização da transação financeira, e no processamento assíncrono subsequente de atualização contábil de saldo (*ledger*) e alertas (*notification*). |
| **WHAT Job** | Autorizar ou recusar pagamentos em tempo real de forma idempotente e protegida por circuit breaker, garantindo a publicação confiável e ordenada do evento aprovado para os serviços contábeis e de notificação. |
| **WHAT benefits for the customer** | Resposta ultrarrápida (< 200ms), garantia contratual contra cobranças duplicadas mesmo sob instabilidade de rede ou duplo clique, e transparência imediata no status da transação. |
| **WHAT benefits for the company** | 100% de consistência contábil sem eventos perdidos via Transactional Outbox, alta disponibilidade operacional mantida via fallback/circuit breaker quando o antifraude falhar, e rastreabilidade total via OpenTelemetry e Grafana. |
