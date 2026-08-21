# TransactionCore — Testes de Carga (Parte 2): Investigando e Resolvendo o Gargalo do Outbox

Este documento dá continuidade ao [documento de testes de carga e resiliência](./transactioncore-testes-carga-resiliencia.md), aprofundando o achado mais relevante daquela rodada: o atraso de ~30 minutos na publicação de eventos pelo Outbox Pattern. Aqui, documentamos o processo completo de investigação, as hipóteses testadas, e a solução final — reduzindo esse tempo para ~10 minutos.

## 1. O problema, recapitulado

No teste de carga original, ~62 mil transferências foram criadas em 5 minutos, mas o tópico Kafka correspondente levou **cerca de 30 minutos** para receber todas as mensagens. A causa raiz identificada: o `OutboxEventPoller` publicava eventos **um de cada vez**, de forma sequencial e bloqueante — esperando a confirmação do Kafka antes de sequer iniciar a publicação do próximo. Com ~62 mil eventos e um custo de rede de dezenas de milissegundos por publicação, o tempo total se acumulava linearmente.

## 2. Primeira solução: Change Data Capture (CDC) com Debezium

Em vez de otimizar o poller existente (paralelizar publicações, processar em lote), optamos por substituir o mecanismo de polling inteiro por uma abordagem mais próxima da usada por sistemas de grande escala: **Change Data Capture**.

### O que mudou

- O Postgres passou a rodar com replicação lógica habilitada (`wal_level=logical`).
- Um processo do Debezium, rodando dentro do Kafka Connect, passou a ler o log interno de transações do banco diretamente — capturando cada novo registro na tabela `outbox_event` no instante em que ele é commitado, sem esperar nenhum ciclo de verificação periódica.
- O recurso "Outbox Event Router" do próprio Debezium foi configurado para extrair o payload e o tópico de destino diretamente das colunas da tabela, publicando no Kafka sem nenhuma transformação manual.
- O `OutboxEventPoller` foi removido do código — a responsabilidade de publicação deixou de existir na aplicação.

### Resultado imediato

O tópico de eventos de transferência iniciada passou a ser preenchido **em tempo real**, no mesmo instante da requisição — o atraso de publicação foi eliminado por completo.

## 3. Um novo gargalo aparece: o consumo, não mais a publicação

Rodando o mesmo teste de carga novamente, um novo padrão surgiu: os tópicos de resultado (transação concluída/falha) ainda demoravam **cerca de 25 minutos** para receber todas as mensagens — mesmo com a publicação já sendo instantânea.

### Diagnóstico

A causa não estava mais na publicação, e sim no **consumo**: o `ledger-service`, que processa cada transferência fazendo duas chamadas HTTP sequenciais (débito e crédito), processava as mensagens **uma de cada vez**. Isso porque o tópico Kafka correspondente havia sido criado automaticamente com **uma única partição** — e o Kafka nunca permite mais de um consumidor processando a mesma partição ao mesmo tempo, independentemente de configuração de concorrência na aplicação.

## 4. Solução: aumentar partições e paralelismo do consumidor

O princípio geral do Kafka: partições definem o teto máximo de paralelismo possível — aumentar threads de consumo sem aumentar partições correspondentes não tem efeito algum, pois as threads extras simplesmente não recebem trabalho.

### O que foi feito

1. O número padrão de partições dos tópicos foi elevado para 6.
2. A concorrência do consumidor no `ledger-service` foi ajustada para acompanhar esse número, permitindo processamento genuinamente paralelo.

### Progressão medida

| Configuração | Tempo de drenagem | Observação |
|---|---|---|
| 1 partição, consumo sequencial | ~25 minutos | Consumo é o novo gargalo, mesmo com publicação instantânea |
| 6 partições, concorrência 3 | ~15 minutos | Ganho parcial — concorrência configurada abaixo do paralelismo disponível |
| 6 partições, concorrência 6 | ~10 minutos | Paralelismo utilizado por completo |

## 5. Descoberta lateral: contenção de lock otimista

