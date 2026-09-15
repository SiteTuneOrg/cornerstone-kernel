package com.cornerstone.ledger.model;

/**
 * A {@link JournalEntry} was posted to a {@link Ledger} that already has an
 * entry with the same {@link JournalEntry#id()} — whether it's literally
 * the same object (a retried {@link Ledger#post} call) or a second, distinct
 * entry built with a reused id.
 */
public final class DuplicateJournalEntryException extends LedgerException {
    public DuplicateJournalEntryException(String id) {
        super("entry \"" + id + "\" was already posted to this Ledger");
    }
}
