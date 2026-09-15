package com.cornerstone.ledger.model;

/** A journal entry's debits and credits do not sum to the same amount. */
public final class UnbalancedEntryException extends LedgerException {
    public UnbalancedEntryException(Money totalDebits, Money totalCredits) {
        super("entry does not balance: debits " + totalDebits + " ≠ credits " + totalCredits);
    }
}
