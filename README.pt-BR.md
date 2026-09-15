# Kernel de ledger em Java

*[Read in English](README.md)*

Um kernel concreto de ledger de partida dobrada em Java: as classes e
entidades de partida dobrada, e nada além disso. **Ainda sem banco de
dados** — isto é só o kernel, Java 25 puro, sem dependências além do JDK e
JUnit para os testes. Persistência é um projeto futuro deliberadamente
separado, construído sobre os tipos públicos deste aqui, não uma camada
JPA/JDBC colada por cima.

Este é um projeto independente, não faz parte do build de nenhuma aplicação
maior — só um módulo Maven simples que você clona e roda sozinho (`mvn test`).

## Decisões de design

Cada uma destas é uma bifurcação real de design, não uma escolha arbitrária:

- **`Money` não tem sinal; o sinal mora em `Posting.side()`.** Juntar
  "quanto" e "débito ou crédito" num único número assinado é compacto, mas
  quem lê o código precisa lembrar a convenção de sinal toda vez. Aqui
  `Money` não pode ser negativo (o construtor lança exceção), e um `Posting`
  é explicitamente `Posting.debit(account, amount)` ou
  `Posting.credit(account, amount)`. "Qual lançamento é o débito" é
  respondido lendo a chamada, nunca ficando de olho num sinal.

- **`AccountNature` é dona da regra contábil, não uma tabela de consulta.**
  `AccountNature.ASSET.signFor(NormalSide.CREDIT)` retorna `-1` porque a
  própria constante do enum sabe seu lado normal (`NormalSide.DEBIT`) e
  delega a regra de sinal para `NormalSide.signFor`. A regra de "o que um
  crédito faz numa conta de ativo" vive como código de verdade no tipo, não
  como uma convenção implícita que cada chamador precisa aplicar na mão.

- **`Account` é uma árvore de verdade, e garante seus próprios invariantes.**
  Herança de natureza, ausência de ciclos e nomes únicos entre irmãos não são
  checados por uma passada separada sobre uma lista plana, depois do fato.
  `Account.addChild(child)` recusa uma incompatibilidade de natureza
  (`NatureMismatchException`) ou um nome duplicado (`DuplicateAccountException`)
  no exato momento em que a aresta seria criada — não existe outra forma de
  anexar um filho, então um ciclo é estruturalmente inatingível em vez de
  algo que uma varredura separada precisa detectar.

- **`JournalEntry` não pode existir desbalanceado.** Não tem construtor
  público — só `JournalEntry.Builder`, e `.build()` valida (≥ 2 postings,
  toda conta tocada com `acceptsPostings()`, débitos == créditos) antes de
  devolver uma instância. "Válido por construção" elimina toda uma classe de
  bug: esquecer de chamar o validador. O custo é que `JournalEntry` é
  genuinamente um valor depois de construído (sem setters), enquanto
  `Account` continua sendo uma entidade mutável com identidade, porque um
  plano de contas é editado ao longo de anos e um lançamento, uma vez
  postado, não é.

- **`Ledger` é a raiz do agregado, não um saco de dados.**
  `Ledger.openAccount(...)` e `Ledger.post(entry)` são as *únicas* formas de
  fazer o grafo de objetos crescer, então ele consegue de fato garantir — não
  só documentar — que nenhum `JournalEntry` em `ledger.entries()` referencia
  uma `Account` que este `Ledger` não possui (`Ledger.post` checa identidade
  de objeto, não só uma string de id batendo).

- **Uma pequena hierarquia de exceções (`sealed class LedgerException`) em
  vez de um enum com tags.** Chamadores em Java distinguem falhas pela
  cláusula `catch`, então este kernel tem um tipo de exceção por modo de
  falha real (`UnbalancedEntryException`, `AccountNotPostableException`,
  `NatureMismatchException`, `DuplicateAccountException`,
  `DuplicateAccountCodeException`, `CurrencyMismatchException`) — cada uma
  carregando exatamente os dados necessários pra formatar sua própria
  mensagem, selada (`sealed`) para que um `switch` sobre elas seja exaustivo.

