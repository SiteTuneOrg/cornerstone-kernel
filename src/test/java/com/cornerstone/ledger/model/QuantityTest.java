package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Quantity}: non-negative units of an account's own commodity, never mixed across commodities. */
class QuantityTest {

    private static final Commodity PETR4 = Commodity.asset("PETR4", 1);
    private static final Commodity VALE3 = Commodity.asset("VALE3", 1);
    private static final Commodity BRL = Commodity.currency("BRL", 100);

    @Test
    void refusesNegativeUnits() {
        assertThrows(IllegalArgumentException.class, () -> Quantity.of(-1, PETR4));
    }

    @Test
    void countsUnitsOfACurrencyForAnExchangeLeg() {
        assertEquals(10_000, Quantity.of(10_000, BRL).units());
    }

    @Test
    void addsAndSubtractsWithinTheSameCommodity() {
        Quantity ten = Quantity.of(10, PETR4);
        Quantity three = Quantity.of(3, PETR4);

        assertEquals(Quantity.of(13, PETR4), ten.plus(three));
        assertEquals(Quantity.of(7, PETR4), ten.minus(three));
        assertTrue(ten.minus(ten).isZero());
        assertTrue(Quantity.zero(PETR4).isZero());
        assertFalse(ten.isZero());
        assertEquals(10, ten.units());
        assertEquals(PETR4, ten.commodity());
    }

    @Test
    void refusesANegativeResultAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> Quantity.of(1, PETR4).minus(Quantity.of(2, PETR4)));
        assertThrows(ArithmeticException.class,
                () -> Quantity.of(Long.MAX_VALUE, PETR4).plus(Quantity.of(1, PETR4)));
    }

    @Test
    void refusesToCombineDifferentCommodities() {
        Quantity petr = Quantity.of(1, PETR4);
        Quantity vale = Quantity.of(1, VALE3);
        assertThrows(CurrencyMismatchException.class, () -> petr.plus(vale));
        assertThrows(CurrencyMismatchException.class, () -> petr.minus(vale));
        assertThrows(CurrencyMismatchException.class, () -> petr.compareTo(vale));
    }

    @Test
    void ordersEqualsAndPrints() {
        assertTrue(Quantity.of(1, PETR4).compareTo(Quantity.of(2, PETR4)) < 0);
        assertEquals(Quantity.of(5, PETR4), Quantity.of(5, PETR4));
        assertEquals(Quantity.of(5, PETR4).hashCode(), Quantity.of(5, PETR4).hashCode());
        assertNotEquals(Quantity.of(5, PETR4), Quantity.of(5, VALE3));
        assertEquals("5 PETR4", Quantity.of(5, PETR4).toString());
    }
}