Ao subir a concorrência para 6, um novo sintoma apareceu: um pequeno número de falhas de `ObjectOptimisticLockingFailureException` — o mecanismo de lock otimista (`@Version`) da entidade `Account` detectando que duas threads tentaram atualizar a mesma conta ao mesmo tempo.

### A causa: um artefato do próprio teste

O teste de carga usava um pool de apenas 20 contas, compartilhado entre até 200 usuários virtuais simultâneos — uma concentração de tráfego muito acima do que aconteceria em produção real, onde milhões de contas dividem a carga.

### Duas camadas de mitigação implementadas

1. **Retry automático e local**, dentro do próprio `account-service`: ao detectar esse conflito específico, o método de débito/crédito tenta novamente algumas vezes, com um atraso curto (100ms), antes de propagar qualquer erro. Isso resolveu a esmagadora maioria dos conflitos de forma transparente.
2. **Retry de nível superior**, via o mecanismo de resiliência do Kafka já existente (retry com backoff + Dead Letter Queue), como rede de segurança para os casos raros em que o retry local não foi suficiente.

### Validação da hipótese

Para confirmar que a causa era o tamanho do pool de contas do teste — e não uma falha real de arquitetura — o teste foi repetido com um pool de 300 contas, mantendo a mesma configuração de concorrência.

**Resultado: zero erros de lock otimista, no mesmo tempo de processamento (~10 minutos).** Isso confirmou que a contenção observada era inteiramente um artefato da baixa variedade de contas no teste, não uma limitação do sistema.

### Uma hipótese descartada pelos dados: o pool de conexões do banco

Antes de testar o pool de contas, cogitou-se que o pool de conexões padrão do `account-service` (10 conexões, via HikariCP) pudesse se tornar um gargalo sob a nova concorrência. O teste foi repetido com o pool aumentado para 20 conexões — sem nenhuma mudança perceptível no tempo total. Concluímos que, nesse nível de carga, o pool de conexões nunca foi, de fato, o fator limitante.

## 6. Uma otimização considerada e conscientemente descartada

Uma última hipótese de melhoria foi avaliada: paralelizar as duas chamadas HTTP do `ledger-service` (débito e crédito) em vez de executá-las sequencialmente, o que reduziria a latência por mensagem processada em aproximadamente metade.

### Por que essa otimização foi descartada

A ordem sequencial atual (debitar, só depois creditar) não é apenas uma escolha de implementação — é o que garante estruturalmente que dinheiro nunca seja criado do nada. Paralelizar as duas chamadas introduziria um cenário hoje impossível: o crédito sendo aplicado com sucesso enquanto o débito correspondente falha, criando saldo na conta de destino sem que ele tenha efetivamente saído da conta de origem.

Corrigir esse novo cenário exigiria uma segunda via de compensação (desfazer o crédito, na direção oposta da compensação que já existe para "débito ok, crédito falho"), dobrando a complexidade do fluxo de recuperação de falhas.

**Decisão:** o ganho de performance (~2x de latência por mensagem) não justificou dobrar a superfície de risco à integridade financeira do sistema. Em vez disso, o throughput deveria continuar sendo resolvido pelo eixo de paralelismo horizontal (mais partições, mais instâncias), que não introduz nenhum novo caminho de falha na lógica de negócio.

## 7. Resultado final e o que ainda limita o sistema

| Métrica | Antes | Depois |
|---|---|---|
| Tempo de drenagem do backlog de publicação | ~30 minutos | Instantâneo (CDC) |
| Tempo de drenagem do processamento de consumo | ~25 minutos | ~10 minutos |
| Erros de contenção sob carga realista (300 contas) | — | 0 |

O teto atual de processamento (~10 minutos para ~62 mil transferências, em um único processo do `ledger-service`) é determinado, de forma estrutural e esperada, por dois fatores: o número de partições configurado (6) e a latência inerente de duas chamadas HTTP síncronas por mensagem processada. Superar esse teto exigiria escalonamento horizontal — múltiplas instâncias do `ledger-service`, cada uma consumindo uma fatia das partições — que é a forma como sistemas de pagamento de grande escala resolvem esse mesmo problema na prática, e permanece documentado como evolução natural do projeto.