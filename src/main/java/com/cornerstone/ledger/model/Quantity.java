package com.cornerstone.ledger.model;

import java.util.Objects;

/**
 * An immutable, non-negative count of units of a {@link Posting}'s account's
 * own {@link Commodity} — shares of PETR4, satoshis of Bitcoin, or, on an
 * exchange leg (a USD account posted in a BRL entry), cents of the foreign
 * currency. The account-side counterpart of {@link Money}: same
 * non-negative-by-construction shape (sign lives on {@link Posting#side()}),
 * same {@code long} smallest-unit representation, but denominated in what the
 * account holds rather than in the entry's currency.
 *
 * <p>Kept as a distinct type from {@link Money} — even though both are "a
 * non-negative long plus a commodity" — because they answer different
 * questions and must never be added to each other: {@code Money} is "how
 * much did this cost/how much cash moved", {@code Quantity} is "how many
 * units are now held". A {@link Posting} against a position account carries
 * both, independently, and only {@code Money} is required to balance across
 * a {@link JournalEntry} — a security position, unlike cash, moves on ONE
 * side of a trade only (the counterparty's leg is cash, not units of the
 * same security).
 */
public final class Quantity implements Comparable<Quantity> {

    private final long units;
    private final Commodity commodity;

    private Quantity(long units, Commodity commodity) {
        if (units < 0) {
            throw new IllegalArgumentException("Quantity does not allow a negative value: " + units);
        }
        Objects.requireNonNull(commodity, "commodity");
        this.units = units;
        this.commodity = commodity;
    }

    public static Quantity of(long units, Commodity commodity) {
        return new Quantity(units, commodity);
    }

    public static Quantity zero(Commodity commodity) {
        return new Quantity(0, commodity);
    }

    public long units() {
        return units;
    }

    public Commodity commodity() {
        return commodity;
    }

    public boolean isZero() {
        return units == 0;
    }

    public Quantity plus(Quantity other) {
        requireSameCommodity(other);
        return new Quantity(Math.addExact(units, other.units), commodity);
    }

    public Quantity minus(Quantity other) {
        requireSameCommodity(other);
        long result = Math.subtractExact(units, other.units);
        if (result < 0) {
            throw new IllegalArgumentException("negative result: " + this + " - " + other);
        }
        return new Quantity(result, commodity);
    }

    private void requireSameCommodity(Quantity other) {
        if (!commodity.equals(other.commodity)) {
            throw new CurrencyMismatchException(commodity, other.commodity);
        }
    }

    @Override
    public int compareTo(Quantity other) {
        requireSameCommodity(other);
        return Long.compare(units, other.units);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Quantity other && units == other.units && commodity.equals(other.commodity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(units, commodity);
    }

    @Override
    public String toString() {
        return units + " " + commodity.code();
    }
}
