---
title: "Padrões de Projeto e Refatoração do OutboxService"
date: "2026-09-15 21:19:37"
tags: [spring-boot, outbox-pattern, design-patterns, kafka, opentelemetry, solid]
---

O exame da classe [`OutboxService`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java) confirma que ela acumula múltiplos papéis e forte acoplamento, violando princípios fundamentais de engenharia de software (notadamente **SOLID**) e boas práticas de arquitetura orientada a eventos.

Abaixo está o diagnóstico das fragilidades atuais e os padrões de projeto recomendados para reestruturar esse componente com alta qualidade, testabilidade e resiliência.

---

### 1. Diagnóstico dos Problemas e Violações de Princípios

1. **Violação do Princípio da Responsabilidade Única (SRP - Single Responsibility Principle)**
   A classe centraliza tarefas que pertencem a camadas e domínios diferentes:
   - Consulta e paginação no banco de dados para busca de lotes pendentes;
   - Desserialização de dados de contexto de rastreabilidade (OpenTelemetry);
   - Gerenciamento explícito de Spans do OpenTelemetry (criação, escopo, tags, captura de erro e encerramento);
   - Desserialização JSON do payload da mensagem;
   - Acionamento direto de envio de mensagens ao broker;
   - Atualização de status e contagem de tentativas;
   - Inspeção procedural e manual de cadeias de exceções de rede.

