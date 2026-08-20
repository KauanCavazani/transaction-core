# TransactionCore: construindo um sistema de pagamentos do zero para aprender (de verdade) como bancos digitais funcionam

## 1. Introdução — o que é o TransactionCore e por quê

O TransactionCore é um sistema de pagamentos que simula, em pequena escala, como um banco digital move dinheiro entre contas. Ele nasceu de um objetivo simples: eu queria migrar da área de consultoria de TI para o setor financeiro, e percebi que a maioria dos portfólios de desenvolvedor por aí são projetos de CRUD — cadastro, listagem, edição, exclusão. Isso não mostra se alguém sabe lidar com os problemas reais que aparecem quando dinheiro está envolvido: consistência entre serviços, concorrência, falhas de rede, duplicidade de requisições, auditoria.

Então decidi construir algo mais próximo da realidade: um sistema com múltiplos serviços independentes, comunicação assíncrona via eventos, e todas as garantias que um sistema financeiro de verdade precisa ter. Não é um banco de verdade — mas as decisões de arquitetura foram tomadas como se fosse.

## 2. Visão geral da arquitetura

O sistema é dividido em quatro serviços independentes, cada um com sua própria responsabilidade e seu próprio banco de dados:

- **account-service** — dono das contas e dos saldos. É quem sabe debitar e creditar dinheiro de verdade.
- **payment-service** — recebe o pedido de transferência, valida o essencial, e inicia o processo.
- **ledger-service** — o "contador" do sistema. Ele decide se a transferência realmente acontece, aplicando as regras de contabilidade.
- **notification-service** — avisa o resultado final (sucesso ou falha) de cada transferência.

O fluxo de uma transferência, em linhas gerais:

```
Cliente → payment-service → (evento) → ledger-service → account-service (debita/credita)
                                              ↓
                                        (evento de resultado)
                                              ↓
                                     notification-service
```

Repara que o `payment-service` não é quem move o dinheiro — ele só inicia o processo e publica um evento. Quem efetivamente debita e credita é o `ledger-service`, reagindo a esse evento de forma assíncrona.

### Por que microsserviços, e não um único sistema

A decisão de separar em serviços independentes, cada um com seu próprio banco, não foi só "porque é moderno". Foi para reproduzir um problema real: em bancos de verdade, diferentes times cuidam de diferentes partes do sistema (contas, pagamentos, contabilidade, notificações), e esses times não podem depender uns dos outros para fazer deploy. Separar os serviços obriga a resolver problemas que um sistema monolítico esconde — como saber se uma operação em outro serviço deu certo, sem poder simplesmente "chamar um método" e ter a resposta na hora.

## 3. Decisão técnica: Outbox Pattern

### O problema

Imagina que o `payment-service` precisa fazer duas coisas ao mesmo tempo: salvar a transferência no banco de dados, e avisar o `ledger-service` que uma transferência nova existe (publicando um evento). Essas duas ações acontecem em dois sistemas diferentes — o banco de dados e o sistema de mensageria (Kafka). E se o serviço salvar no banco com sucesso, mas cair bem antes de publicar o evento? A transferência existe, mas ninguém nunca fica sabendo dela. Ela fica "presa" para sempre.

### A solução

Em vez de salvar no banco e publicar no Kafka como duas ações separadas, o sistema salva **os dois** — o dado da transferência e o "compromisso de publicar o evento" — na mesma escrita no banco de dados, de forma atômica. Depois, um processo separado, rodando periodicamente, lê esses "compromissos" pendentes e os publica de verdade no Kafka, marcando cada um como concluído só depois de ter certeza de que o Kafka recebeu.

Esse padrão foi implementado duas vezes no projeto — uma no `payment-service`, outra no `ledger-service` — porque os dois publicam eventos como parte do que fazem.

## 4. Decisão técnica: idempotência em múltiplas camadas

### O problema

Em qualquer sistema que recebe requisições pela internet, timeouts e falhas de rede acontecem. Se um cliente não recebe resposta a tempo, é normal ele tentar de novo. O problema: se o sistema não souber diferenciar "essa é uma transferência nova" de "essa é a mesma transferência de novo, só que reenviada", ele pode processar a mesma transferência duas vezes — debitando o dobro do dinheiro.

### A solução, em camadas

O sistema resolve isso com três camadas de proteção, cada uma cobrindo um cenário diferente:

