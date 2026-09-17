package com.cornerstone.ledger.model;

import java.util.Objects;

/**
 * A currency ("BRL", "USD") or a tradable asset ("PETR4", "BTC") that money
 * can be denominated in. Identity is {@link #code()} — two {@code Commodity}
 * instances with the same code are the same commodity, so this is a value
 * object, not an entity: {@link #equals(Object)} is by code alone.
 *
 * @param code   e.g. "BRL", "PETR4"
 * @param scale  smallest units per whole unit (BRL: 100 = cents; a stock
 *               position tracked to 8 decimals: 100_000_000)
 * @param kind   currency vs. asset
 */
public record Commodity(String code, long scale, CommodityKind kind) {

    public Commodity {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(kind, "kind");
        if (code.isBlank()) {
            throw new IllegalArgumentException("blank commodity code");
        }
        if (scale <= 0) {
            throw new IllegalArgumentException("commodity scale must be positive: " + scale);
        }
    }

    public static Commodity currency(String code, long scale) {
        return new Commodity(code, scale, CommodityKind.CURRENCY);
    }

    public static Commodity asset(String code, long scale) {
        return new Commodity(code, scale, CommodityKind.ASSET);
    }

    /**
     * Throws {@link IllegalArgumentException} if this commodity isn't
     * {@code expected}'s kind — the same "wrong kind" check {@link Price}
     * and {@link ExchangeRate} each need on construction,
     * pulled out so the message and the check stay in one place.
     */
    void requireKind(CommodityKind expected, String context) {
        if (kind != expected) {
            throw new IllegalArgumentException(
                    context + " requires a commodity of kind " + expected + ", got " + code + " (" + kind + ")");
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Commodity other && code.equals(other.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
