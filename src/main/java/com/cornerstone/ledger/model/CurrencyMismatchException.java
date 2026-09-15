package com.cornerstone.ledger.model;

/**
 * Two {@link Money} values, two {@link Quantity} values, or a posting and
 * its account/entry disagree on commodity — despite the name, this covers
 * both a currency mismatch (two {@code Money}) and an asset mismatch (two
 * {@code Quantity}); the name predates {@code Quantity} and is kept for
 * source stability rather than split into a near-duplicate type.
 */
public final class CurrencyMismatchException extends LedgerException {
    public CurrencyMismatchException(Commodity expected, Commodity actual) {
        super("expected commodity " + expected.code() + ", got " + actual.code());
    }
}
