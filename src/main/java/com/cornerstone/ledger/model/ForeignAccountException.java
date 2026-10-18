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

    private ForeignAccountException(String message) {
        super(message);
    }

    /** {@link Account#addChild} across two books, or between a book's account and a standalone one. */
    static ForeignAccountException crossBook(Account parent, Account child) {
        return new ForeignAccountException("cannot attach \"" + child.path() + "\" under \"" + parent.path()
                + "\": they belong to different books (" + bookOf(parent) + " vs " + bookOf(child) + ")");
    }

    private static String bookOf(Account account) {
        return account.belongsToALedger() ? "a Ledger" : "no Ledger";
    }
}
