package com.cornerstone.ledger.model;

/**
 * {@link Account#addChild} was asked to attach an account under itself or
 * under one of its own descendants — including the degenerate case of an
 * account being attached under itself directly. Refused at the point of
 * attachment so a cycle can never actually form (a cyclic parent chain would
 * make {@link Account#path()}/{@link Account#isDescendantOf} loop forever).
 */
public final class CyclicAccountException extends LedgerException {
    public CyclicAccountException(Account child, Account newParent) {
        super("account \"" + child.path() + "\" cannot be attached under \"" + newParent.path()
                + "\" — would create a cycle in the hierarchy");
    }
}
