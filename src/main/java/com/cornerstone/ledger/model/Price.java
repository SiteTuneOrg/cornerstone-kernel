package com.cornerstone.ledger.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A quote: {@code units} of a position commodity were worth {@code worth},
 * on {@code date}. The valuation counterpart of {@link Posting}'s
 * cost/proceeds split — {@code worth} answers "what is it worth today",
 * not "what did it cost when bought".
 *
 * <p>Deliberately not a decimal rate. {@code units}/{@code worth} is an
 * unreduced ratio of two already-typed, exact values (a {@link Quantity}
 * and a {@link Money}) — the same shape a real quote line has ("1000,00000
 * cotas valiam R$12.345,67"), not a synthetic "price per whole unit"
 * decimal that would round once to be stored and again to be applied.
 * {@link #valueOf(Quantity)} still divides — a valuation is a report, not
 * a posting, and nothing here feeds a division result back into further
 * exact arithmetic — but that division happens exactly once, against the
 * two numbers the quote itself reported.
 */
public final class Price {

    private final Quantity units;
    private final Money worth;
    private final LocalDate date;

    private Price(Quantity units, Money worth, LocalDate date) {
        this.units = Objects.requireNonNull(units, "units");
        this.worth = Objects.requireNonNull(worth, "worth");
        this.date = Objects.requireNonNull(date, "date");
        units.commodity().requireKind(CommodityKind.ASSET, "Price");
        worth.commodity().requireKind(CommodityKind.CURRENCY, "Price");
        if (units.isZero()) {
            throw new IllegalArgumentException("Price cannot quote zero units");
        }
    }

    public static Price of(Quantity units, Money worth, LocalDate date) {
        return new Price(units, worth, date);
    }

    public Quantity units() {
        return units;
    }

    public Money worth() {
        return worth;
    }

    public LocalDate date() {
        return date;
    }

    /**
     * The monetary value of {@code quantity} at this quote — a valuation,
     * not a posting: nothing here checks it against any {@link JournalEntry}.
     * Division truncates toward zero, the same convention {@code long}
     * arithmetic uses everywhere else in this kernel.
     */
    public Money valueOf(Quantity quantity) {
        if (!quantity.commodity().equals(units.commodity())) {
            throw new CurrencyMismatchException(units.commodity(), quantity.commodity());
        }
        long amount = Math.multiplyExact(quantity.units(), worth.amount()) / units.units();
        return Money.of(amount, worth.commodity());
    }

    @Override
    public String toString() {
        return units + " = " + worth + " (" + date + ")";
    }
}
