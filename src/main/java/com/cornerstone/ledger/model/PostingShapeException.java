package com.cornerstone.ledger.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link Posting} has the wrong shape for its account: a {@link Quantity}
 * given where money already is the position, missing where the account holds
 * a position or another currency, an exchange leg with a zero side, a
 * position valued in another asset, or a posting that moves nothing at all.
 *
 * <p>This is the central invariant of positions (a position move with no
 * quantity silently corrupts the share count), not ordinary argument
 * validation, so it belongs in the sealed hierarchy: a caller that catches
 * {@link LedgerException} catches it too. A quantity in a commodity other
 * than the account's own, or a value that is not in a currency on a cash
 * account, is still a {@link CurrencyMismatchException}.
 *
 * <p>{@link #amount()} and {@link #quantity()} are transient: like the
 * accounts other exceptions here name in their message, they do not survive
 * Java serialization (after it {@code amount()} is {@code null}); the message
 * and {@link #accountId()} do.
 */
public final class PostingShapeException extends LedgerException {

    private final String accountId;
    private final transient Money amount;
    private final transient Quantity quantity;

    PostingShapeException(Account account, Money amount, Quantity quantity, String message) {
        super(message);
        this.accountId = Objects.requireNonNull(account, "account").id();
        this.amount = Objects.requireNonNull(amount, "amount");
        this.quantity = quantity;
    }

    /** Id of the account the posting targeted (the exception holds no reference to the mutable entity). */
    public String accountId() {
        return accountId;
    }

    public Money amount() {
        return amount;
    }

    public Optional<Quantity> quantity() {
        return Optional.ofNullable(quantity);
    }
}
