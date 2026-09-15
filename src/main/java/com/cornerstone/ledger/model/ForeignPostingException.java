package com.cornerstone.ledger.model;

/**
 * A {@link Posting} was used with a {@link Ledger} that didn't post it —
 * e.g. {@link Ledger#setReconciliationStatus} called with a posting that
 * isn't part of any {@link JournalEntry} this ledger has posted.
 */
public final class ForeignPostingException extends LedgerException {
    public ForeignPostingException(Posting posting) {
        super("posting \"" + posting + "\" does not belong to this Ledger");
    }
}
