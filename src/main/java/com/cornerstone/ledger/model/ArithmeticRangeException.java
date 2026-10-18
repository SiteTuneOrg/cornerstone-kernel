package com.cornerstone.ledger.model;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * An arithmetic operation of this kernel was refused: a {@link LedgerArithmetic}
 * call, or a sum ({@link Money#plus}, {@link Ledger#balanceOf}...) whose exact
 * result does not fit a {@code long}. Carries the failure as
 * data ({@link #reason}, {@link #context}, {@link #value}, {@link #index})
 * rather than only as prose, so a layer on top of this kernel can phrase the
 * error in its own contract's words without parsing an English message.
 */
public final class ArithmeticRangeException extends LedgerException {

    public enum Reason {
        /** The exact result does not fit a {@code long}; {@link #value()} holds it. */
        LONG_OVERFLOW,
        DIVISION_BY_ZERO,
        EMPTY_WEIGHTS,
        /** {@link #index()} names the first negative weight. */
        NEGATIVE_WEIGHT,
        ZERO_WEIGHT_SUM
    }

    private final Reason reason;
    private final String context;
    private final BigInteger value;
    private final Integer index;

    private ArithmeticRangeException(Reason reason, String context, BigInteger value, Integer index, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.context = Objects.requireNonNull(context, "context");
        this.value = value;
        this.index = index;
    }

    static ArithmeticRangeException longOverflow(String context, BigInteger value) {
        return new ArithmeticRangeException(Reason.LONG_OVERFLOW, context, value, null,
                context + " overflows a long: " + value);
    }

    static ArithmeticRangeException divisionByZero(String context) {
        return new ArithmeticRangeException(Reason.DIVISION_BY_ZERO, context, null, null,
                context + ": division by zero");
    }

    static ArithmeticRangeException emptyWeights(String context) {
        return new ArithmeticRangeException(Reason.EMPTY_WEIGHTS, context, null, null,
                context + ": no weights");
    }

    static ArithmeticRangeException negativeWeight(String context, int index, long weight) {
        return new ArithmeticRangeException(Reason.NEGATIVE_WEIGHT, context, BigInteger.valueOf(weight), index,
                context + ": negative weight at [" + index + "]: " + weight);
    }

    static ArithmeticRangeException zeroWeightSum(String context) {
        return new ArithmeticRangeException(Reason.ZERO_WEIGHT_SUM, context, null, null,
                context + ": the weights must sum to a positive number");
    }

    public Reason reason() {
        return reason;
    }

    /** The operation that failed, e.g. {@code "mulDiv"}, {@code "apportion"}, {@code "Money.plus"} or {@code "Ledger.balanceOf"}. */
    public String context() {
        return context;
    }

    /** The offending number, for {@link Reason#LONG_OVERFLOW} and {@link Reason#NEGATIVE_WEIGHT}. */
    public Optional<BigInteger> value() {
        return Optional.ofNullable(value);
    }

    /** The offending weight's position, for {@link Reason#NEGATIVE_WEIGHT}. */
    public OptionalInt index() {
        return index == null ? OptionalInt.empty() : OptionalInt.of(index);
    }
}
