package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ExchangeRate} closes the loop the {@code TRADING} account pattern
 * (see README) leaves open: the two single-currency entries balance
 * structurally on their own, but comparing what they're worth in a common
 * currency needs an explicit rate.
 */
class ExchangeRateTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);
    private static final Commodity USD = Commodity.currency("USD", 100);

    @Test
    void convertsAtTheExactRateItWasQuotedAt() {
        ExchangeRate rate = ExchangeRate.of(Money.of(100_000, USD), Money.of(500_000, BRL), LocalDate.of(2026, 1, 5));
        assertEquals(Money.of(500_000, BRL), rate.convert(Money.of(100_000, USD)));
    }

    @Test
    void tradingAccountsNetToZeroWhenConvertedAtTheSameRateTheExchangeUsed() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account nubankCC = ledger.openAccount("nubank-cc", "Nubank Checking", BRL, assets);
        Account nomadInvest = ledger.openAccount("nomad-invest", "Nomad Brokerage", USD, assets);
        Account trading = ledger.openAccount("trading", "Trading", BRL, assets);
        Account tradingBrl = ledger.openAccount("trading-brl", "Trading BRL", BRL, trading);
        Account tradingUsd = ledger.openAccount("trading-usd", "Trading USD", USD, trading);
        tradingBrl.setType(AccountType.TRADING);
        tradingUsd.setType(AccountType.TRADING);

        LocalDate date = LocalDate.of(2026, 1, 5);
        ExchangeRate rate = ExchangeRate.of(Money.of(100_000, USD), Money.of(500_000, BRL), date);

        ledger.post(JournalEntry.builder("exchange-1", date, "BRL->USD exchange (outflow)", BRL)
                .credit(nubankCC, Money.of(500_000, BRL))
                .debit(tradingBrl, Money.of(500_000, BRL))
                .build());

        ledger.post(JournalEntry.builder("exchange-2", date, "BRL->USD exchange (inflow)", USD)
                .credit(tradingUsd, Money.of(100_000, USD))
                .debit(nomadInvest, Money.of(100_000, USD))
                .build());

        long tradingBrlBalance = ledger.balanceOf(tradingBrl);
        long tradingUsdBalance = ledger.balanceOf(tradingUsd);
        assertEquals(500_000L, tradingBrlBalance);
        assertEquals(-100_000L, tradingUsdBalance);

        Money tradingUsdInBrl = rate.convert(Money.of(Math.abs(tradingUsdBalance), USD));
        assertEquals(tradingBrlBalance, tradingUsdInBrl.amount(),
                "the two trading legs net to zero when converted at the same rate the exchange used");
    }

    @Test
    void refusesTwoLegsInTheSameCurrency() {
        assertThrows(IllegalArgumentException.class,
                () -> ExchangeRate.of(Money.of(100, BRL), Money.of(500, BRL), LocalDate.now()));
    }

    @Test
    void refusesToConvertAnAmountInTheWrongCurrency() {
        ExchangeRate rate = ExchangeRate.of(Money.of(100_000, USD), Money.of(500_000, BRL), LocalDate.now());
        assertThrows(CurrencyMismatchException.class, () -> rate.convert(Money.of(100, BRL)));
    }
}