## Posições: a commodity de uma conta é o que ela GUARDA, não em que ela é precificada

Adicionado quando uma importação real de CSV precisou representar "quantas
ações de X estão em posse", não só "quanto dinheiro se moveu" — batendo com
um padrão que já existe no domínio de rastreio de posições: um ativo tem seu
próprio campo de cotação, separado de uma configuração de precisão decimal
para como sua quantidade é exibida.

- **`Account.commodity()` é sobrecarregada de propósito.** Para uma conta de
  caixa é uma moeda (BRL, USD) e todo `Posting` contra ela carrega `Money`
  naquela mesma moeda — o dinheiro É a posição ali. Para uma conta de
  posição em corretora é o próprio ATIVO
  (`Commodity.asset("PETR4", 1)`): `Money` num posting assim é o
  custo/proventos na moeda em que a negociação aconteceu (que NÃO precisa
  ser a mesma commodity da conta), e uma nova `Quantity` — a contraparte de
  `Money` do lado do ativo, com a mesma forma de não-negativo-por-construção
  — carrega a mudança real de unidades, na commodity do próprio ativo.
- **`Posting` reforça qual forma se aplica, checando `account.commodity().kind()`.**
  Uma conta do tipo moeda aceita `Money` naquela moeda e RECUSA `Quantity`
  totalmente (`Posting.debit(cashAccount, money, quantity)` lança
  `IllegalArgumentException` antes da construção). Uma conta do tipo ativo
  EXIGE uma `Quantity` compatível com sua própria commodity — omiti-la é
  recusado, nunca tratado silenciosamente como "sem mudança de posição",
  porque isso é exatamente o tipo de corrupção silenciosa que o `CLAUDE.md`
  deste projeto alerta em outro lugar (o incidente em que um campo de
  resultado anulado fabricou silenciosamente R$60 de imposto).
  `Ledger.quantityOf(account)` retorna `OptionalLong.empty()` para uma conta
  que não é de posição, não zero — "nunca negociou" e "posição zero
  confirmada" precisam continuar distinguíveis.
- **Um único posting só de quantidade é um `JournalEntry` legítimo e
  completo.** Um desdobramento de ações ou uma bonificação de custo zero
  move unidades SEM nenhum dinheiro — não há uma segunda perna pra balancear
  contra, porque nada de valor monetário mudou em lugar nenhum.
  `JournalEntry.Builder.build()` permite exatamente um posting quando ele é
  `Money.zero(...)` mais uma `Quantity` diferente de zero — a própria guarda
  do `Posting` de "amount E quantity ambos zero" já elimina o caso
  degenerado de um posting que de fato não faz nada, então admitir um único
  posting aqui não reabre esse buraco.

## `RECEIVABLE`/`PAYABLE`: nem todo investimento é uma posição

`AccountType` começou com oito espécies comuns de conta sob
`ASSET` (`BANK`, `CASH`, `STOCK`, `MUTUAL`) e `LIABILITY` (`CREDIT`).
`RECEIVABLE` e `PAYABLE` foram adicionados quando modelar um plano de contas
brasileiro de verdade revelou um caso que essas oito não cobrem bem: um
investimento sem unidade cotada publicamente para guardar uma `Quantity`.

A linha que separa qualquer ativo, investimento ou não, não é "é renda fixa
ou renda variável" — é **existe uma unidade cotada publicamente cuja
contagem vale a pena rastrear separada do seu valor em dinheiro**:

