package com.cornerstone.ledger.model;

/**
 * Root of the library's exception hierarchy. Unchecked on purpose: a caller
 * that violates a ledger invariant (unbalanced entry, wrong currency, a
 * posting to a placeholder account) has a bug, not a recoverable condition
 * to catch-and-continue from. Sealed, with one subclass per actual failure
 * mode ({@link UnbalancedEntryException}, {@link AccountNotPostableException},
 * {@link CurrencyMismatchException}, {@link NatureMismatchException},
 * {@link DuplicateAccountException}, {@link DuplicateAccountCodeException},
 * {@link CommodityConflictException}, {@link AccountIdConflictException},
 * {@link ForeignAccountException}, {@link ForeignPostingException},
 * {@link DuplicateJournalEntryException}, {@link InsufficientPostingsException},
 * {@link CyclicAccountException}), so callers distinguish failures by
 * {@code catch} clause and a {@code switch} over them is exhaustive.
 */
public sealed class LedgerException extends RuntimeException
        permits UnbalancedEntryException, AccountNotPostableException,
                CurrencyMismatchException, NatureMismatchException, DuplicateAccountException,
                DuplicateAccountCodeException, CommodityConflictException, AccountIdConflictException,
                ForeignAccountException, ForeignPostingException, DuplicateJournalEntryException,
                InsufficientPostingsException, CyclicAccountException {

    public LedgerException(String message) {
        super(message);
    }
}
