package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Ledger#currency}/{@link Ledger#asset} and
 * {@link Ledger#rootAccountOrCreate}/{@link Ledger#accountOrCreate} exist so
 * a caller building a chart of accounts incrementally (a CSV importer,
 * typically) never needs its own {@code Map<String, X>} cache in front of
 * what the ledger already tracks internally.
 */
class LedgerChartBuildingTest {

    @Test
    void currencyIsRegisteredOnceAndReturnedOnRepeatCalls() {
        Ledger ledger = new Ledger();
        Commodity first = ledger.currency("BRL", 100);
        Commodity second = ledger.currency("BRL", 100);
        assertSame(first, second, "same call, same shape — must return the SAME registered object");
        assertEquals(1, ledger.commodities().size());
    }

    @Test
    void conflictingRedefinitionOfTheSameCommodityCodeIsRefused() {
        Ledger ledger = new Ledger();
        ledger.currency("BRL", 100);
        assertThrows(CommodityConflictException.class, () -> ledger.currency("BRL", 1));
    }

    @Test
    void rootAccountOrCreateIsIdempotentByIdOnRepeatCalls() {
        Ledger ledger = new Ledger();
        Commodity brl = ledger.currency("BRL", 100);
        Account first = ledger.rootAccountOrCreate("assets", "Assets", AccountNature.ASSET, brl);
        Account second = ledger.rootAccountOrCreate("assets", "Assets", AccountNature.ASSET, brl);
        assertSame(first, second);
        assertEquals(1, ledger.roots().size());
    }

    @Test
    void accountOrCreateIsIdempotentByIdOnRepeatCalls() {
        Ledger ledger = new Ledger();
        Commodity brl = ledger.currency("BRL", 100);
        Account assets = ledger.rootAccountOrCreate("assets", "Assets", AccountNature.ASSET, brl);
        Account first = ledger.accountOrCreate("bank", "Bank", brl, assets);
        Account second = ledger.accountOrCreate("bank", "Bank", brl, assets);
        assertSame(first, second);
        assertEquals(1, assets.children().size());
    }

    @Test
    void reusingAnIdForADifferentlyShapedAccountIsRefused() {
        Ledger ledger = new Ledger();
        Commodity brl = ledger.currency("BRL", 100);
        ledger.rootAccountOrCreate("root", "Assets", AccountNature.ASSET, brl);
        assertThrows(AccountIdConflictException.class,
                () -> ledger.rootAccountOrCreate("root", "Expenses", AccountNature.EXPENSE, brl));
    }

    @Test
    void strictCreationRefusesAnIdAlreadyInUseEvenWithTheSameShape() {
        Ledger ledger = new Ledger();
        Commodity brl = ledger.currency("BRL", 100);
        ledger.openRootAccount("root", "Assets", AccountNature.ASSET, brl);
        assertThrows(AccountIdConflictException.class,
                () -> ledger.openRootAccount("root", "Assets", AccountNature.ASSET, brl));
    }

    @Test
    void rootAccountOrCreateRefusesAConflictingCommodityOnItsReuseBranch() {
        Ledger ledger = new Ledger();
        ledger.openRootAccount("cash", "Cash", AccountNature.ASSET, Commodity.currency("USD", 100));

        // same code, different scale — a rogue commodity that never went
        // through ledger.currency(...). Commodity.equals() is code-only, so
        // this must be refused by the registry check, not silently waved
        // through as "same shape" because the account already exists.
        Commodity rogueUsd = Commodity.currency("USD", 1);
        assertThrows(CommodityConflictException.class,
                () -> ledger.rootAccountOrCreate("cash", "Cash", AccountNature.ASSET, rogueUsd));
    }

    @Test
    void accountOrCreateRefusesAConflictingCommodityOnItsReuseBranch() {
        Ledger ledger = new Ledger();
        Commodity brl = ledger.currency("BRL", 100);
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, brl);
        ledger.openAccount("cash", "Cash", Commodity.currency("USD", 100), assets);

        Commodity rogueUsd = Commodity.currency("USD", 1);
        assertThrows(CommodityConflictException.class,
                () -> ledger.accountOrCreate("cash", "Cash", rogueUsd, assets));
    }

    @Test
    void postingAnEntryBuiltWithAnAccountFromAnotherLedgerIsRefused() {
        Ledger ledgerA = new Ledger();
        Commodity brlA = ledgerA.currency("BRL", 100);
        Account bankA = ledgerA.openRootAccount("bank", "Bank", AccountNature.ASSET, brlA);

        Ledger ledgerB = new Ledger();
        Commodity brlB = ledgerB.currency("BRL", 100);
        Account revenueB = ledgerB.openRootAccount("revenue", "Revenue", AccountNature.REVENUE, brlB);

        // entry balances fine on its own — the violation is that revenueB
        // belongs to ledgerB, not ledgerA, and only surfaces at post() time.
        JournalEntry entry = JournalEntry.builder("e1", LocalDate.of(2026, 1, 1), "Crossed", brlA)
                .debit(bankA, Money.of(1_000, brlA))
                .credit(revenueB, Money.of(1_000, brlA))
                .build();

        assertThrows(ForeignAccountException.class, () -> ledgerA.post(entry));
    }

    @Test
    void openAccountRefusesAParentFromAnotherLedger() {
        Ledger ledgerA = new Ledger();
        Commodity brlA = ledgerA.currency("BRL", 100);
        Account rootA = ledgerA.openRootAccount("root", "Root", AccountNature.ASSET, brlA);

        Ledger ledgerB = new Ledger();
        Commodity brlB = ledgerB.currency("BRL", 100);

        assertThrows(ForeignAccountException.class,
                () -> ledgerB.openAccount("child", "Child", brlB, rootA));
        assertThrows(ForeignAccountException.class,
                () -> ledgerB.accountOrCreate("child", "Child", brlB, rootA));
        // rootA must stay exactly as it was — no child attached by the
        // refused ledgerB calls above.
        assertEquals(0, rootA.children().size());
    }
}
