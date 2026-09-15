package com.cornerstone.ledger.model;

/**
 * An {@link Account} id collided with an existing one on a {@link Ledger} —
 * either {@link Ledger#openAccount}/{@link Ledger#openRootAccount} were
 * asked to create a brand-new account under an id already in use, or
 * {@link Ledger#accountOrCreate}/{@link Ledger#rootAccountOrCreate} were
 * asked to reuse an id for an account with a different name, nature,
 * commodity or parent than the one already registered under it.
 */
public final class AccountIdConflictException extends LedgerException {
    private AccountIdConflictException(String message) {
        super(message);
    }

    /** The id is already in use — {@code openAccount}/{@code openRootAccount}'s strict-creation path. */
    public static AccountIdConflictException alreadyInUse(String id) {
        return new AccountIdConflictException("account id already in use: " + id);
    }

    /** The id is reused, but the requested shape disagrees with the account already registered under it. */
    public static AccountIdConflictException differentShape(String id) {
        return new AccountIdConflictException(
                "account id \"" + id + "\" already exists with a different shape than requested");
    }
}
