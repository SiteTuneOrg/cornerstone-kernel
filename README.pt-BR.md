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
  `DuplicateAccountCodeException`, `CurrencyMismatchException`,
  `CommodityConflictException`, `AccountIdConflictException`,
  `ForeignAccountException`, `ForeignPostingException`,
  `DuplicateJournalEntryException`, `InsufficientPostingsException`,
  `CyclicAccountException`, `ArithmeticRangeException`) — cada uma carregando exatamente os dados
  necessários pra formatar sua própria mensagem, selada (`sealed`) para que
  um `switch` sobre elas seja exaustivo. Mantido honesto numa revisão do
  `Ledger`: um punhado de violações de invariante (registro de commodity
  conflitante, id reutilizado com forma diferente, id já em uso, posting
  contra uma conta que pertence a outro `Ledger`) ainda lançava um
  `IllegalArgumentException` cru, fora da hierarquia selada — as três
  exceções acima fecharam essa lacuna. `IllegalArgumentException` puro
  continua onde já estava, pra validação comum de argumento de construtor
  (`Money`/`Quantity` recusando valor negativo, `Account` recusando nome
  vazio) — isso é um tipo de falha diferente de um invariante do ledger, e é
  a convenção padrão do Java pra esse caso.

## Posições: a commodity de uma conta é o que ela GUARDA, não em que ela é precificada

Adicionado quando uma importação real de CSV precisou representar "quantas
ações de X estão em posse", não só "quanto dinheiro se moveu" — batendo com
um padrão que já existe no domínio de rastreio de posições: um ativo tem seu
próprio campo de cotação, separado de uma configuração de precisão decimal
para como sua quantidade é exibida.

- **`Account.commodity()` é sobrecarregada de propósito.** Para uma conta de
  caixa é uma moeda (BRL, USD); um `Posting` contra ela nessa mesma moeda
  carrega só `Money` — o dinheiro É a posição ali (um posting de um
  lançamento em outra moeda é uma perna de câmbio, veja `TRADING` abaixo). Para uma conta de
  posição em corretora é o próprio ATIVO
  (`Commodity.asset("PETR4", 1)`): `Money` num posting assim é o
  custo/proventos na moeda em que a negociação aconteceu (que NÃO precisa
  ser a mesma commodity da conta), e uma nova `Quantity` — a contraparte de
  `Money` do lado do ativo, com a mesma forma de não-negativo-por-construção
  — carrega a mudança real de unidades, na commodity do próprio ativo.
- **`Posting` reforça qual forma se aplica: há `Quantity` exatamente quando a
  commodity da conta difere da do dinheiro.** Uma conta em moeda lançada na
  própria moeda aceita `Money` e RECUSA `Quantity` totalmente
  (`Posting.debit(cashAccount, money, quantity)` lança
  `IllegalArgumentException` antes da construção). Uma conta em moeda lançada
  em OUTRA moeda é uma perna de câmbio e EXIGE `Quantity` na própria moeda
  (veja `TRADING` abaixo), com os DOIS lados diferentes de zero: a razão entre
  eles é a taxa, e duas pernas de dinheiro zero fechariam em 0 == 0 criando
  moeda estrangeira do nada. Uma conta do tipo ativo
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
  numa conta de posição (tipo ativo) com `Money.zero(...)` mais uma
  `Quantity` diferente de zero — nunca numa conta de caixa, onde uma perna
  de câmbio sozinha com dinheiro zero criaria moeda do nada. A própria guarda
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

