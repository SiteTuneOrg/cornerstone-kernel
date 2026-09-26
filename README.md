# Ledger Java kernel

*[Leia em português](README.pt-BR.md)*

A concrete double-entry ledger kernel in Java: the double-entry classes and
entities, and nothing else. **No database yet** — this is the kernel only,
plain Java 25 with zero dependencies beyond the JDK and JUnit for tests.
Persistence is a deliberately separate future project built on top of this
one's public types, not a JPA/JDBC layer bolted onto it here.

This is a standalone project, not part of any larger app's build — just a
plain Maven module you clone and run on its own (`mvn test`).

## Design decisions

Each of these is a real design fork, not an arbitrary choice:

- **`Money` is unsigned; sign lives on `Posting.side()`.** Folding "how
  much" and "debit or credit" into one signed number is compact, but every
  reader has to remember the sign convention. Here `Money` cannot be
  negative (the constructor throws), and a `Posting` is explicitly
  `Posting.debit(account, amount)` or `Posting.credit(account, amount)`.
  "Which leg is the debit" is answered by reading the call, never by
  squinting at a sign.

- **`AccountNature` owns the accounting rule, not a lookup table.**
  `AccountNature.ASSET.signFor(NormalSide.CREDIT)` returns `-1` because the
  enum constant itself knows its normal side (`NormalSide.DEBIT`) and
  delegates the sign rule to `NormalSide.signFor`. The rule for "what does a
  credit do to an asset account" lives as real code on the type, not as an
  implicit convention callers have to apply by hand.

- **`Account` is a real tree, and enforces its own invariants.** Nature
  inheritance, cycle-freedom, and unique sibling names aren't checked by a
  separate pass run over a flat list after the fact. `Account.addChild(child)`
  refuses a nature mismatch (`NatureMismatchException`) or a duplicate name
  (`DuplicateAccountException`) at the moment the edge would be created —
  there is no other way to attach a child, so a cycle is structurally
  unreachable rather than something a separate walk has to detect.

- **`JournalEntry` cannot exist unbalanced.** It has no public constructor —
  only `JournalEntry.Builder`, and `.build()` validates (≥ 2 postings, every
  touched account `acceptsPostings()`, debits == credits) before an instance
  is ever handed back. "Valid by construction" removes the entire failure
  mode of forgetting to call a validator. The tradeoff is that `JournalEntry`
  is genuinely a value once built (no setters), while `Account` stays a
  mutable entity with identity, because a chart of accounts is edited over
  years and a journal entry, once posted, is not.

- **`Ledger` is the aggregate root, not a data bag.** `Ledger.openAccount(...)`
  and `Ledger.post(entry)` are the *only* way to grow the object graph, so it
  can actually guarantee — not just document — that no `JournalEntry` in
  `ledger.entries()` references an `Account` this `Ledger` doesn't own
  (`Ledger.post` checks object identity, not just a matching id string).

- **A small exception hierarchy (`sealed class LedgerException`) instead of
  a tagged enum.** Java callers distinguish failures by `catch` clause, so
  this kernel has one exception type per actual failure mode
  (`UnbalancedEntryException`, `AccountNotPostableException`,
  `NatureMismatchException`, `DuplicateAccountException`,
  `DuplicateAccountCodeException`, `CurrencyMismatchException`,
  `CommodityConflictException`, `AccountIdConflictException`,
  `ForeignAccountException`, `ForeignPostingException`,
  `DuplicateJournalEntryException`, `InsufficientPostingsException`,
  `CyclicAccountException`, `ArithmeticRangeException`) — each carrying exactly the data needed to
  format its own message, sealed so a `switch` over them is exhaustive. Kept
  honest on a pass through `Ledger`: a handful of invariant violations
  (a conflicting commodity registration, an id reused with a different
  shape, an id already in use, a posting against an account owned by a
  different `Ledger`) were still throwing a bare `IllegalArgumentException`
  outside the sealed hierarchy — the three exceptions above closed that gap.
  Plain `IllegalArgumentException` stays where it already was for ordinary
  constructor argument validation (`Money`/`Quantity` refusing a negative
  value, `Account` refusing a blank name) — that's a different kind of
  failure than a ledger invariant, and the standard Java convention for it.

