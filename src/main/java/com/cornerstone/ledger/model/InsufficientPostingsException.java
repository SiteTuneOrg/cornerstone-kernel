package com.cornerstone.ledger.model;

/**
 * A {@link JournalEntry.Builder#build()} was attempted with fewer postings
 * than a double-entry transaction requires: at least 2, or exactly 1 when
 * it's a zero-money quantity-only adjustment (see {@code build()}'s javadoc).
 */
public final class InsufficientPostingsException extends LedgerException {
    public InsufficientPostingsException(String entryId, int postingCount) {
        super("entry " + entryId + " requires at least 2 postings (or 1 zero-value quantity adjustment), got "
                + postingCount);
    }
}
