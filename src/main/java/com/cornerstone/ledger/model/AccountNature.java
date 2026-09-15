package com.cornerstone.ledger.model;

/**
 * Which side of the balance sheet (or income statement) an account belongs
 * to, and what that implies about debits and credits. The enum owns the one
 * rule that actually follows from being a given nature: which side is
 * "normal" for it.
 */
public enum AccountNature {
    ASSET(NormalSide.DEBIT),
    LIABILITY(NormalSide.CREDIT),
    EQUITY(NormalSide.CREDIT),
    REVENUE(NormalSide.CREDIT),
    EXPENSE(NormalSide.DEBIT);

    private final NormalSide normalSide;

    AccountNature(NormalSide normalSide) {
        this.normalSide = normalSide;
    }

    public NormalSide normalSide() {
        return normalSide;
    }

    /**
     * Net effect (+1 or -1) that posting {@code amount} on {@code side} has
     * on an account of this nature. Delegates to {@link NormalSide}, which is
     * where the actual accounting rule lives — this method exists so a caller
     * holding an {@code AccountNature} never needs to also know about
     * {@code NormalSide} as a separate concept.
     */
    public int signFor(NormalSide side) {
        return normalSide.signFor(side);
    }
}
