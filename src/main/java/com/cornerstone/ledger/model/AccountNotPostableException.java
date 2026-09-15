package com.cornerstone.ledger.model;

/**
 * A posting targeted an account that cannot directly receive one: a
 * placeholder (organizational only), a system account, a hidden
 * (soft-deleted) account, or an account that already has children.
 */
public final class AccountNotPostableException extends LedgerException {
    public AccountNotPostableException(Account account, String reason) {
        super("account \"" + account.path() + "\" does not accept postings: " + reason);
    }
}
