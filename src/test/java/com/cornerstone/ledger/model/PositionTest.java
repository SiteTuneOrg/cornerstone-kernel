package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A position account's {@link Account#commodity()} is the security itself
 * (a {@link CommodityKind#ASSET}), not a currency — {@link Money} on its
 * postings is the cost/proceeds in whatever currency the trade happened in,
 * and {@link Quantity} is the actual unit change. See the class doc on
 * {@link Posting}.
 */
class PositionTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);
    private static final Commodity PETR4 = Commodity.asset("PETR4", 1);

    @Test
    void buyingAStockTracksCostAndQuantitySeparately() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Banco", BRL, assets);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);

        ledger.post(JournalEntry.builder("e1", LocalDate.of(2026, 1, 5), "Compra PETR4", BRL)
                .debit(stock, Money.of(900_000, BRL), Quantity.of(100, PETR4))
                .credit(bank, Money.of(900_000, BRL))
                .build());

        assertEquals(900_000L, ledger.balanceOf(stock));
        assertEquals(100L, ledger.quantityOf(stock).getAsLong());
        assertTrue(ledger.quantityOf(bank).isEmpty(), "a currency account is not a position account");
    }

    @Test
    void zeroValueBonusShareIsAStandaloneQuantityAdjustment() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);

        // A single leg, no counterpart: no money moved, only the share
        // count — there's no second leg to balance against because there's
        // no monetary fact to record on the other side.
        ledger.post(JournalEntry.builder("e2", LocalDate.of(2026, 2, 1), "Bonus shares", BRL)
                .debit(stock, Money.zero(BRL), Quantity.of(5, PETR4))
                .build());

        assertEquals(5L, ledger.quantityOf(stock).getAsLong());
        assertEquals(0L, ledger.balanceOf(stock));
    }

    @Test
    void currencyAccountRefusesQuantity() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Banco", BRL, assets);

        assertThrows(IllegalArgumentException.class,
                () -> Posting.debit(bank, Money.of(100, BRL), Quantity.of(1, PETR4)));
    }

    @Test
    void positionAccountRequiresQuantity() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);

        assertThrows(IllegalArgumentException.class, () -> Posting.debit(stock, Money.of(100, BRL)));
    }

    @Test
    void aCompletelyInertPostingIsRefused() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);

        assertThrows(IllegalArgumentException.class,
                () -> Posting.debit(stock, Money.zero(BRL), Quantity.zero(PETR4)));
    }

    @Test
    void aPositionsValueMustBeInACurrencyNeverInAnotherAsset() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);
        Commodity hgtx3 = Commodity.asset("HGTX3", 1);

        assertThrows(IllegalArgumentException.class,
                () -> Posting.debit(stock, Money.of(10, hgtx3), Quantity.of(1, PETR4)));
    }
}