- **Tem uma → conta de posição (`STOCK`/`MUTUAL`, com `Quantity`).** Ações
  BR, ações EUA, ETFs e FIIs negociam em bolsa como unidades discretas
  (`STOCK`); Tesouro Direto e uma previdência privada acompanhada por cota
  (PGBL/VGBL) negociam em unidades fracionárias contra uma cotação diária
  (`MUTUAL`). Mesma forma de qualquer outra conta de posição: a commodity da
  própria conta é o ativo em si, uma compra carrega `Money` na moeda em que
  a negociação aconteceu mais uma `Quantity` na commodity daquele ativo.

  ```java
  Commodity petr4 = ledger.asset("PETR4", 1);
  Account petr4Acc = ledger.openAccount("petr4", "PETR4", petr4, acoesBr);
  petr4Acc.setType(AccountType.STOCK);

  Commodity cotaPrevidencia = ledger.asset("BRASILPREV-RT-FIX-100", 100_000);
  Account pgbl = ledger.openAccount("pgbl-brasilprev", "PGBL Brasilprev", cotaPrevidencia, previdencia);
  pgbl.setType(AccountType.MUTUAL);
  ```

- **Não tem nenhuma → `RECEIVABLE` (ativo) ou `PAYABLE` (passivo), sem
  `Quantity` alguma.** CDB, LCI, LCA e títulos privados não têm uma unidade
  cotada em mercado — o que existe é um valor em dinheiro que cresce por
  juros acumulados, a mesma forma de uma conta poupança. Forçar uma
  `Quantity` neles significaria inventar uma unidade falsa sem cotação real
  por trás. `RECEIVABLE` lê melhor que o `ASSET` genérico pra esses casos —
  "dinheiro que alguém te deve de volta" — e é o que uma perna de "a receber
  do cônjuge" numa conta conjunta/compartilhada (veja a seção sobre `Ledger`
  por pessoa acima) usaria do lado do outro parceiro, com `PAYABLE` como seu
  espelho do lado passivo.

  ```java
  Commodity brl = ledger.currency("BRL", 100);
  Account cdbXp = ledger.openAccount("cdb-xp-2027", "CDB XP 2027", brl, rendaFixaPrivada);
  cdbXp.setType(AccountType.RECEIVABLE);

  // rendimento: crédito numa conta INCOME, débito aqui — sem Quantity, é só dinheiro
  Posting.debit(cdbXp, Money.of(1234, brl));
  ```

Um plano de contas completo e trabalhado (contas bancárias até cada classe
de ativo acima) mora no histórico de commits/log de conversa deste projeto
em vez de ser duplicado aqui — as duas regras acima (posição vs. sem
posição, e qual `AccountType` segue de cada uma) são o que reaplicar a
qualquer nova classe de ativo que aparecer depois.

## `TRADING`: movendo dinheiro entre duas moedas diferentes

`JournalEntry` fixa uma `Commodity` para o lançamento inteiro — todo
`Posting` nele precisa estar naquela mesma moeda (`Builder.posting()` checa
isso antes de adicionar um). Isso é deliberado (veja o papel de
`CurrencyMismatchException` em outro lugar deste documento), mas significa
que um único `JournalEntry` não consegue expressar "R$5.000 saem de uma
conta em BRL, US$1.000 chegam numa conta em USD" — não há uma moeda única
que as duas pernas possam compartilhar.

`AccountType.TRADING` (`AccountNature.ASSET`, sem `Quantity`) existe para
transformar isso num *par* de lançamentos de moeda única, ligados por uma
conta técnica por moeda — um padrão às vezes chamado de Trading Account.
Um câmbio de uma conta corrente em BRL para uma conta de corretora em USD,
a R$5,00/US$1,00:

