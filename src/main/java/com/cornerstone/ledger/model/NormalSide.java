package com.cornerstone.ledger.model;

/**
 * The side (debit or credit) that INCREASES a given account's balance.
 *
 * <p>This is the one piece of accounting logic every double-entry system has
 * to put <em>somewhere</em>. It is modeled here as a first-class enum with
 * behavior, so {@link AccountNature} can delegate to it instead of every
 * consumer re-deriving the sign convention by hand.
 */
public enum NormalSide {
    DEBIT,
    CREDIT;

    /** The other side. */
    public NormalSide opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }

    /**
     * +1 if posting on {@code postingSide} moves an account's balance in its
     * normal (increasing) direction, -1 otherwise. An asset account (normal
     * side DEBIT) posted on the CREDIT side returns -1: the posting reduces
     * the balance.
     */
    public int signFor(NormalSide postingSide) {
        return this == postingSide ? 1 : -1;
    }
}
