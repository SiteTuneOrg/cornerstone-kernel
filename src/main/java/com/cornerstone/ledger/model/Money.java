package com.cornerstone.ledger.model;

import java.util.Objects;

/**
 * An immutable, non-negative amount of a specific {@link Commodity}, in its
 * smallest unit (cents for BRL, satoshi-like units for an asset).
 *
 * <p>Deliberately unsigned: sign belongs to a {@link Posting}'s
 * {@link NormalSide}, not to the amount. A negative amount cannot be
 * constructed at all, so "which posting is the debit" is answered by the
 * type ({@code Posting.side()}), never by squinting at a sign.
 *
 * <p>Arithmetic overflow is checked ({@link Math#addExact}); this class does
 * not additionally clamp to a smaller ceiling such as
 * {@code Number.MAX_SAFE_INTEGER} — a pure JVM library has no downstream
 * boundary that would justify one.
 */
public final class Money implements Comparable<Money> {

    private final long amount;
    private final Commodity commodity;

    private Money(long amount, Commodity commodity) {
        if (amount < 0) {
            throw new IllegalArgumentException("Money does not allow a negative value: " + amount);
        }
        this.amount = amount;
        this.commodity = Objects.requireNonNull(commodity, "commodity");
    }

    public static Money of(long amount, Commodity commodity) {
        return new Money(amount, commodity);
    }

    public static Money zero(Commodity commodity) {
        return new Money(0, commodity);
    }

    public long amount() {
        return amount;
    }

    public Commodity commodity() {
        return commodity;
    }

    public boolean isZero() {
        return amount == 0;
    }

    public Money plus(Money other) {
        requireSameCommodity(other);
        return new Money(Math.addExact(amount, other.amount), commodity);
    }

    /**
     * Subtracts {@code other} from this amount. Throws if the result would be
     * negative — callers that need a signed delta (a balance that can go
     * negative, e.g. a liability overpaid) use {@code long} arithmetic
     * directly via {@link #amount()}, not this method.
     */
    public Money minus(Money other) {
        requireSameCommodity(other);
        long result = Math.subtractExact(amount, other.amount);
        if (result < 0) {
            throw new IllegalArgumentException(
                    "negative result: " + this + " - " + other);
        }
        return new Money(result, commodity);
    }

    private void requireSameCommodity(Money other) {
        if (!commodity.equals(other.commodity)) {
            throw new CurrencyMismatchException(commodity, other.commodity);
        }
    }

    @Override
    public int compareTo(Money other) {
        requireSameCommodity(other);
        return Long.compare(amount, other.amount);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && amount == other.amount && commodity.equals(other.commodity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, commodity);
    }

    @Override
    public String toString() {
        return amount + " " + commodity.code();
    }
}