```java
Account tradingBrl = ledger.openAccount("trading-brl", "Câmbio BRL", brl, trading);
Account tradingUsd = ledger.openAccount("trading-usd", "Câmbio USD", usd, trading);
tradingBrl.setType(AccountType.TRADING);
tradingUsd.setType(AccountType.TRADING);

// lançamento 1 — só a perna BRL
JournalEntry.builder("cambio-1", hoje, "Câmbio BRL→USD (saída)", brl)
    .credit(nubankCC, Money.of(500000, brl))
    .debit(tradingBrl, Money.of(500000, brl))
    .build();

// lançamento 2 — só a perna USD
JournalEntry.builder("cambio-2", hoje, "Câmbio BRL→USD (entrada)", usd)
    .credit(tradingUsd, Money.of(100000, usd))
    .debit(nomadInvest, Money.of(100000, usd))
    .build();
```

Nenhuma mudança estrutural foi necessária pra suportar isso —
`Ledger.balanceOf()` já documenta que um saldo negativo (um crédito numa
conta cujo lado normal é débito) é um estado legítimo e reportável, não um
erro. É exatamente isso que `tradingUsd` acaba tendo aqui: +R$5.000 em
`tradingBrl`, −US$1.000 em `tradingUsd`. Convertidos pela MESMA cotação
usada na negociação (1.000 × 5,00 = 5.000), o par se cancela exatamente —
as contas de trading zeram numa moeda comum para essa troca específica.
Qualquer resíduo que aparecer depois, se uma cotação *diferente* for usada
pra convertê-las, é ganho/perda cambial real e observável — este kernel não
calcula isso (ainda não há camada de relatório), mas o dado que ele guarda
já é suficiente pra alguém calcular depois.

## `Ledger` é dono do registro de commodities e da construção idempotente do plano de contas

Adicionado quando um importador real começou a construir um plano de contas
incrementalmente a partir de linhas de CSV e precisou de "a conta chamada X,
criada no primeiro uso" — toda versão inicial dessa lógica mantinha seu
próprio cache `Map<String, Account>` na
frente de uma busca que o `Ledger` já faz internamente (`accountsById`).
Multiplicado entre as futuras camadas (salário, orçamento — as que o
usuário diz que estão vindo), isso é o mesmo cache reimplementado uma vez
por consumidor.

- **`Ledger.currency(code, scale)` / `Ledger.asset(code, scale)`** registram
  uma `Commodity` uma vez e devolvem a mesma instância em chamadas
  repetidas — fechando uma lacuna real, não só adicionando conveniência:
  `Commodity.equals()` compara só `code`, então dois `Commodity.currency("BRL", 100)`
  e `Commodity.currency("BRL", 1)` construídos independentemente comparariam
  IGUAIS apesar de discordarem na escala, e a aritmética de `Money` (que
  checa `equals`, não escala) os misturaria silenciosamente. Um segundo
  registro conflitante para o mesmo código é recusado de cara.
- **`Ledger.rootAccountOrCreate`/`Ledger.accountOrCreate`** devolvem a conta
  existente para um id se ele já existir, senão a criam — recusando (não
  substituindo silenciosamente) se um id REUTILIZADO foi solicitado com
  nome, natureza, commodity ou pai diferentes. Essa recusa foi o que de fato
  pegou um bug real: um id de conta de posição que não incorporava a moeda
  deixava uma única linha precificada em USD
  compartilhar silenciosamente uma conta com centenas de linhas precificadas
  em BRL para o mesmo instrumento, até esse método passar a recusar a
  incompatibilidade em vez de um cache improvisado mantendo silenciosamente
  qualquer forma que tivesse sido criada primeiro. Veja `LedgerChartBuildingTest`.

## Um `Ledger` é um livro fechado, não necessariamente uma pessoa

O uso pretendido para este kernel é um `Ledger` por pessoa: cada membro da
família tem sua própria instância, isolada das outras. Esse isolamento não é
só uma convenção de nomes — `Ledger.post(entry)` recusa qualquer `Posting`
cuja `Account` não seja possuída por aquele `Ledger` exato (a identidade da
`Account` é checada, não só uma string de id batendo, veja
`requireOwnedByThisLedger`), então o `JournalEntry` de uma pessoa
estruturalmente não consegue tocar o plano de contas de outra pessoa. Da
mesma forma, a unicidade global de `Ledger.setAccountCode` para
`Account.code()` é global *dentro de um `Ledger`*: os ledgers de duas
pessoas diferentes podem reutilizar o mesmo código sem conflito, porque são
livros diferentes.

