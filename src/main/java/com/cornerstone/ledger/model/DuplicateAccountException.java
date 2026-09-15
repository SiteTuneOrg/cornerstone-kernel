package com.cornerstone.ledger.model;

/** Two sibling accounts (same parent) share a name. */
public final class DuplicateAccountException extends LedgerException {
    public DuplicateAccountException(String name, Account parent) {
        super("an account \"" + name + "\" already exists under \"" + parent.path() + "\"");
    }
}
