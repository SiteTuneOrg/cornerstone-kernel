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
  `DuplicateAccountCodeException`, `CurrencyMismatchException`) — each
  carrying exactly the data needed to format its own message, sealed so a
  `switch` over them is exhaustive.

## Positions: an account's commodity is what it HOLDS, not what it's priced in

Added once a real CSV import needed to represent "how many shares of X are
held", not just "how much money moved" — matching a pattern that already
exists in the domain of position tracking: a security has its own
price-tracking field, separate from a decimal-precision setting for how
its quantity is displayed.

- **`Account.commodity()` is overloaded on purpose.** For a cash account
  it's a currency (BRL, USD) and every `Posting` against it carries `Money`
  in that same currency — money IS the position there. For a brokerage
  position account it's the SECURITY itself
  (`Commodity.asset("PETR4", 1)`): `Money` on such a posting is the
  cost/proceeds in whatever currency the trade happened in (which does NOT
  have to equal the account's own commodity), and a new `Quantity` — the
  asset-side counterpart of `Money`, same non-negative-by-construction shape
  — carries the actual unit change, in the security's own commodity.
- **`Posting` enforces which shape applies, by checking `account.commodity().kind()`.**
  A currency-kind account accepts `Money` in that currency and REFUSES a
  `Quantity` outright (`Posting.debit(cashAccount, money, quantity)` throws
  `IllegalArgumentException` before construction). An asset-kind account
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
  exactly one posting when it is `Money.zero(...)` plus a non-zero
  `Quantity` — `Posting`'s own "amount AND quantity both zero" guard already
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

A full worked chart (bank accounts through every asset class above) lives
in this project's commit history / chat log rather than duplicated here —
the two rules above (position vs. no position, and which `AccountType`
follows from each) are what to reapply to any new asset class that shows
up later.

## `TRADING`: moving money between two different currencies

`JournalEntry` pins one `Commodity` for the whole entry — every `Posting`
in it must be in that same currency (`Builder.posting()` checks this before
adding one). That is deliberate (see `CurrencyMismatchException`'s job
elsewhere in this doc), but it means a single `JournalEntry` cannot express
"R$5.000 leaves a BRL account, US$1.000 lands in a USD account" — there is
no single currency both legs could share.

`AccountType.TRADING` (`AccountNature.ASSET`, no `Quantity`) exists to make
that a *pair* of single-currency entries instead, bridged by one technical
account per currency — a pattern sometimes called a Trading Account. An
exchange from a BRL checking account into a USD brokerage account, at
R$5,00/US$1,00:

```java
Account tradingBrl = ledger.openAccount("trading-brl", "Câmbio BRL", brl, trading);
Account tradingUsd = ledger.openAccount("trading-usd", "Câmbio USD", usd, trading);
tradingBrl.setType(AccountType.TRADING);
tradingUsd.setType(AccountType.TRADING);

// entry 1 — BRL leg only
JournalEntry.builder("cambio-1", hoje, "Câmbio BRL→USD (saída)", brl)
    .credit(nubankCC, Money.of(500000, brl))
    .debit(tradingBrl, Money.of(500000, brl))
    .build();

// entry 2 — USD leg only
JournalEntry.builder("cambio-2", hoje, "Câmbio BRL→USD (entrada)", usd)
    .credit(tradingUsd, Money.of(100000, usd))
    .debit(nomadInvest, Money.of(100000, usd))
    .build();
```

No structural change was needed to support this — `Ledger.balanceOf()`
already documents that a negative balance (a credit on an account whose
normal side is debit) is a legitimate, reportable state, not an error. That
is exactly what `tradingUsd` ends up with here: +R$5.000 on `tradingBrl`,
−US$1.000 on `tradingUsd`. Converted at the SAME rate the trade used
(1.000 × 5,00 = 5.000), the pair exactly cancels — the trading accounts net
to zero in a common currency for this one exchange. Any residual that shows
up later, if a *different* rate is used to convert them, is real,
observable FX gain/loss — this kernel doesn't compute that (no reporting
layer yet), but the data it stores is enough for one to, later.

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
  Quantity.java                   immutable, non-negative unit count of an ASSET commodity
  Account.java                   chart-of-accounts node; owns tree invariants
  Posting.java                   one debit or credit leg of an entry; Money + optional Quantity
  JournalEntry.java               the double-entry aggregate; valid by construction
  Ledger.java                     aggregate root: commodity registry + chart of accounts + posted entries
  LedgerException.java + 6 subclasses
src/test/java/.../model/LedgerTest.java
src/test/java/.../model/PosicaoTest.java
src/test/java/.../model/LedgerChartBuildingTest.java
```

Run it: `mvn test` (needs a JDK 25 on `PATH`, or point Maven's toolchain at one).

## What this deliberately leaves out

- **No database.** Explicitly out of scope per the user's direction on this
  spike — a JDBC/ORM layer on top of `Ledger`/`Account`/`JournalEntry` is
  its own future project, not bolted on here.
- No fiscal engine, no rebalancing, no positions/cost-basis walk, no HTTP
  layer — this stays scoped to the double-entry classes and entities only.
