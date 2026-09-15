package com.cornerstone.ledger.model;

/**
 * An {@link Account} was used with a {@link Ledger} that didn't create it —
 * e.g. a {@link JournalEntry} posted against an account belonging to a
 * different {@code Ledger} instance, or {@link Ledger#setAccountCode}
 * called on one. Identity is checked ({@code account.id()} resolving to a
 * DIFFERENT object in this ledger's registry, or to nothing at all), not
 * just a matching id string.
 */
public final class ForeignAccountException extends LedgerException {
    public ForeignAccountException(Account account) {
        super("account \"" + account.path() + "\" does not belong to this Ledger");
    }
}
