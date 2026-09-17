package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Money}: unsigned smallest-unit amounts; sign lives on the posting side, not here. */
class MoneyTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);
    private static final Commodity USD = Commodity.currency("USD", 100);

    @Test
    void refusesANegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(-1, BRL));
    }

    @Test
    void addsAndSubtractsWithinTheSameCommodity() {
        Money tenReais = Money.of(1_000, BRL);
        Money threeReais = Money.of(300, BRL);

        assertEquals(Money.of(1_300, BRL), tenReais.plus(threeReais));
        assertEquals(Money.of(700, BRL), tenReais.minus(threeReais));
        assertTrue(Money.zero(BRL).isZero());
        assertFalse(tenReais.isZero());
    }

    @Test
    void refusesANegativeResultAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(1, BRL).minus(Money.of(2, BRL)));
        assertThrows(ArithmeticException.class, () -> Money.of(Long.MAX_VALUE, BRL).plus(Money.of(1, BRL)));
    }

    @Test
    void refusesToCombineDifferentCurrencies() {
        Money reais = Money.of(1, BRL);
        Money dollars = Money.of(1, USD);
        assertThrows(CurrencyMismatchException.class, () -> reais.plus(dollars));
        assertThrows(CurrencyMismatchException.class, () -> reais.minus(dollars));
        assertThrows(CurrencyMismatchException.class, () -> reais.compareTo(dollars));
    }

    @Test
    void ordersEqualsAndPrints() {
        assertTrue(Money.of(1, BRL).compareTo(Money.of(2, BRL)) < 0);
        assertTrue(Money.of(2, BRL).compareTo(Money.of(1, BRL)) > 0);
        assertEquals(Money.of(5, BRL).hashCode(), Money.of(5, BRL).hashCode());
        assertNotEquals(Money.of(5, BRL), Money.of(5, USD));
        assertEquals("150 BRL", Money.of(150, BRL).toString());
    }
}
