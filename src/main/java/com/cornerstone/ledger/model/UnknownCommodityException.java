package com.cornerstone.ledger.model;

/**
 * A {@link JournalEntry} was posted to a {@link Ledger} with a
 * {@link Commodity} code that ledger never registered. The ledger is the one
 * place a code resolves to a shape (see {@link Ledger}), so a code it does
 * not know has no scale it could be checked against.
 */
public final class UnknownCommodityException extends LedgerException {
    public UnknownCommodityException(Commodity commodity) {
        super("commodity \"" + commodity.code() + "\" is not registered in this Ledger");
    }
}