## Positions: an account's commodity is what it HOLDS, not what it's priced in

Added once a real CSV import needed to represent "how many shares of X are
held", not just "how much money moved" — matching a pattern that already
exists in the domain of position tracking: a security has its own
price-tracking field, separate from a decimal-precision setting for how
its quantity is displayed.

- **`Account.commodity()` is overloaded on purpose.** For a cash account
  it's a currency (BRL, USD); a `Posting` against it in that same currency
  carries only `Money` — money IS the position there (a posting from an
  entry in another currency is an exchange leg, see `TRADING` below). For a brokerage
  position account it's the SECURITY itself
  (`Commodity.asset("PETR4", 1)`): `Money` on such a posting is the
  cost/proceeds in whatever currency the trade happened in (which does NOT
  have to equal the account's own commodity), and a new `Quantity` — the
  asset-side counterpart of `Money`, same non-negative-by-construction shape
  — carries the actual unit change, in the security's own commodity.
- **`Posting` enforces which shape applies: a `Quantity` is present exactly
  when the account's commodity differs from the money's.** A currency account
  posted in its own currency accepts `Money` and REFUSES a `Quantity`
  outright (`Posting.debit(cashAccount, money, quantity)` throws
  `IllegalArgumentException` before construction). A currency account posted
  in ANOTHER currency is an exchange leg and REQUIRES a `Quantity` in its own
  currency (see `TRADING` below), with BOTH sides non-zero: their ratio is the
  rate, and two zero-money legs would otherwise balance at 0 == 0 while minting
  foreign currency. An asset-kind account
  REQUIRES a `Quantity` matching its own commodity — omitting it is refused,
  never silently treated as "no position change", because that is exactly
  the kind of silent corruption this project's `CLAUDE.md` warns about
  elsewhere (the incident where an annulled result field quietly fabricated
  R$60 of tax). `Ledger.quantityOf(account)` returns `OptionalLong.empty()`
  for a non-position account, not zero — "never traded" and "confirmed zero
  position" must stay distinguishable.
- **A single quantity-only posting is a legitimate, complete `JournalEntry`.**
  A stock split or a zero-cost bonus share issue moves units with NO money at
  all — there is no second leg for it to balance against, because nothing of
  monetary value changed anywhere. `JournalEntry.Builder.build()` allows
  exactly one posting when it is on a position (asset-kind) account with
  `Money.zero(...)` plus a non-zero `Quantity` — never on a cash account,
  where a lone zero-money exchange leg would mint currency. `Posting`'s own "amount AND quantity both zero" guard already
  rules out the degenerate case of a posting that truly does nothing, so
  admitting one posting here doesn't reopen that hole.

## `RECEIVABLE`/`PAYABLE`: not every investment is a position

`AccountType` started with eight common account species under `ASSET`
(`BANK`, `CASH`, `STOCK`, `MUTUAL`) and `LIABILITY` (`CREDIT`). `RECEIVABLE`
and `PAYABLE` were added once modeling a real Brazilian chart of accounts
surfaced a case those eight don't cover cleanly: an investment with no
publicly-quoted unit to hold a `Quantity` of.

The dividing line for any asset, investment or not, is not "is this
fixed income or variable income" — it's **does it have a publicly-quoted
unit whose count is worth tracking separately from its money value**:

- **Has one → a position account (`STOCK`/`MUTUAL`, with `Quantity`).**
  BR stocks, US stocks, ETFs and FIIs all trade on an exchange as discrete
  units (`STOCK`); Tesouro Direto and a cota-tracked previdência privada
  (PGBL/VGBL) trade in fractional units against a daily quote (`MUTUAL`).
  Same shape as any other position account: the account's own commodity is
  the security itself, a purchase carries `Money` in whatever currency the
  trade happened in plus a `Quantity` in that security's commodity.

  ```java
  Commodity petr4 = ledger.asset("PETR4", 1);
  Account petr4Acc = ledger.openAccount("petr4", "PETR4", petr4, acoesBr);
  petr4Acc.setType(AccountType.STOCK);

  Commodity cotaPrevidencia = ledger.asset("BRASILPREV-RT-FIX-100", 100_000);
  Account pgbl = ledger.openAccount("pgbl-brasilprev", "PGBL Brasilprev", cotaPrevidencia, previdencia);
  pgbl.setType(AccountType.MUTUAL);
  ```

- **Has none → `RECEIVABLE` (asset) or `PAYABLE` (liability), no `Quantity`
  at all.** CDB, LCI, LCA and private bonds don't have a market-quoted unit
  — what exists is a money value that grows by accrued interest, the same
  shape as a savings account. Forcing a `Quantity` onto them would mean
  inventing a fake unit with no real quote behind it. `RECEIVABLE` reads
  better than the generic `ASSET` for these — "money someone owes you
  back" — and is what a shared/joint-account "due from spouse" leg
  (see the `Ledger`-per-person section above) would use on the other
  partner's side, with `PAYABLE` as its liability-side mirror.

  ```java
  Commodity brl = ledger.currency("BRL", 100);
  Account cdbXp = ledger.openAccount("cdb-xp-2027", "CDB XP 2027", brl, rendaFixaPrivada);
  cdbXp.setType(AccountType.RECEIVABLE);

  // rendimento: crédito numa conta INCOME, débito aqui — sem Quantity, é só dinheiro
  Posting.debit(cdbXp, Money.of(1234, brl));
  ```

`Account.code()` is meant to serve as a stable, user-assigned correlation
key — e.g. matching an OFX import's account references, as discussed above.
`Ledger.setReconciliationStatus(posting, status)` is the posting-level half
of that same workflow: a `ReconciliationStatus` (`PENDING`, `CLEARED`,
`RECONCILED`) tracked per `Posting`, the same mechanism most accounting
tools use to mark "this posting has been matched against a bank statement".
It lives on `Ledger`, not on `Posting` itself, on purpose — `Posting` and
`JournalEntry` are this kernel's immutable values (see "`JournalEntry`
cannot exist unbalanced" above), while `Account` and `Ledger` are its two
mutable entities; reconciliation status is a fact about a posting's
lifecycle that changes after the entry is posted, so it belongs with the
other mutable, `Ledger`-owned side registries (`accountsByCode` is the
other one). `Ledger.reconciliationStatusOf(posting)` defaults to `PENDING`
for anything not explicitly marked otherwise.

A full worked chart (bank accounts through every asset class above) lives
in this project's commit history / chat log rather than duplicated here —
the two rules above (position vs. no position, and which `AccountType`
follows from each) are what to reapply to any new asset class that shows
up later.

`Quantity` only ever answers "how many units are held" — valuing a
position (what it's worth today, not what it cost) needs a price-per-unit
type this kernel didn't have until `Price` was added: `units` of a
position commodity were `worth` some `Money`, on a `date`. Kept as an
unreduced ratio of an already-typed `Quantity` and `Money` — the same shape
a real quote line has ("1000,00000 cotas valiam R$12.345,67") — rather than
a decimal rate that would round once to be stored and again to be applied.
`Price.valueOf(Quantity)` still divides — a valuation is a report, not a
posting, and nothing here feeds the result back into further exact
arithmetic — but that division happens exactly once, against the two
numbers the quote itself reported, and truncates toward zero like every
other division in this kernel.

## `TRADING`: moving money between two different currencies

`JournalEntry` pins one currency for the whole entry: every `Posting`'s
`Money` is in it (`Builder.posting()` checks this). An exchange is still ONE
entry, never two: the leg on the account in the other
currency carries the entry's `Money` (what it cost) and a `Quantity` in the
account's own currency (what arrived). The ratio of the two is the rate the
exchange used, so the rate lives in the fact itself, not beside it.

Optionally, `AccountType.TRADING` accounts (one per currency) add the legs
that make EACH currency net to zero inside the entry — the book-wide option
an interchange format calls trading accounts. US$1.000,00 bought at R$5,25:

```java
Account tradingBrl = ledger.openAccount("trading-brl", "BRL", brl, trading);
Account tradingUsd = ledger.openAccount("trading-usd", "USD", usd, trading);
tradingBrl.setType(AccountType.TRADING);
tradingUsd.setType(AccountType.TRADING);

JournalEntry.builder("cambio", hoje, "Câmbio BRL→USD", brl)
    .debit(nomadInvest, Money.of(525000, brl), Quantity.of(100000, usd))   // US$1.000 arrive
    .credit(tradingUsd, Money.of(525000, brl), Quantity.of(100000, usd))   // ...leave USD trading
    .debit(tradingBrl, Money.of(525000, brl))                              // R$5.250 enter BRL trading
    .credit(nubankCC, Money.of(525000, brl))                               // ...leave checking
    .build();
```

`Ledger.balanceOf` adds each posting's `Money`, in its entry's currency, so
for an account posted in several currencies it mixes them. `Ledger.unitsOf`
is the balance in the account's OWN commodity: `Money` for postings in its
own currency, `Quantity` for exchange legs. Here `unitsOf(nomadInvest)` is
US$1.000,00, `unitsOf(tradingUsd)` −US$1.000,00 and `unitsOf(tradingBrl)`
+R$5.250,00 (see `ExchangeRateTest`).

A pair of single-currency entries bridged by the trading accounts (one entry
in BRL, one in USD) still works too. That shape keeps no rate in the book:
`ExchangeRate` (`base` of one currency was worth `quote` of another, on a
`date`, an unreduced ratio of two `Money` values like `Price`) is what then
converts `tradingUsd`'s balance back into BRL. Any residual that shows up
when a *different* rate converts the same balances is real, observable FX
gain/loss.
## `Ledger` owns the commodity registry and idempotent chart-building

Added once a real importer started building a chart of accounts
incrementally from CSV rows and needed "the account named X, created on
first use" — every early version of that logic kept its own
`Map<String, Account>` cache in front of a lookup `Ledger` already does
internally (`accountsById`). Multiplied across future layers (salário,
orçamento — the ones the user says are coming), that's the same cache
reimplemented once per consumer.

- **`Ledger.currency(code, scale)` / `Ledger.asset(code, scale)`** register a
  `Commodity` once and return the same instance on repeat calls — closing a
  real gap, not just adding convenience: `Commodity.equals()` compares only
  `code`, so two independently constructed `Commodity.currency("BRL", 100)`
  and `Commodity.currency("BRL", 1)` would compare EQUAL despite disagreeing
  on scale, and `Money` arithmetic (which checks `equals`, not scale) would
  silently mix them. A second, conflicting registration for the same code is
  refused outright.
- **`Ledger.rootAccountOrCreate`/`Ledger.accountOrCreate`** return the
  existing account for an id if present, otherwise create it — refusing (not
  silently substituting) if a REUSED id was requested with a different name,
  nature, commodity or parent. That refusal is what actually caught a real
  bug: a position-account id that didn't fold in currency let a single
  USD-priced row silently share an account with hundreds of BRL-priced
  ones for the same instrument, until this method
  started refusing the mismatch instead of an ad hoc cache silently keeping
  whichever shape was created first. See `LedgerChartBuildingTest`.

## A `Ledger` is one closed book, not necessarily one person

The intended usage for this kernel is one `Ledger` per person: each family
member gets their own instance, isolated from the others'. That isolation is
not just a naming convention — `Ledger.post(entry)` refuses any `Posting`
whose `Account` isn't owned by that exact `Ledger` (`Account` identity is
checked, not just a matching id string, see `requireOwnedByThisLedger`), so
one person's `JournalEntry` structurally cannot touch another person's chart
of accounts. Likewise, `Ledger.setAccountCode`'s global uniqueness for
`Account.code()` is global *within one `Ledger`* — two different people's
ledgers can reuse the same code with no conflict, because they're different
books.

The reason this is "one `Ledger` per person" rather than "one `Ledger` per
family" is the same reason `JournalEntry` cannot exist unbalanced: every
entry in a book should belong to one accountable party. A family ledger
where anyone's postings land in one shared pile stops answering "whose
money is this" as cleanly as the model above answers "which side is the
debit".

**Joint accounts don't need a new concept — they need another `Ledger`.**
Nothing here requires `Ledger` to map 1:1 onto a person; it maps onto
"a book that closes on its own". A couple's shared checking account is
naturally its own book: a third `Ledger` (`ledger-conjuge-conjunta`, say)
that owns just that account, with each partner's contribution posted into
it from their side. That keeps every `Ledger`'s invariant intact —
`accountsById` never has to represent fractional ownership, and
`Account.addChild`/`acceptsPostings` don't need a concept of "shared"
they don't have today.

The other option — putting the joint account inside one partner's own
`Ledger` and tracking the other partner's share with a synthetic
"due to/due from spouse" account — is the classical accounting answer to
shared ownership and works fine here too (it's just two more `Account`s and
ordinary `Posting`s, nothing new to build). It's more manual per couple than
a dedicated shared `Ledger`, but doesn't require deciding this up front.

Consolidating across a family's several `Ledger`s (net worth across
everyone, say) is explicitly a job for a layer above this kernel — nothing
here aggregates across `Ledger` instances, the same way nothing here talks
to a database yet.

