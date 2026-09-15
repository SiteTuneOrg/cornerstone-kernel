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
 * <p>This is what closes the loop the {@code TRADING} account pattern
 * leaves open: two single-currency {@link JournalEntry} legs, bridged by a
 * pair of technical accounts, balance structurally on their own, but the
 * realized gain or loss when those balances are later compared needs an
 * explicit rate — nothing about the entries themselves records one.
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
        if (base.isZero()) {
            throw new IllegalArgumentException("ExchangeRate cannot have a zero base");
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
        long converted = Math.multiplyExact(amount.amount(), quote.amount()) / base.amount();
        return Money.of(converted, quote.commodity());
    }

    @Override
    public String toString() {
        return base + " = " + quote + " (" + date + ")";
    }
}