O motivo de ser "um `Ledger` por pessoa" em vez de "um `Ledger` por família"
é o mesmo motivo de `JournalEntry` não poder existir desbalanceado: todo
lançamento num livro deveria pertencer a uma parte responsável. Um ledger de
família onde os postings de qualquer um caem numa pilha compartilhada para
de responder "de quem é esse dinheiro" tão bem quanto o modelo acima
responde "qual lado é o débito".

**Contas conjuntas não precisam de um conceito novo — precisam de outro
`Ledger`.** Nada aqui exige que `Ledger` mapeie 1:1 para uma pessoa; ele
mapeia para "um livro que fecha sozinho". A conta corrente conjunta de um
casal é naturalmente seu próprio livro: um terceiro `Ledger`
(`ledger-conjuge-conjunta`, por exemplo) que possui só aquela conta, com a
contribuição de cada parceiro postada nele a partir do seu lado. Isso mantém
o invariante de cada `Ledger` intacto — `accountsById` nunca precisa
representar posse fracionária, e `Account.addChild`/`acceptsPostings` não
precisam de um conceito de "compartilhado" que não têm hoje.

A outra opção — colocar a conta conjunta dentro do `Ledger` de um dos
parceiros e rastrear a parte do outro com uma conta sintética de "a
receber/a pagar do cônjuge" — é a resposta contábil clássica pra posse
compartilhada e também funciona bem aqui (são só mais duas `Account`s e
`Posting`s comuns, nada novo pra construir). É mais manual por casal do que
um `Ledger` compartilhado dedicado, mas não exige decidir isso de antemão.

Consolidar entre os vários `Ledger`s de uma família (patrimônio total de
todo mundo, por exemplo) é explicitamente um trabalho para uma camada acima
deste kernel — nada aqui agrega entre instâncias de `Ledger`, do mesmo jeito
que nada aqui fala com um banco de dados ainda.

## O que tem aqui

```
src/main/java/com/cornerstone/ledger/model/
  NormalSide.java               débito/crédito e a regra de sinal entre eles
  AccountNature.java            ativo/passivo/PL/receita/despesa + lado normal
  AccountType.java              espécies de conta → natureza
  Commodity.java                 moeda ou ativo em que um Money/Quantity é denominado
  Money.java                     valor imutável, não-negativo + commodity
  Quantity.java                   contagem de unidades imutável, não-negativa, de uma commodity ASSET
  Account.java                   nó do plano de contas; dona dos invariantes da árvore
  Posting.java                   uma perna de débito ou crédito de um lançamento; Money + Quantity opcional
  JournalEntry.java               o agregado de partida dobrada; válido por construção
  Ledger.java                     raiz do agregado: registro de commodities + plano de contas + lançamentos postados
  LedgerException.java + 6 subclasses
src/test/java/.../model/LedgerTest.java
src/test/java/.../model/PosicaoTest.java
src/test/java/.../model/LedgerChartBuildingTest.java
```

Rode assim: `mvn test` (precisa de um JDK 25 no `PATH`, ou aponte o
toolchain do Maven para um).

## O que fica deliberadamente de fora

- **Sem banco de dados.** Explicitamente fora de escopo por direção do
  usuário neste spike — uma camada JDBC/ORM sobre `Ledger`/`Account`/`JournalEntry`
  é seu próprio projeto futuro, não colada aqui.
- Sem motor fiscal, sem rebalanceamento, sem cálculo de posições/custo
  médio, sem camada HTTP — o escopo continua limitado às classes e
  entidades de partida dobrada.
