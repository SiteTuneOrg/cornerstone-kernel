package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * An exchange between two currencies, both ways the kernel records one (see
 * README): one entry with a leg in the other currency, with
 * optional {@code TRADING} legs; or a pair of single-currency entries, where
 * {@link ExchangeRate} closes the loop, because comparing what the two sides
 * are worth in a common currency needs an explicit rate.
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

    /**
     * With trading accounts on: ONE entry in BRL. The USD account's leg carries the BRL
     * value and the USD quantity; the trading legs make each currency net to zero inside the entry.
     */
    @Test
    void anExchangeIsOneEntryWithAForeignLegAndTradingLegs() {
        Ledger ledger = new Ledger();
        Account bank = ledger.openRootAccount("bank", "Bank", AccountNature.ASSET, BRL);
        Account broker = ledger.openRootAccount("broker-usd", "Broker USD", AccountNature.ASSET, USD);
        Account trading = ledger.openRootAccount("trading", "Trading", AccountNature.ASSET, BRL);
        Account tradingBrl = ledger.openAccount("trading-brl", "BRL", BRL, trading);
        Account tradingUsd = ledger.openAccount("trading-usd", "USD", USD, trading);
        tradingBrl.setType(AccountType.TRADING);
        tradingUsd.setType(AccountType.TRADING);

        // US$ 100.00 bought at R$ 5.25.
        ledger.post(JournalEntry.builder("exchange", LocalDate.of(2026, 1, 5), "BRL->USD exchange", BRL)
                .debit(broker, Money.of(52_500, BRL), Quantity.of(10_000, USD))
                .credit(tradingUsd, Money.of(52_500, BRL), Quantity.of(10_000, USD))
                .debit(tradingBrl, Money.of(52_500, BRL))
                .credit(bank, Money.of(52_500, BRL))
                .build());

        assertEquals(10_000L, ledger.unitsOf(broker), "US$ 100.00 held, in the account's own currency");
        assertEquals(52_500L, ledger.balanceOf(broker), "what it cost, in the entry's currency");
        assertEquals(-52_500L, ledger.unitsOf(bank));
        assertEquals(-10_000L, ledger.unitsOf(tradingUsd), "the USD that left the trading account...");
        assertEquals(52_500L, ledger.unitsOf(tradingBrl), "...against the BRL that entered it");
    }

    @Test
    void aLegInAnotherCurrencyRequiresTheAmountInTheAccountsOwnCurrency() {
        Ledger ledger = new Ledger();
        Account broker = ledger.openRootAccount("broker-usd", "Broker USD", AccountNature.ASSET, USD);

        assertThrows(PostingShapeException.class, () -> Posting.debit(broker, Money.of(52_500, BRL)));
        assertThrows(CurrencyMismatchException.class,
                () -> Posting.debit(broker, Money.of(52_500, BRL), Quantity.of(10_000, BRL)));
        assertThrows(PostingShapeException.class,
                () -> Posting.debit(broker, Money.of(10_000, USD), Quantity.of(10_000, USD)),
                "posted in its own currency, money IS the amount: no Quantity");
        assertThrows(CurrencyMismatchException.class,
                () -> Posting.debit(broker, Money.of(1, Commodity.asset("PETR4", 1)), Quantity.of(10_000, USD)),
                "a currency account's value is never in an asset");
    }

    @Test
    void aZeroMoneyExchangeLegIsRefusedSoItCannotMintCurrency() {
        Ledger ledger = new Ledger();
        Account broker = ledger.openRootAccount("broker-usd", "Broker USD", AccountNature.ASSET, USD);
        Account savings = ledger.openRootAccount("savings-usd", "Savings USD", AccountNature.ASSET, USD);

        assertThrows(PostingShapeException.class,
                () -> JournalEntry.builder("mint", LocalDate.of(2026, 1, 5), "free dollars", BRL)
                        .debit(broker, Money.zero(BRL), Quantity.of(1_000_000, USD)),
                "a lone zero-money leg");
        JournalEntry.Builder twoLegs = JournalEntry.builder("mint2", LocalDate.of(2026, 1, 5), "free dollars", BRL)
                .debit(broker, Money.of(1, BRL), Quantity.of(1, USD));
        assertThrows(PostingShapeException.class,
                () -> twoLegs.debit(savings, Money.zero(BRL), Quantity.of(500, USD)),
                "a second zero-money leg would balance the first at 0 == 0");
    }

    @Test
    void aZeroQuantityExchangeLegIsRefusedBecauseTheRateWouldBeUndefined() {
        Ledger ledger = new Ledger();
        Account broker = ledger.openRootAccount("broker-usd", "Broker USD", AccountNature.ASSET, USD);

        assertThrows(PostingShapeException.class,
                () -> Posting.debit(broker, Money.of(50_000, BRL), Quantity.zero(USD)));
    }

    @Test
    void convertDoesNotOverflowOnTheIntermediateProduct() {
        ExchangeRate rate = ExchangeRate.of(Money.of(100_000_000, USD), Money.of(525_000_000, BRL), LocalDate.now());
        assertEquals(Money.of(105_000_000_000L, BRL), rate.convert(Money.of(20_000_000_000L, USD)));
    }
}