2. **Violação do Princípio Aberto/Fechado (OCP - Open/Closed Principle)**
   O serviço está acoplado diretamente a um único evento concreto e seu produtor:
   - [Linha 74](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java#L74): Converte o payload exclusivamente para [`PaymentAuthorizedEvent`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/event/PaymentAuthorizedEvent.java).
   - [Linha 76](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java#L76): Invoca diretamente [`PaymentEventProducer.sendPaymentAuthorizedEventSync`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/producer/PaymentEventProducer.java#L32-L50).
   - Se novos tipos de eventos precisarem da tabela de outbox (como estornos, cancelamentos ou notificações), será necessário alterar essa classe com cadeias de `if/else` ou `switch`.

3. **Poluição por Preocupações Transversais (Cross-Cutting Concerns)**
   Três níveis de blocos `try-catch-finally` aninhados são utilizados unicamente para gerenciar o ciclo de vida do Span de rastreio, soterrando a regra de negócio em código de infraestrutura.

4. **Antipadrão Transacional de Longa Duração (Connection Pool Pinning)**
   O método [`publishPendingEvents`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java#L44-L103) está anotado com `@Transactional`.
   - A transação e a conexão do banco de dados ficam presas durante todo o processamento do lote (até 50 eventos), enquanto chamadas síncronas de rede são feitas ao Kafka (com timeout de até 3 segundos cada).
   - Se houver lentidão na rede ou no broker, isso gera exaustão rápida do pool de conexões do banco (HikariCP).
   - Se uma exceção não tratada estourar, todo o lote é revertido no banco, mas mensagens já enviadas ao Kafka não podem ser desfeitas, gerando duplicidade inevitável.

5. **Modelo de Domínio Anêmico**
   A entidade [`OutboxEvent`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/entity/OutboxEvent.java) atua apenas como repositório de dados passivo. O serviço manipula suas propriedades diretamente por `setStatus`, `setProcessedAt` e `setRetryCount`, espalhando regras de transição de estado.

---

### 2. Padrões de Projeto Recomendados

#### Padrão 1: Strategy (ou Event Dispatcher / Router)
* **Objetivo**: Desacoplar o [`OutboxService`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java) dos tipos concretos de eventos e produtores Kafka, assegurando o princípio Aberto/Fechado (OCP).
* **Aplicação**:
  - Definir uma interface de estratégia:
    ```java
    public interface OutboxPublisherStrategy {
        boolean supports(String eventType);
        void publish(OutboxEvent event);
    }
    ```
  - Criar implementações específicas para cada tipo (ex.: `PaymentAuthorizedPublisherStrategy`):
    ```java
    @Component
    @RequiredArgsConstructor
    public class PaymentAuthorizedPublisherStrategy implements OutboxPublisherStrategy {
        private final PaymentEventProducer producer;
        private final ObjectMapper objectMapper;

        @Override
        public boolean supports(String eventType) {
            return "PAYMENT_AUTHORIZED".equalsIgnoreCase(eventType);
        }

        @Override
        public void publish(OutboxEvent event) {
            PaymentAuthorizedEvent payload = objectMapper.readValue(event.getPayload(), PaymentAuthorizedEvent.class);
            producer.sendPaymentAuthorizedEventSync(payload);
        }
    }
    ```
  - O [`OutboxService`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java) recebe `List<OutboxPublisherStrategy>` injetada pelo Spring e localiza a estratégia correspondente em tempo de execução. Novos eventos passam a ser adicionados apenas criando novas classes, sem tocar no serviço principal.

---

#### Padrão 2: Decorator ou Template Callback / Aspecto (AOP) para Observabilidade
* **Objetivo**: Isolar toda a complexidade de OpenTelemetry de dentro do laço principal.
* **Aplicação**:
  - Criar um componente executor de contexto, como um `OutboxTelemetryExecutor` ou template callback funcional:
    ```java
    @Component
    @RequiredArgsConstructor
    public class OutboxTelemetryExecutor {
        private final OpenTelemetryService openTelemetryService;
        private final Tracer tracer;
        private final ObjectMapper objectMapper;

        public void executeWithTracing(OutboxEvent event, Runnable action) {
            Context parentContext = extractParentContext(event.getTraceContext());
            Span span = tracer.spanBuilder("outbox.publish." + event.getType())
                    .setParent(parentContext)
                    .setAttribute("messaging.system", "kafka")
                    .setSpanKind(SpanKind.PRODUCER)
                    .setAttribute("outbox.retry_count", event.getRetryCount())
                    .startSpan();

            try (Scope scope = span.makeCurrent()) {
                if (event.getRetryCount() > 0) {
                    span.addEvent("outbox.retry", Attributes.of(AttributeKey.longKey("Attempt"), event.getRetryCount().longValue()));
                }
                action.run();
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR);
                throw e;
            } finally {
                span.end();
            }
        }
    }
    ```
  - A chamada no serviço torna-se direta e expressiva:
    `telemetryExecutor.executeWithTracing(event, () -> dispatcher.dispatch(event));`

---

#### Padrão 3: Strategy / Policy para Classificação de Erros e Tentativas (Retry Policy & Error Classifier)
* **Objetivo**: Substituir o método procedural com `instanceof` e laço `while` ([`isBrokerCommunicationError`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java#L105-L117)) por uma política extensível e encapsulada.
* **Aplicação**:
  - Criar um componente `OutboxErrorClassifier`:
    ```java
    public enum ErrorClassification {
        TRANSIENT_BROKER_ERROR, // Deve interromper o lote e tentar no próximo ciclo
        NON_TRANSIENT_ERROR      // Erro de dados/payload (poison pill); deve contar retry ou ir para Dead Letter
    }
    ```
  - O classificador decide se o erro é de rede/broker (ex.: `TimeoutException`, `KafkaException`) ou irrecuperável (ex.: erro de desserialização JSON). Se for erro de dados, o lote continua para os próximos itens em vez de travar o processo.
  - Adicionar limite de tentativas: quando `retryCount >= MAX_RETRIES`, o evento transiciona para [`OutboxStatus.FAILED`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/entity/OutboxStatus.java) em vez de permanecer indefinidamente em `PENDING`.

---

#### Padrão 4: Pipeline / Template Method (Ciclo de Vida do Evento)
* **Objetivo**: Separar a orquestração do lote (busca e iteração) do processamento individual de cada evento.
* **Aplicação**:
  - Dividir em dois componentes:
    1. [`OutboxService`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java): busca o lote de itens pendentes e delega ao processador.
    2. `OutboxEventProcessor`: processa um único evento ponta a ponta (rastreio -> estratégia de envio -> persistência de estado).

---

#### Padrão 5: Transaction Worker / Granularidade de Transação (Unit of Work Fino)
* **Objetivo**: Proteger o pool de conexões do banco de dados e evitar bloqueios desnecessários.
* **Aplicação**:
  - **Remover** o `@Transactional` amplo de [`publishPendingEvents()`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java#L44).
  - O envio síncrono de rede ao Kafka ocorre **fora** de qualquer transação de banco.
  - Ao receber a confirmação (ACK) do broker, persiste-se a mudança de status (`SENT`) em uma transação pontual rápida via repositório.
  - Isso evita conexões JDBC travadas durante eventuais picos de latência no cluster Kafka.

---

#### Padrão 6: Modelo Rico (Rich Domain Model / Information Expert)
* **Objetivo**: Encapsular as regras de transição de estado e contagem de tentativas na própria entidade [`OutboxEvent`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/entity/OutboxEvent.java).
* **Aplicação**:
  - Adicionar métodos de domínio:
    ```java
    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.processedAt = Instant.now();
    }

    public void recordFailure(int maxRetries) {
        this.retryCount++;
        if (this.retryCount >= maxRetries) {
            this.status = OutboxStatus.FAILED;
            this.processedAt = Instant.now();
        }
    }
    ```

---

### 3. Como ficaria o [`OutboxService`](file:///C:/Users/guima/OneDrive/Documentos/projects/payments-authorization-system/authorization-service/src/main/java/com/payments/authorization/service/OutboxService.java) refatorado

Com os padrões aplicados, o serviço passa a focar exclusivamente em orquestrar o lote:

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor eventProcessor;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingForUpdate(batchSize);
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            ProcessingResult result = eventProcessor.process(event);
            if (result.isSuccess()) {
                publishedCount++;
            } else if (result.isBrokerUnavailable()) {
                log.warn("Broker Kafka indisponível. Interrompendo lote para tentativa posterior.");
                break;
            }
        }
        return publishedCount;
    }
}
```

### Resumo dos Benefícios
- **Manutenibilidade**: Código limpo, legível e sem aninhamento excessivo de blocos de tratamento de erro.
- **Extensibilidade (OCP)**: Suporte a novos eventos adicionando apenas novas implementações de `OutboxPublisherStrategy`.
- **Desempenho e Resiliência**: Sem travamento do pool de banco de dados durante envio de rede.
- **Testabilidade**: Cada responsabilidade pode ser testada isoladamente com testes unitários simples e focados.
