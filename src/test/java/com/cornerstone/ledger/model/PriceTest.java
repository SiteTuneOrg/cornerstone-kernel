package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Price} is a valuation quote (units were worth this much, on this
 * date) — kept as an unreduced ratio of an already-typed {@link Quantity}
 * and {@link Money} rather than a decimal rate, so no precision is lost
 * storing it; {@link Price#valueOf} still divides once, when actually
 * applying the quote to a different quantity.
 */
class PriceTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);
    private static final Commodity PETR4 = Commodity.asset("PETR4", 1);

    @Test
    void valueOfTheSameQuantityQuotedReturnsExactlyWhatItWasWorth() {
        Price quote = Price.of(Quantity.of(3, PETR4), Money.of(1000, BRL), LocalDate.of(2026, 1, 5));
        assertEquals(Money.of(1000, BRL), quote.valueOf(Quantity.of(3, PETR4)));
    }

    @Test
    void valueOfADifferentQuantityTruncatesTowardZeroLikeEverywhereElseInThisKernel() {
        // R$10,00 for 3 shares = R$3,333... per share; 1 share truncates to R$3,33.
        Price quote = Price.of(Quantity.of(3, PETR4), Money.of(1000, BRL), LocalDate.of(2026, 1, 5));
        assertEquals(Money.of(333, BRL), quote.valueOf(Quantity.of(1, PETR4)));
    }

    @Test
    void valueOfAFractionalQuotaPositionAtAPensionFundStyleQuote() {
        // a fund quota with 5 decimal places, like a real pension plan.
        Commodity quota = Commodity.asset("BRASILPREV-RT-FIX-100", 100_000);
        // 1,000.00000 quotas were worth R$12,345.67 that day.
        Price quote = Price.of(
                Quantity.of(1_000_00000L, quota), Money.of(1_234_567, BRL), LocalDate.of(2026, 3, 1));

        // a position of 250.00000 quotas.
        Money value = quote.valueOf(Quantity.of(250_00000L, quota));

        assertEquals(Money.of(308_641, BRL), value); // R$3,086.41 (truncated from R$3,086.4175)
    }

    @Test
    void refusesAQuoteWhoseWorthIsNotACurrency() {
        Commodity hgtx3 = Commodity.asset("HGTX3", 1);
        assertThrows(IllegalArgumentException.class,
                () -> Price.of(Quantity.of(1, PETR4), Money.of(1, hgtx3), LocalDate.now()));
    }

    @Test
    void refusesToValueAQuantityOfADifferentCommodity() {
        Price quote = Price.of(Quantity.of(1, PETR4), Money.of(3550, BRL), LocalDate.now());
        Commodity vale3 = Commodity.asset("VALE3", 1);
        assertThrows(CurrencyMismatchException.class, () -> quote.valueOf(Quantity.of(1, vale3)));
    }
}