1. **Uma constraint de unicidade no banco de dados** — garante, no nível mais fundamental, que a mesma "chave de identificação" da requisição nunca gera duas transferências, mesmo que todo o resto falhe.
2. **Um cache com trava de concorrência (Redis)** — antes mesmo de tocar no banco, o sistema tenta "reservar" aquela chave. Se duas requisições idênticas chegarem ao mesmo tempo, só uma consegue a reserva; a outra sabe imediatamente que já existe alguém processando aquilo.
3. **Registro de eventos já processados** — cada serviço que reage a eventos (o `ledger-service` e o `notification-service`) guarda quais eventos já tratou, porque sistemas de mensageria como o Kafka garantem que uma mensagem será entregue "pelo menos uma vez" — o que, na prática, significa que ela pode chegar duplicada.

Cada camada existe porque resolve um problema específico que as outras não resolvem sozinhas.

## 5. Decisão técnica: Saga com compensação

### O problema

Como não existe uma "transação única" que abranja múltiplos serviços diferentes (cada um com seu próprio banco), uma transferência precisa ser dividida em passos: primeiro debitar a conta de origem, depois creditar a conta de destino. E se o débito funcionar, mas o crédito falhar? O dinheiro já saiu de um lugar e não chegou a lugar nenhum.

### A solução

Esse padrão é chamado de Saga: uma sequência de passos entre serviços, onde cada passo tem um passo de "desfazer" correspondente. Se o crédito falha depois que o débito já aconteceu, o sistema aciona automaticamente uma compensação — credita de volta a conta de origem, desfazendo o débito. Só depois disso ele avisa que a transferência falhou.

Esse é, provavelmente, o trecho de lógica mais delicado do projeto inteiro: ele precisa garantir que a própria compensação não seja aplicada duas vezes (usando o mesmo mecanismo de idempotência descrito acima), e precisa decidir o que fazer no cenário raro em que até a compensação falha — nesse caso, o sistema não tenta "adivinhar" uma solução automática; ele registra isso como um erro crítico, para que alguém intervenha manualmente.

## 6. Decisão técnica: contabilidade em partida dobrada

### O conceito

Bancos não registram uma transferência como um único número solto ("saiu R$150 da conta A, entrou na conta B"). Eles registram **dois lançamentos separados**: um débito na conta de origem, um crédito na conta de destino — e a regra de ouro é que a soma desses dois lançamentos é sempre zero. Isso é uma técnica contábil usada há séculos, e todo sistema financeiro sério a usa internamente, mesmo que o usuário final nunca veja isso.

### Por que isso importa

Esse formato permite verificação estrutural automática: a qualquer momento, é possível somar os lançamentos de uma transferência e confirmar que o resultado é zero. Se não for, existe um problema real — um bug ou uma inconsistência — e isso é detectável matematicamente, sem depender de auditoria manual. É esse formato que o `ledger-service` usa para registrar cada transferência processada com sucesso.

## 7. Resiliência: retry, backoff e Dead Letter Queue

Sistemas distribuídos falham — não é uma questão de "se", é uma questão de "quando". Um serviço pode estar temporariamente fora do ar, uma conexão pode cair, uma mensagem pode chegar corrompida.

O sistema trata isso em duas etapas:

1. **Tentativas automáticas com espera crescente** — se processar um evento falha, o sistema tenta de novo algumas vezes, com um intervalo entre as tentativas, antes de desistir. Isso cobre falhas curtas e temporárias.
2. **Uma "fila de mensagens problemáticas"** — se todas as tentativas falharem, em vez de simplesmente descartar a mensagem (o que faria a transferência sumir sem deixar rastro), ela é movida para um lugar separado, guardando o motivo exato da falha, para investigação e reprocessamento posterior.

Um detalhe técnico interessante aqui: o conteúdo dessa "mensagem problemática" pode ser de dois tipos diferentes, dependendo de onde a falha aconteceu (se foi ao tentar entender a mensagem, ou se foi durante o processamento dela depois de já ter sido entendida). O sistema usa um mecanismo que escolhe automaticamente a forma certa de guardar cada tipo, sem quebrar em nenhum dos dois cenários.

## 8. Segurança de tipos e modelagem de dinheiro

Uma decisão simples, mas fundamental: dinheiro nunca é representado com os tipos numéricos "de ponto flutuante" comuns em programação (os mesmos usados para medir distância, temperatura, etc). Esses tipos não conseguem representar valores decimais com exatidão perfeita — o que, em cálculos financeiros repetidos, gera diferenças de centavos que se acumulam com o tempo.

