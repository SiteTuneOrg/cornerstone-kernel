package com.cornerstone.ledger.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A quote: {@code base} of one currency was worth {@code quote} of another,
 * on {@code date} — e.g. "US$1,000.00 was R$5,000.00" on the day of an
 * exchange. Same shape as {@link Price}, for the same reason: an unreduced
 * ratio of two already-typed {@link Money} values, not a decimal rate that
 * would round twice.
 *
 * <p>An exchange recorded as ONE entry with a foreign leg (see {@link Posting})
 * already carries its rate in that leg's money/quantity ratio. This type is
 * for the other shape: two single-currency {@link JournalEntry} legs bridged
 * by {@code TRADING} accounts, which balance structurally on their own but
 * record no rate, so comparing those balances later needs an explicit one.
 */
public final class ExchangeRate {

    private final Money base;
    private final Money quote;
    private final LocalDate date;

    private ExchangeRate(Money base, Money quote, LocalDate date) {
        this.base = Objects.requireNonNull(base, "base");
        this.quote = Objects.requireNonNull(quote, "quote");
        this.date = Objects.requireNonNull(date, "date");
        base.commodity().requireKind(CommodityKind.CURRENCY, "ExchangeRate");
        quote.commodity().requireKind(CommodityKind.CURRENCY, "ExchangeRate");
        if (base.commodity().equals(quote.commodity())) {
            throw new IllegalArgumentException(
                    "ExchangeRate requires two different currencies, got " + base.commodity().code()
                            + " on both sides");
        }
        if (base.isZero() || quote.isZero()) {
            // No currency is worth nothing: a zero on either side is a rate
            // that was never read, and a zero quote would convert a whole
            // balance to 0 without a word.
            throw new IllegalArgumentException("ExchangeRate cannot have a zero side, got " + base + " = " + quote);
        }
    }

    public static ExchangeRate of(Money base, Money quote, LocalDate date) {
        return new ExchangeRate(base, quote, date);
    }

    public Money base() {
        return base;
    }

    public Money quote() {
        return quote;
    }

    public LocalDate date() {
        return date;
    }

    /**
     * Converts {@code amount} (must be in {@link #base()}'s currency) into
     * {@link #quote()}'s currency, at this rate. Division truncates toward
     * zero, the same convention {@code long} arithmetic uses everywhere
     * else in this kernel.
     */
    public Money convert(Money amount) {
        if (!amount.commodity().equals(base.commodity())) {
            throw new CurrencyMismatchException(base.commodity(), amount.commodity());
        }
        long converted = LedgerArithmetic.mulDiv(amount.amount(), quote.amount(), base.amount(), LedgerArithmetic.Rounding.TRUNC);
        return Money.of(converted, quote.commodity());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ExchangeRate other
                && base.equals(other.base) && quote.equals(other.quote) && date.equals(other.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, quote, date);
    }

    @Override
    public String toString() {
        return base + " = " + quote + " (" + date + ")";
    }
}
