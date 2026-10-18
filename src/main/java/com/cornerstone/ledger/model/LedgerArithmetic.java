package com.cornerstone.ledger.model;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Integer arithmetic on amounts in their smallest unit: scaling by a ratio
 * ({@link #mulDiv}) and splitting a total by weights ({@link #apportion}).
 *
 * <p>Intermediates are {@link BigInteger}, so {@code quantity × price} never
 * overflows before the division brings it back down; only a final result that
 * does not fit a {@code long} is refused ({@link ArithmeticRangeException}).
 * Like {@link Money}, this class does not clamp to a smaller ceiling such as
 * {@code Number.MAX_SAFE_INTEGER}: that boundary belongs to whichever wire a
 * caller serves, not to the kernel.
 */
public final class LedgerArithmetic {

    /** How {@link #mulDiv} rounds a result that is not exact. */
    public enum Rounding {
        /** Toward zero. */
        TRUNC,
        /** Toward negative infinity. */
        FLOOR,
        /** To the nearest integer, halves away from zero (not banker's rounding). */
        ROUND
    }

    private LedgerArithmetic() {
    }

    /** {@code a × b ÷ c}, rounded by {@code rounding}. */
    public static long mulDiv(long a, long b, long c, Rounding rounding) {
        if (c == 0) {
            throw ArithmeticRangeException.divisionByZero("mulDiv");
        }
        BigInteger product = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
        BigInteger divisor = BigInteger.valueOf(c);
        // Work on magnitudes and reapply the sign, so every mode is defined on non-negative numbers.
        boolean negative = product.signum() != 0 && product.signum() != divisor.signum();
        BigInteger pa = product.abs();
        BigInteger qa = divisor.abs();
        BigInteger magnitude = switch (rounding) {
            case TRUNC -> pa.divide(qa);
            case FLOOR -> {
                BigInteger[] quotientAndRemainder = pa.divideAndRemainder(qa);
                // Floor equals trunc for a positive result; a negative inexact one moves away from zero.
                yield negative && quotientAndRemainder[1].signum() != 0
                        ? quotientAndRemainder[0].add(BigInteger.ONE)
                        : quotientAndRemainder[0];
            }
            case ROUND -> pa.add(qa.shiftRight(1)).divide(qa);
        };
        return toLong(negative ? magnitude.negate() : magnitude, "mulDiv");
    }

    /**
     * Splits {@code total} across {@code weights} by the largest-remainder
     * method: the parts sum to {@code total} exactly. Remainder ties go to the
     * lowest index, so apportioning the same total twice yields the same parts.
     * A negative {@code total} is split by magnitude and every part negated.
     */
    public static long[] apportion(long total, long[] weights) {
        if (weights.length == 0) {
            throw ArithmeticRangeException.emptyWeights("apportion");
        }
        BigInteger weightSum = BigInteger.ZERO;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] < 0) {
                throw ArithmeticRangeException.negativeWeight("apportion", i, weights[i]);
            }
            weightSum = weightSum.add(BigInteger.valueOf(weights[i]));
        }
        if (weightSum.signum() == 0) {
            throw ArithmeticRangeException.zeroWeightSum("apportion");
        }

        boolean negative = total < 0;
        BigInteger magnitude = BigInteger.valueOf(total).abs();
        BigInteger[] parts = new BigInteger[weights.length];
        BigInteger[] remainders = new BigInteger[weights.length];
        BigInteger missing = magnitude;
        for (int i = 0; i < weights.length; i++) {
            BigInteger[] quotientAndRemainder = magnitude.multiply(BigInteger.valueOf(weights[i])).divideAndRemainder(weightSum);
            parts[i] = quotientAndRemainder[0];
            remainders[i] = quotientAndRemainder[1];
            missing = missing.subtract(parts[i]);
        }

        // Remainder descending, ties by lowest index; `missing` is always smaller than weights.length.
        int[] order = IntStream.range(0, weights.length).boxed()
                .sorted(Comparator.<Integer, BigInteger>comparing(i -> remainders[i]).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .mapToInt(Integer::intValue)
                .toArray();
        for (int k = 0; missing.signum() > 0; k++, missing = missing.subtract(BigInteger.ONE)) {
            parts[order[k]] = parts[order[k]].add(BigInteger.ONE);
        }

        return Arrays.stream(parts)
                .mapToLong(part -> toLong(negative ? part.negate() : part, "apportion"))
                .toArray();
    }

    private static long toLong(BigInteger value, String context) {
        if (value.bitLength() > 63) {
            throw ArithmeticRangeException.longOverflow(context, value);
        }
        return value.longValue();
    }

    /**
     * {@code a + b}, or {@link ArithmeticRangeException} with the exact sum
     * when it does not fit a {@code long} — the hierarchy's overflow, never a
     * bare {@link ArithmeticException}.
     */
    static long add(long a, long b, String context) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException overflow) {
            throw ArithmeticRangeException.longOverflow(context, BigInteger.valueOf(a).add(BigInteger.valueOf(b)));
        }
    }
}
