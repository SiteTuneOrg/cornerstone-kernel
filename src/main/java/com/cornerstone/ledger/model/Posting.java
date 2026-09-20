package com.cornerstone.ledger.model;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * One leg of a {@link JournalEntry}: a debit or credit of {@link #amount()}
 * — and, for a position account or an exchange leg, a {@link #quantity()} — against
 * {@link #account()}.
 *
 * <p>A {@code Posting} carries an unsigned {@link Money} plus an explicit
 * {@link NormalSide}, rather than folding "how much" and "debit or credit"
 * into one signed number. {@link #signedAmount()} is the bridge back to a
 * single signed number when one is actually needed (e.g. computing a
 * running balance).
 *
 * <p><b>An account's own {@link Account#commodity()} is what it holds a
 * position IN, not what its money is denominated in.</b> {@code Money} on a
 * posting is always in the entry's currency; {@link #quantity()} is the
 * change in the account's own commodity, and it is present exactly when
 * those two differ:
 *
 * <ul>
 *   <li>A cash account posted in its own currency (BRL account, BRL entry):
 *       money IS the position, so there is no {@link Quantity}.
 *   <li>A cash account posted in another currency (USD account, BRL entry —
 *       an exchange in one entry, optionally with {@link AccountType#TRADING}
 *       legs): {@code Money} is the BRL value and the mandatory {@link Quantity}
 *       is the USD amount; their ratio is the rate the exchange used.
 *   <li>A brokerage position account (PETR4, BTC): {@code Money} is the
 *       cost/proceeds and the mandatory {@link Quantity} is the change in
 *       units held.
 * </ul>
 *
 * A mandatory quantity is never silently defaulted.
 */
public final class Posting {

    private final Account account;
    private final NormalSide side;
    private final Money amount;
    private final Quantity quantity;
    private final String memo;

    private Posting(Account account, NormalSide side, Money amount, Quantity quantity, String memo) {
        this.account = Objects.requireNonNull(account, "account");
        this.side = Objects.requireNonNull(side, "side");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.quantity = quantity;
        this.memo = memo;

        Commodity accountCommodity = account.commodity();
        if (accountCommodity.kind() == CommodityKind.CURRENCY && accountCommodity.equals(amount.commodity())) {
            if (quantity != null) {
                throw new IllegalArgumentException(
                        "account \"" + account.path() + "\" is posted in its own currency — money is the position, it does not accept a Quantity");
            }
        } else if (accountCommodity.kind() == CommodityKind.CURRENCY) { // posted in another currency: an exchange leg
            if (amount.commodity().kind() != CommodityKind.CURRENCY) {
                throw new CurrencyMismatchException(accountCommodity, amount.commodity());
            }
            if (quantity == null) {
                // Catches the mistake a multi-currency importer will
                // otherwise make silently: posting a USD amount to a
                // BRL-denominated cash account with nothing saying how much
                // BRL actually moved. The entry still balances (its OWN
                // currency check only compares postings to each other) but
                // the account's balance in its own currency would be unknown.
                throw new CurrencyMismatchException(accountCommodity, amount.commodity());
            }
            if (!quantity.commodity().equals(accountCommodity)) {
                throw new CurrencyMismatchException(accountCommodity, quantity.commodity());
            }
            if (amount.isZero() || quantity.isZero()) {
                // An exchange always moves both sides: their ratio IS the rate. A zero side is a rate
                // of 0 or infinity, and two zero-money legs balance each other at 0 == 0 while
                // minting foreign currency out of nothing — the single-leg guard in
                // JournalEntry.build() never sees them. Unlike a position (a split moves units with
                // no money), a currency has no legitimate one-sided move inside another currency's
                // entry: a USD-to-USD transfer is an entry in USD.
                throw new IllegalArgumentException(
                        "exchange leg on \"" + account.path() + "\" must move both the entry's currency and "
                                + accountCommodity.code() + " (got " + amount + " and " + quantity + ")");
            }
        } else { // ASSET: a position account.
            if (amount.commodity().kind() != CommodityKind.CURRENCY) {
                throw new IllegalArgumentException(
                        "a position's value must be in a CURRENCY, got " + amount.commodity().code()
                                + " (another asset) on " + account.path());
            }
            if (quantity == null) {
                // Fail-closed on purpose, same doctrine as the R$60
                // fabrication incident this project's CLAUDE.md documents
                // elsewhere: a position account move with no quantity would
                // silently corrupt the SHARE COUNT — which is exactly what
                // this type exists to prevent — while looking like a
                // perfectly ordinary, successfully-posted entry.
                throw new IllegalArgumentException(
                        "position account \"" + account.path() + "\" requires a Quantity — none was given");
            }
            if (!quantity.commodity().equals(accountCommodity)) {
                throw new CurrencyMismatchException(accountCommodity, quantity.commodity());
            }
        }

        if (amount.isZero() && (quantity == null || quantity.isZero())) {
            // Neither leg moved anything — no side for a no-op to belong to.
            // A quantity-only move (e.g. a fractional-share correction with
            // no monetary value) is legitimate and must NOT trip this; only
            // "literally nothing changed" is refused.
            throw new IllegalArgumentException("posting with no effect at all (amount and quantity both zero) on " + account.path());
        }
    }

    public static Posting debit(Account account, Money amount) {
        return new Posting(account, NormalSide.DEBIT, amount, null, null);
    }

    public static Posting credit(Account account, Money amount) {
        return new Posting(account, NormalSide.CREDIT, amount, null, null);
    }

    public static Posting debit(Account account, Money amount, String memo) {
        return new Posting(account, NormalSide.DEBIT, amount, null, memo);
    }

    public static Posting credit(Account account, Money amount, String memo) {
        return new Posting(account, NormalSide.CREDIT, amount, null, memo);
    }

    public static Posting debit(Account account, Money amount, Quantity quantity) {
        return new Posting(account, NormalSide.DEBIT, amount, quantity, null);
    }

    public static Posting credit(Account account, Money amount, Quantity quantity) {
        return new Posting(account, NormalSide.CREDIT, amount, quantity, null);
    }

    public static Posting debit(Account account, Money amount, Quantity quantity, String memo) {
        return new Posting(account, NormalSide.DEBIT, amount, quantity, memo);
    }

    public static Posting credit(Account account, Money amount, Quantity quantity, String memo) {
        return new Posting(account, NormalSide.CREDIT, amount, quantity, memo);
    }

    public Account account() {
        return account;
    }

    public NormalSide side() {
        return side;
    }

    public Money amount() {
        return amount;
    }

    /** Present iff the account's commodity differs from {@link #amount()}'s — a position or an exchange leg; see the class doc. */
    public Optional<Quantity> quantity() {
        return Optional.ofNullable(quantity);
    }

    public Optional<String> memo() {
        return Optional.ofNullable(memo);
    }

    /** This posting's effect on {@link #account()}'s money balance, signed by its own nature. */
    public long signedAmount() {
        return (long) account.nature().signFor(side) * amount.amount();
    }

    /** This posting's effect on {@link #account()}'s unit count, signed by its own nature — empty when {@link #quantity()} is. */
    public OptionalLong signedQuantity() {
        return quantity == null
                ? OptionalLong.empty()
                : OptionalLong.of((long) account.nature().signFor(side) * quantity.units());
    }

    @Override
    public String toString() {
        String base = side + " " + amount + " " + account.path();
        return quantity == null ? base : base + " (" + quantity + ")";
    }
}
