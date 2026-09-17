# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A standalone double-entry bookkeeping kernel: plain Java 25, a single Maven module, zero compile-time dependencies beyond the JDK (JUnit 5 for tests only). **No persistence, no HTTP, no fiscal engine** — a database layer is a separate future project built on top of the public types, so don't add JPA/JDBC or other dependencies here. `README.md` (with a pt-BR translation in `README.pt-BR.md`, keep both in sync) documents the reasoning behind every design decision; read the relevant section before changing a core invariant. `MANIFESTO.md` (bilingual, ported from the `portfolio` repo) states the product's purpose and principles; its "O papel do kernel" section translates them into kernel terms.

## Commands

Use the Maven Wrapper (script-only distribution; needs JDK 25 on `PATH`):

```bash
./mvnw test                                   # run all tests
./mvnw test -Dtest=LedgerTest                 # one test class
./mvnw test -Dtest=LedgerTest#methodName      # one test method
./mvnw verify                                 # tests + JaCoCo coverage gate (>= 80% line coverage, bundle-wide)
./mvnw verify -Djacoco.haltOnFailure=false    # coverage report without failing (target/site/jacoco/)
```

There is no linter/formatter configured.

## Commit gate

`.githooks/pre-commit` runs `./mvnw -q verify` whenever staged changes touch `src/`, so a commit fails if coverage drops below 80% (enable per clone with `git config core.hooksPath .githooks`). A Claude Code PreToolUse hook (`.claude/hooks/block-hook-bypass.sh`) blocks `git commit --no-verify`/`-n` and overriding `core.hooksPath` — fix coverage instead; if a bypass is really needed, ask the user to commit themselves. New code needs tests that keep the gate green.

CI (`.github/workflows/ci.yml`) runs the same `./mvnw verify` gate on JDK 25 (Temurin) for pushes to `main` and every pull request, and uploads the JaCoCo report as an artifact. The pre-commit hook is opt-in per clone; CI is the gate that cannot be skipped.

## Architecture

Everything lives in `com.cornerstone.ledger.model`. The key ideas span several classes:

- **Amounts are unsigned `long`s in the commodity's smallest unit.** `Money` and `Quantity` refuse negatives; the sign lives on `Posting.side()` (`NormalSide` DEBIT/CREDIT), and `AccountNature` (via its normal side) decides how a side affects a balance. All arithmetic is exact integer math: `Math.*Exact` for add/subtract, `LedgerArithmetic.mulDiv`/`apportion` (BigInteger intermediates, explicit `Rounding`) for scaling/splitting. No `double`, no `BigDecimal` rates — `Price` and `ExchangeRate` are kept as unreduced ratios of two typed values and divide exactly once, truncating toward zero.
- **Immutable values vs. mutable entities.** `Money`, `Quantity`, `Commodity`, `Posting`, `JournalEntry`, `Price`, `ExchangeRate` are values. `Account` (a tree node) and `Ledger` are the only mutable entities. Post-posting lifecycle state (e.g. `ReconciliationStatus`, `accountsByCode`) lives in side registries on `Ledger`, not on `Posting`.
- **Valid by construction.** `JournalEntry` has no public constructor; `JournalEntry.Builder.build()` enforces ≥ 2 postings (except a single quantity-only posting with zero money, e.g. a stock split), postable accounts, one currency per entry, and debits == credits. `Account.addChild` is the only way to link accounts and rejects nature mismatches/duplicate sibling names at edge creation.
- **`Ledger` is the aggregate root.** Growth only through `Ledger` (`currency`/`asset` commodity registry, `openAccount`, `rootAccountOrCreate`/`accountOrCreate`, `post`). It checks account ownership by object identity, refuses conflicting commodity registrations (since `Commodity.equals` compares only `code`), and makes `*OrCreate` idempotent but refusing when a reused id comes with a different shape. One `Ledger` = one closed book (per person, or a separate book for a joint account); nothing aggregates across ledgers.
- **Positions.** An account's commodity is what it *holds*. A `Posting` carries a `Quantity` exactly when the account's commodity differs from the money's: required for asset-kind accounts (`STOCK`/`MUTUAL`) and for cross-currency exchange legs, refused for a currency account posted in its own currency. `Ledger.balanceOf` sums `Money` in entry currency; `Ledger.unitsOf` sums in the account's own commodity; `quantityOf` returns `OptionalLong.empty()` (not 0) for non-position accounts. Investments with no quoted unit (CDB, LCI…) use `RECEIVABLE`/`PAYABLE` with no `Quantity`. Multi-currency exchanges follow GnuCash's model, optionally with per-currency `AccountType.TRADING` accounts (see `ExchangeRateTest`).
- **Exceptions.** `LedgerException` is a `sealed`, unchecked hierarchy with one subclass per ledger-invariant failure, each carrying the data for its message. Adding a new failure mode means a new subclass *and* updating the `permits` clause (and the lists in the Javadoc and README). Plain `IllegalArgumentException` is reserved for ordinary constructor argument validation (negative `Money`, blank account name). Never let a mismatch be silently accepted or defaulted — refusing is the established behavior.
