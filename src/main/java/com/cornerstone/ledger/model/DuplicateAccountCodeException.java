package com.cornerstone.ledger.model;

/**
 * Two accounts share a {@link Account#code()} — either two siblings
 * ({@link Account#addChild}/{@link Account#setCode}) or, when the accounts
 * belong to a {@link Ledger}, any two accounts anywhere in its chart
 * ({@link Ledger#setAccountCode}).
 */
public final class DuplicateAccountCodeException extends LedgerException {
    public DuplicateAccountCodeException(String code, Account owner) {
        super("code \"" + code + "\" already used by account \"" + owner.path() + "\"");
    }
}
