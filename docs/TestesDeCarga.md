# TransactionCore — Testes de Carga e Resiliência

Este documento registra os testes realizados para validar duas coisas separadas, mas complementares: **quanto volume o sistema aguenta** (teste de carga) e **o que acontece quando parte do sistema falha** (teste de resiliência). Em um sistema financeiro, a segunda pergunta é tão importante quanto a primeira — não basta ser rápido, é preciso nunca perder ou duplicar dinheiro, mesmo quando algo dá errado.

## 1. Teste de carga

### Objetivo

Medir o throughput (requisições por segundo) e a latência do fluxo principal do sistema — criação de uma transferência via `POST /payments` — sob carga crescente, e confirmar que o sistema não perde nem corrompe nenhuma transação mesmo sob volume alto.

### Ferramenta e metodologia

Usamos [k6](https://k6.io) para simular usuários virtuais realizando transferências repetidamente, com uma chave de idempotência única a cada requisição (garantindo que cada chamada gera uma transação nova, não um cache-hit).

- **Pool de contas:** 20 contas criadas antecipadamente, com saldo alto o suficiente para não gerar falhas de saldo insuficiente durante o teste — o objetivo era medir performance, não repetir a validação de regras de negócio já coberta pelos testes unitários.
- **Perfil de carga:** rampa gradual de 0 até 200 usuários virtuais simultâneos, ao longo de 5 minutos, com aquecimento e desaquecimento nas pontas.
- **Ambiente:** todos os 4 serviços, Postgres, Redis e Kafka rodando localmente na mesma máquina de desenvolvimento — ou seja, competindo pelos mesmos recursos de CPU e memória. Em produção, com serviços isolados em máquinas ou containers dedicados, os números tendem a ser melhores.

### Resultados

| Métrica | Valor |
|---|---|
| Requisições HTTP totais (`POST /payments`) | 61.953 |
| Throughput sustentado | ~206 requisições/segundo |
| Taxa de erro | 0% |
| Latência média | 275ms |
| Latência mediana (p50) | 237ms |
| Latência p95 | 561ms |
| Latência p99 | 745ms |
| Latência máxima observada | 1,32s |

**Nenhuma requisição falhou** ao longo dos 5 minutos de teste — todas as ~62 mil transferências foram criadas com sucesso e persistidas corretamente.

### Por que a latência sobe sob carga

Cada requisição de transferência faz **duas chamadas HTTP síncronas** do `payment-service` para o `account-service`, validando a disponibilidade das contas de origem e destino antes de criar a transação. Com 200 usuários virtuais simultâneos, o `account-service` recebe o dobro do tráfego do `payment-service`, e essa latência soma à cadeia inteira. Isso é uma consequência esperada e honesta da arquitetura de comunicação síncrona escolhida para validação rápida — não um bug.

## 2. Achado: gargalo no processamento do Outbox

Durante o teste de carga, observamos um comportamento importante: mesmo após o teste do k6 terminar, o tópico Kafka `transactioncore.transactions.initiated` continuou recebendo mensagens novas por vários minutos — um total de ~44 mil mensagens visíveis no Kafdrop enquanto o backlog ainda drenava.

### Causa

O `OutboxEventPoller` do `payment-service` processa eventos pendentes **sequencialmente**: a cada ciclo de 5 segundos, ele busca todos os registros pendentes e publica um por um, esperando a confirmação do Kafka antes de seguir para o próximo. Sob o volume gerado pelo teste (~62 mil transações criadas em 5 minutos), a taxa de criação de eventos superou, por um período, a taxa de publicação — gerando um backlog que precisou de tempo adicional para ser drenado por completo, mesmo após o fim do teste de carga.

### Por que isso não é uma falha de integridade

Nenhum evento foi perdido. O backlog é justamente o comportamento esperado do outbox pattern sob pressão: eventos ficam persistidos e aguardando publicação, nunca descartados. Confirmamos isso monitorando a contagem de eventos não publicados:

```sql
SELECT COUNT(*) FROM outbox_event WHERE published = false;
```

Esse número caiu de forma consistente ao longo dos minutos seguintes, até chegar a zero — confirmando que o sistema drenou o backlog inteiro sem perda de dados, apenas com atraso.

### Melhoria identificada

O poller processa eventos um de cada vez, de forma estritamente sequencial. Uma evolução natural seria publicar em lote ou paralelizar a publicação (múltiplas mensagens em trânsito simultaneamente, sem esperar a confirmação de cada uma isoladamente antes de enviar a próxima), trocando parte da simplicidade atual por mais throughput de publicação sob picos de carga.

## 3. Testes de resiliência (falhas simuladas)

Além do volume, testamos manualmente o comportamento do sistema diante de falhas de infraestrutura — desligando serviços de propósito, no meio do processamento de uma transferência.

### Cenário A — `account-service` fora do ar durante o processamento do `ledger-service`

**Setup:** uma transferência foi criada normalmente (com `account-service` e `payment-service` no ar). Antes do `ledger-service` processar o evento correspondente, o `account-service` foi derrubado propositalmente.

**Comportamento observado:**
1. O `ledger-service` tentou debitar a conta de origem e recebeu um erro de conexão (serviço indisponível).
2. O mecanismo de retry com backoff tentou novamente algumas vezes, automaticamente, sem intervenção manual.
3. Como o `account-service` continuou fora do ar, todas as tentativas se esgotaram, e o evento foi movido para o tópico de Dead Letter Queue (`transactioncore.transactions.initiated.dlq`), preservando o conteúdo original e o motivo exato da falha.
4. Nenhuma transação ficou "perdida" — o evento continuou disponível para investigação e reprocessamento manual, e a transação nunca foi marcada como concluída incorretamente.

### Cenário B — falha no meio de uma Saga (débito bem-sucedido, crédito falho)

**Setup:** simulação de uma transferência onde o débito na conta de origem foi aplicado com sucesso, mas o crédito na conta de destino falhou (conta indisponível).

**Comportamento observado:**
1. O `ledger-service` detectou a falha no crédito.
2. Antes de reportar a transação como falha, ele acionou automaticamente a compensação — creditando de volta a conta de origem, desfazendo o débito já aplicado.
3. A compensação usou um identificador de operação próprio, diferente do débito original, garantindo que o mecanismo de idempotência do `account-service` não bloqueasse essa segunda operação por engano.
4. Só depois da compensação confirmada, o evento de falha foi publicado.

**Resultado:** o saldo da conta de origem retornou ao valor original — nenhum dinheiro ficou "preso" fora de uma conta, mesmo com a falha no meio do fluxo.

## 4. Conclusão

O sistema demonstrou, sob os dois eixos testados:

- **Volume:** suporta ao redor de 200 requisições por segundo com 0% de taxa de erro em ambiente de desenvolvimento local (todos os componentes competindo pelos mesmos recursos de uma única máquina), com latência p95 na casa de 500-600ms.
- **Resiliência:** nenhuma falha simulada — de rede, de serviço indisponível, ou de passo intermediário de uma Saga — resultou em perda de evento, duplicação de processamento, ou inconsistência de saldo. As falhas foram sempre tratadas de forma controlada (retry, Dead Letter Queue, compensação), com sinalização clara para investigação quando a recuperação automática não era mais possível.

O principal ponto de melhoria identificado — o processamento sequencial do outbox sob picos de carga — não compromete a integridade dos dados, mas representa uma oportunidade real de evolução de performance, documentada como próximo passo técnico do projeto.