package com.cornerstone.ledger.model;

/** A child account's nature disagrees with its parent's. */
public final class NatureMismatchException extends LedgerException {
    public NatureMismatchException(Account child, Account parent) {
        super("account \"" + child.name() + "\" (" + child.nature() + ") disagrees with parent account \""
                + parent.name() + "\" (" + parent.nature() + ")");
    }
}