Em vez disso, o sistema usa um tipo numérico decimal exato para qualquer valor monetário, e encapsula isso em um objeto próprio que representa "um valor de dinheiro em uma moeda" — nunca um número solto. Esse objeto garante, por construção, que não é possível somar reais com dólares por engano, e centraliza toda a lógica de comparação e operação de valores monetários em um único lugar do código.

## 9. Testes

Cada serviço tem testes de unidade cobrindo suas regras de negócio principais — as entidades (contas, transações, lançamentos contábeis) e os serviços que orquestram a lógica. Os testes usam dublês (mocks) para simular as dependências externas, como bancos de dados e chamadas para outros serviços, o que permite rodá-los rapidamente, sem precisar de nenhuma infraestrutura real no ar.

Os cenários testados não são só os "caminhos felizes" — incluem explicitamente casos de falha (saldo insuficiente, conta inexistente, chave de idempotência reutilizada com dados diferentes) e, principalmente, os cenários de concorrência e compensação que são o motivo de o sistema existir dessa forma.

## 10. Desafios reais enfrentados (e como foram resolvidos)

Nenhum sistema desse tamanho é construído sem tropeços — e acho que vale compartilhar alguns dos mais instrutivos:

**Um `@Transactional` que não fazia nada.** Em um certo ponto, um método marcado como transacional simplesmente não estava garantindo atomicidade nenhuma — sem erro, sem aviso, silenciosamente. A causa: no Spring, esse tipo de anotação funciona através de um "proxy" que intercepta chamadas externas ao objeto; quando um método chama outro método da mesma classe internamente, esse proxy é contornado, e a anotação perde o efeito. A correção foi mover a lógica transacional para uma classe separada, garantindo que a chamada realmente passasse pelo mecanismo do Spring.

**Uma mensagem sendo serializada duas vezes.** Durante os testes, um evento estava chegando ao destino como uma string contendo JSON dentro de outra string JSON. A causa era uma configuração de serialização aplicando conversão duas vezes sobre um conteúdo que já vinha pronto. A correção envolveu simplificar essa configuração para não duplicar o trabalho.

**Um consumidor de eventos que não sabia diferenciar dois tipos de mensagem.** Um mesmo serviço precisava reagir a dois tipos diferentes de evento, vindos de dois tópicos diferentes, mas configurados para desserializar tudo da mesma forma. A correção foi dar a cada tipo de evento sua própria "receita" de leitura, decidida pelo tópico de origem, não por metadados que nem sempre estão presentes na mensagem.

## 11. Limitações conhecidas e o que evoluiria com mais tempo

Nenhum sistema é perfeito, e eu prefiro deixar isso explícito em vez de fingir que não existe:

- **As notificações têm texto fixo, sem suporte a múltiplos idiomas.** Numa evolução real, o sistema guardaria uma chave de mensagem e os dados variáveis, e só montaria o texto final — no idioma certo — na hora de exibir, usando um mecanismo de internacionalização já pronto no próprio ecossistema Java.
- **Quando a compensação de uma transferência falha, o sistema só registra isso como um erro crítico em log.** O ideal seria um alerta automático indo direto para um time de operações, em vez de depender de alguém encontrar esse log manualmente.
- **A publicação de eventos depende de um processo que verifica periodicamente se há algo novo para publicar.** Funciona bem, mas existe uma abordagem mais sofisticada, usada por sistemas de maior escala: ler as mudanças diretamente do log interno de transações do banco de dados, no exato momento em que acontecem, em vez de ficar perguntando de tempos em tempos. Uma tecnologia comum para isso é o Debezium.
- **Não existe autenticação entre os serviços internos.** Hoje, qualquer chamador que souber o endereço certo pode acionar as operações internas de débito e crédito do `account-service`. Em um ambiente real, essa comunicação seria protegida — seja por certificados entre serviços, chaves de API, ou uma rede isolada que só os próprios serviços conseguem acessar.

## 12. Conclusão

Construir o TransactionCore me obrigou a pensar em problemas que um CRUD simples nunca revelaria: o que acontece quando duas coisas precisam ser verdadeiras ao mesmo tempo, mas vivem em sistemas diferentes; o que fazer quando uma operação falha no meio do caminho; como garantir que um evento nunca se perde, mesmo quando tudo dá errado ao mesmo tempo. Nenhuma dessas respostas é óbvia — e é exatamente por isso que valeu a pena.