`Account.code()` é pensado como chave de correlação estável definida pelo
usuário — ex. batendo com as referências de conta de uma importação de OFX,
como discutido acima. `Ledger.setReconciliationStatus(posting, status)` é a
metade do mesmo fluxo no nível do posting: um `ReconciliationStatus`
(`PENDING`, `CLEARED`, `RECONCILED`) rastreado por `Posting`, o mesmo
mecanismo que a maioria das ferramentas de contabilidade usa pra marcar
"este posting já foi batido contra um extrato bancário". Ele mora no
`Ledger`, não no próprio `Posting`, de propósito — `Posting` e
`JournalEntry` são os valores imutáveis deste kernel (veja "`JournalEntry`
não pode existir desbalanceado" acima), enquanto `Account` e `Ledger` são
suas duas entidades mutáveis; status de conciliação é um fato sobre o ciclo
de vida de um posting que muda depois que o lançamento já foi postado,
então pertence junto dos outros registros paralelos mutáveis que o
`Ledger` já possui (`accountsByCode` é o outro). `Ledger.reconciliationStatusOf(posting)`
retorna `PENDING` por padrão pra qualquer coisa que não tenha sido marcada
explicitamente de outro jeito.

Um plano de contas completo e trabalhado (contas bancárias até cada classe
de ativo acima) mora no histórico de commits/log de conversa deste projeto
em vez de ser duplicado aqui — as duas regras acima (posição vs. sem
posição, e qual `AccountType` segue de cada uma) são o que reaplicar a
qualquer nova classe de ativo que aparecer depois.

`Quantity` só responde "quantas unidades estão em posse" — valorizar uma
posição (quanto ela vale hoje, não quanto custou) precisa de um tipo de
preço-por-unidade que este kernel não tinha até `Price` ser adicionado:
`units` de uma commodity de posição valiam (`worth`) determinado `Money`,
numa `date`. Mantido como uma razão não-reduzida entre uma `Quantity` e um
`Money` já tipados — a mesma forma que uma linha de cotação real tem
("1000,00000 cotas valiam R$12.345,67") — em vez de uma taxa decimal que
arredondaria uma vez pra ser guardada e outra pra ser aplicada.
`Price.valueOf(Quantity)` ainda divide — uma valorização é um relatório,
não um posting, e nada aqui realimenta o resultado numa aritmética exata
posterior — mas essa divisão acontece exatamente uma vez, contra os dois
números que a própria cotação informou, e trunca em direção a zero como
qualquer outra divisão neste kernel.

## `TRADING`: movendo dinheiro entre duas moedas diferentes

`JournalEntry` fixa uma moeda para o lançamento inteiro: o `Money` de todo
`Posting` está nela (`Builder.posting()` checa isso). Um câmbio continua
sendo UM lançamento, nunca dois: a perna da conta na
outra moeda carrega o `Money` do lançamento (quanto custou) e uma `Quantity`
na moeda da própria conta (quanto chegou). A razão entre os dois é a
cotação usada, então a cotação faz parte do próprio fato, e não fica ao lado.

Opcionalmente, contas `AccountType.TRADING` (uma por moeda) acrescentam as
pernas que fazem CADA moeda fechar em zero dentro do lançamento — a opção de
livro que um formato de intercâmbio chama de contas de negociação. US$1.000,00 comprados a R$5,25:

```java
Account tradingBrl = ledger.openAccount("trading-brl", "BRL", brl, trading);
Account tradingUsd = ledger.openAccount("trading-usd", "USD", usd, trading);
tradingBrl.setType(AccountType.TRADING);
tradingUsd.setType(AccountType.TRADING);

JournalEntry.builder("cambio", hoje, "Câmbio BRL→USD", brl)
    .debit(nomadInvest, Money.of(525000, brl), Quantity.of(100000, usd))   // chegam US$1.000
    .credit(tradingUsd, Money.of(525000, brl), Quantity.of(100000, usd))   // ...saem do trading USD
    .debit(tradingBrl, Money.of(525000, brl))                              // entram R$5.250 no trading BRL
    .credit(nubankCC, Money.of(525000, brl))                               // ...saem da conta corrente
    .build();
```

`Ledger.balanceOf` soma o `Money` de cada posting, na moeda do seu
lançamento, então numa conta lançada em várias moedas ele as mistura.
`Ledger.unitsOf` é o saldo na commodity DA PRÓPRIA conta: `Money` nos
postings na própria moeda, `Quantity` nas pernas de câmbio. Aqui
`unitsOf(nomadInvest)` é US$1.000,00, `unitsOf(tradingUsd)` −US$1.000,00 e
`unitsOf(tradingBrl)` +R$5.250,00 (veja `ExchangeRateTest`).

Um par de lançamentos de moeda única ligados pelas contas de trading (um em
BRL, outro em USD) também continua funcionando. Essa forma não guarda
cotação no livro: é o `ExchangeRate` (`base` de uma moeda valia `quote` de
outra, numa `date`, razão não reduzida entre dois `Money`, como o `Price`) que
converte o saldo de `tradingUsd` de volta para BRL. Qualquer resíduo que
aparecer quando uma cotação *diferente* converter os mesmos saldos é
ganho/perda cambial real e observável.
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
  Quantity.java                   contagem de unidades imutável, não-negativa, na commodity da própria conta
  Price.java                     cotação de valorização: units valiam este Money, numa data
  ExchangeRate.java               cotação de valorização entre duas moedas, numa data
  ReconciliationStatus.java       pendente/compensado/conciliado, rastreado por Posting no Ledger
  Account.java                   nó do plano de contas; dona dos invariantes da árvore
  Posting.java                   uma perna de débito ou crédito de um lançamento; Money + Quantity opcional
  JournalEntry.java               o agregado de partida dobrada; válido por construção
  Ledger.java                     raiz do agregado: registro de commodities + plano de contas + lançamentos postados + conciliação
  LedgerArithmetic.java          mulDiv (trunc/floor/round) e rateio por maior resto, exatos
  LedgerException.java + 14 subclasses (ArithmeticRangeException carrega a falha como dado)
src/test/java/.../model/LedgerTest.java
src/test/java/.../model/PositionTest.java
src/test/java/.../model/PriceTest.java
src/test/java/.../model/ExchangeRateTest.java
src/test/java/.../model/LedgerChartBuildingTest.java
src/test/java/.../model/LedgerArithmeticTest.java
```

Rode assim: `mvn test` (precisa de um JDK 25 no `PATH`, ou aponte o
toolchain do Maven para um).

## Publicando uma versão

O kernel tem SemVer próprio e é publicado no GitHub Packages
(`https://maven.pkg.github.com/SiteTuneOrg/cornerstone-kernel`). Para publicar: suba o `<version>`
no `pom.xml`, mergeie e empurre a tag `vX.Y.Z` com o mesmo número. O workflow de publicação roda o
gate de cobertura e publica; uma tag que não bate com o pom falha antes de publicar qualquer coisa.
Uma versão já publicada não pode ser publicada de novo, então uma correção é um PATCH novo.

**Pendente:** esse workflow (`.github/workflows/publish.yml`) é aplicado pelo dono, porque a automação
deste repositório não consegue empurrar arquivos de workflow. Enquanto ele não estiver na `main`, uma tag
não publica nada e nada a confere contra o pom.

Ler o pacote pede credencial mesmo dentro da organização: um server `github` no
`~/.m2/settings.xml` com um token clássico com `read:packages`, ou um `mvn install` deste
repositório, que é o que o build local do server sempre fez.

## O que fica deliberadamente de fora

- **Sem banco de dados.** Explicitamente fora de escopo por direção do
  usuário neste spike — uma camada JDBC/ORM sobre `Ledger`/`Account`/`JournalEntry`
  é seu próprio projeto futuro, não colada aqui.
- Sem motor fiscal, sem rebalanceamento, sem cálculo de posições/custo
  médio, sem camada HTTP — o escopo continua limitado às classes e
  entidades de partida dobrada.
