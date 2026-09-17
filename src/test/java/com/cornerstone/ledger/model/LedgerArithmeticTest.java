package com.cornerstone.ledger.model;

import com.cornerstone.ledger.model.LedgerArithmetic.Rounding;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link LedgerArithmetic}: rounding modes on both signs, the largest-remainder
 * split, and the refusals. The frozen TS parity vector is consumed by the
 * server, which adds its wire's safe-integer ceiling on top of these.
 */
class LedgerArithmeticTest {

    @Test
    void mulDivRoundsEachModeOnAPositiveResult() {
        assertEquals(3, LedgerArithmetic.mulDiv(7, 1, 2, Rounding.TRUNC));
        assertEquals(3, LedgerArithmetic.mulDiv(7, 1, 2, Rounding.FLOOR));
        assertEquals(4, LedgerArithmetic.mulDiv(7, 1, 2, Rounding.ROUND));
    }

    @Test
    void mulDivRoundsEachModeOnANegativeResult() {
        assertEquals(-3, LedgerArithmetic.mulDiv(-7, 1, 2, Rounding.TRUNC));
        assertEquals(-4, LedgerArithmetic.mulDiv(7, 1, -2, Rounding.FLOOR));
        assertEquals(-4, LedgerArithmetic.mulDiv(-7, 1, 2, Rounding.ROUND));
        assertEquals(-3, LedgerArithmetic.mulDiv(-6, 1, 2, Rounding.FLOOR));
    }

    @Test
    void mulDivNeverOverflowsInTheIntermediateProduct() {
        assertEquals(Long.MAX_VALUE, LedgerArithmetic.mulDiv(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Rounding.TRUNC));
    }

    @Test
    void mulDivRefusesAResultThatDoesNotFitALong() {
        ArithmeticRangeException e = assertThrows(ArithmeticRangeException.class,
                () -> LedgerArithmetic.mulDiv(Long.MAX_VALUE, 2, 1, Rounding.TRUNC));
        assertEquals(ArithmeticRangeException.Reason.LONG_OVERFLOW, e.reason());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), e.value().orElseThrow());
    }

    @Test
    void mulDivRefusesDivisionByZero() {
        ArithmeticRangeException e = assertThrows(ArithmeticRangeException.class,
                () -> LedgerArithmetic.mulDiv(1, 1, 0, Rounding.ROUND));
        assertEquals(ArithmeticRangeException.Reason.DIVISION_BY_ZERO, e.reason());
    }

    @Test
    void apportionSumsExactlyAndBreaksTiesByLowestIndex() {
        assertArrayEquals(new long[] {34, 33, 33}, LedgerArithmetic.apportion(100, new long[] {1, 1, 1}));
        assertArrayEquals(new long[] {34, 34, 33}, LedgerArithmetic.apportion(101, new long[] {1, 1, 1}));
        assertArrayEquals(new long[] {5, 0, 2}, LedgerArithmetic.apportion(7, new long[] {10, 0, 3}));
    }

    @Test
    void apportionSplitsANegativeTotalByMagnitude() {
        assertArrayEquals(new long[] {-34, -33, -33}, LedgerArithmetic.apportion(-100, new long[] {1, 1, 1}));
    }

    @Test
    void apportionRefusesBadWeights() {
        assertEquals(ArithmeticRangeException.Reason.EMPTY_WEIGHTS, assertThrows(ArithmeticRangeException.class,
                () -> LedgerArithmetic.apportion(1, new long[0])).reason());
        ArithmeticRangeException negative = assertThrows(ArithmeticRangeException.class,
                () -> LedgerArithmetic.apportion(1, new long[] {1, -2, -3}));
        assertEquals(ArithmeticRangeException.Reason.NEGATIVE_WEIGHT, negative.reason());
        assertEquals(1, negative.index().orElseThrow());
        assertEquals(ArithmeticRangeException.Reason.ZERO_WEIGHT_SUM, assertThrows(ArithmeticRangeException.class,
                () -> LedgerArithmetic.apportion(1, new long[] {0, 0})).reason());
    }
}