## What's here

```
src/main/java/com/cornerstone/ledger/model/
  NormalSide.java               debit/credit and the sign rule between them
  AccountNature.java            asset/liability/equity/revenue/expense + normal side
  AccountType.java              account species → nature
  Commodity.java                 currency or asset a Money/Quantity is denominated in
  Money.java                     immutable, non-negative amount + commodity
  Quantity.java                   immutable, non-negative unit count of an account's own commodity
  Price.java                     valuation quote: units were worth this Money, on a date
  ExchangeRate.java               valuation quote between two currencies, on a date
  ReconciliationStatus.java       pending/cleared/reconciled, tracked per Posting on Ledger
  Account.java                   chart-of-accounts node; owns tree invariants
  Posting.java                   one debit or credit leg of an entry; Money + optional Quantity
  JournalEntry.java               the double-entry aggregate; valid by construction
  Ledger.java                     aggregate root: commodity registry + chart of accounts + posted entries + reconciliation
  LedgerArithmetic.java          mulDiv (trunc/floor/round) and largest-remainder apportion, exact
  LedgerException.java + 14 subclasses (ArithmeticRangeException carries its failure as data)
src/test/java/.../model/LedgerTest.java
src/test/java/.../model/PositionTest.java
src/test/java/.../model/PriceTest.java
src/test/java/.../model/ExchangeRateTest.java
src/test/java/.../model/LedgerChartBuildingTest.java
src/test/java/.../model/LedgerArithmeticTest.java
```

Run it: `mvn test` (needs a JDK 25 on `PATH`, or point Maven's toolchain at one).

## Releasing

The kernel keeps its own SemVer and is published to GitHub Packages
(`https://maven.pkg.github.com/SiteTuneOrg/cornerstone-kernel`). To release: bump `<version>` in
`pom.xml`, merge, and push the tag `vX.Y.Z` with that same number. The publish workflow runs the
coverage gate and deploys; a tag that does not match the pom fails before anything is published.
A version already published cannot be published again, so a fix is a new PATCH.

Reading the package needs credentials even inside the organization: a `github` server in
`~/.m2/settings.xml` with a classic token that has `read:packages`, or `mvn install` of this
repository, which is what a local build of the server has always done.

## What this deliberately leaves out

- **No database.** Explicitly out of scope per the user's direction on this
  spike — a JDBC/ORM layer on top of `Ledger`/`Account`/`JournalEntry` is
  its own future project, not bolted on here.
- No fiscal engine, no rebalancing, no positions/cost-basis walk, no HTTP
  layer — this stays scoped to the double-entry classes and entities only.
