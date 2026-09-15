package com.cornerstone.ledger.model;

/**
 * A {@link Commodity} code was registered on a {@link Ledger} with a scale
 * or kind that disagrees with an earlier registration of the same code.
 */
public final class CommodityConflictException extends LedgerException {
    public CommodityConflictException(Commodity existing, Commodity requested) {
        super("commodity \"" + requested.code() + "\" already registered as "
                + existing.kind() + "/scale " + existing.scale()
                + " — conflicting request: " + requested.kind() + "/scale " + requested.scale());
    }
}
