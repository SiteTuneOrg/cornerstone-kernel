package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);

    /** A bank (under Assets) and a salary (under Revenue) account, the recurring 2-account shape several tests below post against. */
    private record BankAndSalary(Account bank, Account salary) {
    }

    private static BankAndSalary openBankAndSalary(Ledger ledger) {
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Bank", BRL, assets);
        Account revenue = ledger.openRootAccount("revenue", "Revenue", AccountNature.REVENUE, BRL);
        Account salary = ledger.openAccount("salary", "Salary", BRL, revenue);
        return new BankAndSalary(bank, salary);
    }

    @Test
    void postsABalancedEntryAndUpdatesBalances() {
        Ledger ledger = new Ledger();
        BankAndSalary chart = openBankAndSalary(ledger);
        Account bank = chart.bank();
        Account salary = chart.salary();

        JournalEntry payday = JournalEntry.builder("e1", LocalDate.of(2026, 9, 5), "September salary", BRL)
                .debit(bank, Money.of(500_000, BRL))
                .credit(salary, Money.of(500_000, BRL))
                .build();

        ledger.post(payday);

        assertEquals(500_000L, ledger.balanceOf(bank));
        assertEquals(500_000L, ledger.balanceOf(salary));
        assertEquals(1, ledger.entries().size());
    }

    @Test
    void refusesToBuildAnUnbalancedEntry() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Bank", BRL, assets);
        Account expenses = ledger.openRootAccount("expenses", "Expenses", AccountNature.EXPENSE, BRL);
        Account groceries = ledger.openAccount("groceries", "Groceries", BRL, expenses);

        JournalEntry.Builder builder = JournalEntry.builder("e2", LocalDate.of(2026, 9, 6), "Wrong purchase", BRL)
                .debit(groceries, Money.of(10_000, BRL))
                .credit(bank, Money.of(9_000, BRL));

        UnbalancedEntryException ex = assertThrows(UnbalancedEntryException.class, builder::build);
        assertTrue(ex.getMessage().contains("does not balance"));
    }

    @Test
    void refusesToPostToAPlaceholderAccount() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        assets.setPlaceholder(true);
        Account bank = ledger.openAccount("bank", "Bank", BRL, assets);
        Account expenses = ledger.openRootAccount("expenses", "Expenses", AccountNature.EXPENSE, BRL);
        Account groceries = ledger.openAccount("groceries", "Groceries", BRL, expenses);

        // "assets" itself now has a child (bank), so it is also non-postable
        // for that reason alone — this exercises the placeholder branch by
        // posting directly against it instead.
        JournalEntry.Builder builder = JournalEntry.builder("e3", LocalDate.of(2026, 9, 6), "Invalid entry", BRL)
                .debit(assets, Money.of(1_000, BRL))
                .credit(bank, Money.of(1_000, BRL));

        assertThrows(AccountNotPostableException.class, builder::build);
    }

    @Test
    void childAccountMustShareItsParentsNature() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account rogue = new Account("rogue", "Wrong Account", AccountNature.LIABILITY, BRL);

        assertThrows(NatureMismatchException.class, () -> assets.addChild(rogue));
    }

    @Test
    void trialBalanceOnlyListsAccountsWithMovement() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Bank", BRL, assets);
        Account cash = ledger.openAccount("cash", "Wallet", BRL, assets);
        Account equity = ledger.openRootAccount("equity", "Equity", AccountNature.EQUITY, BRL);
        Account opening = ledger.openAccount("opening", "Opening Balance", BRL, equity);

        ledger.post(JournalEntry.builder("e4", LocalDate.of(2026, 1, 1), "Opening", BRL)
                .debit(bank, Money.of(1_000_00, BRL))
                .credit(opening, Money.of(1_000_00, BRL))
                .build());

        assertTrue(ledger.trialBalance().containsKey(bank));
        assertTrue(ledger.trialBalance().containsKey(opening));
        assertTrue(!ledger.trialBalance().containsKey(cash), "an account with no movement must not appear");
    }

    @Test
    void postingsStartPendingAndCanBeMarkedReconciled() {
        Ledger ledger = new Ledger();
        BankAndSalary chart = openBankAndSalary(ledger);
        Account bank = chart.bank();
        Account salary = chart.salary();

        JournalEntry payday = JournalEntry.builder("e5", LocalDate.of(2026, 9, 5), "Salary", BRL)
                .debit(bank, Money.of(500_000, BRL))
                .credit(salary, Money.of(500_000, BRL))
                .build();
        ledger.post(payday);

        Posting bankLeg = payday.postingsFor(bank).get(0);
        assertEquals(ReconciliationStatus.PENDING, ledger.reconciliationStatusOf(bankLeg));

        ledger.setReconciliationStatus(bankLeg, ReconciliationStatus.RECONCILED);
        assertEquals(ReconciliationStatus.RECONCILED, ledger.reconciliationStatusOf(bankLeg));
    }

    @Test
    void refusesToReconcileAPostingFromAnotherLedger() {
        Ledger ledgerA = new Ledger();
        BankAndSalary chartA = openBankAndSalary(ledgerA);
        Account bankA = chartA.bank();
        Account salaryA = chartA.salary();

        JournalEntry entry = JournalEntry.builder("e6", LocalDate.of(2026, 9, 5), "Salary", BRL)
                .debit(bankA, Money.of(500_000, BRL))
                .credit(salaryA, Money.of(500_000, BRL))
                .build();
        ledgerA.post(entry);
        Posting bankLeg = entry.postingsFor(bankA).get(0);

        Ledger ledgerB = new Ledger();
        assertThrows(ForeignPostingException.class,
                () -> ledgerB.setReconciliationStatus(bankLeg, ReconciliationStatus.CLEARED));
    }

    @Test
    void settingReconciliationStatusRefusesANullPosting() {
        Ledger ledger = new Ledger();
        assertThrows(NullPointerException.class,
                () -> ledger.setReconciliationStatus(null, ReconciliationStatus.CLEARED));
    }

    @Test
    void postingTheSameEntryTwiceIsRefused() {
        Ledger ledger = new Ledger();
        BankAndSalary chart = openBankAndSalary(ledger);
        Account bank = chart.bank();
        Account salary = chart.salary();

        JournalEntry payday = JournalEntry.builder("e7", LocalDate.of(2026, 9, 5), "Salary", BRL)
                .debit(bank, Money.of(500_000, BRL))
                .credit(salary, Money.of(500_000, BRL))
                .build();
        ledger.post(payday);

        assertThrows(DuplicateJournalEntryException.class, () -> ledger.post(payday));
        assertEquals(500_000L, ledger.balanceOf(bank), "a double-post must not double the balance");
    }

    @Test
    void postingASecondDistinctEntryWithAReusedIdIsAlsoRefused() {
        Ledger ledger = new Ledger();
        BankAndSalary chart = openBankAndSalary(ledger);
        Account bank = chart.bank();
        Account salary = chart.salary();

        ledger.post(JournalEntry.builder("dup-id", LocalDate.of(2026, 9, 5), "Salary", BRL)
                .debit(bank, Money.of(500_000, BRL))
                .credit(salary, Money.of(500_000, BRL))
                .build());

        JournalEntry secondEntryReusingTheSameId = JournalEntry.builder("dup-id", LocalDate.of(2026, 9, 6), "Other", BRL)
                .debit(bank, Money.of(1_000, BRL))
                .credit(salary, Money.of(1_000, BRL))
                .build();

        assertThrows(DuplicateJournalEntryException.class, () -> ledger.post(secondEntryReusingTheSameId));
    }

    @Test
    void balanceOfRefusesAnAccountFromAnotherLedger() {
        Ledger ledgerA = new Ledger();
        Account assetsA = ledgerA.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account bankA = ledgerA.openAccount("bank", "Bank", BRL, assetsA);

        Ledger ledgerB = new Ledger();
        Account bankB = ledgerB.openRootAccount("bank", "Bank", AccountNature.ASSET, BRL);

        assertThrows(ForeignAccountException.class, () -> ledgerA.balanceOf(bankB));
        assertThrows(ForeignAccountException.class, () -> ledgerA.quantityOf(bankB));
        // sanity: bankA's own id doesn't collide by construction, but a
        // same-id foreign account (bankB above, both id "bank") must not
        // silently read ledgerA's real balance for it.
        assertEquals(0L, ledgerA.balanceOf(bankA));
    }

    @Test
    void buildingAnEntryWithFewerThanTwoPostingsIsRefused() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Bank", BRL, assets);

        JournalEntry.Builder builder = JournalEntry.builder("e8", LocalDate.of(2026, 9, 5), "Loose leg", BRL)
                .debit(bank, Money.of(1_000, BRL));

        assertThrows(InsufficientPostingsException.class, builder::build);
    }

    @Test
    void addChildRefusesASelfParentCycle() {
        Account a = new Account("a", "A", AccountNature.ASSET, BRL);
        assertThrows(CyclicAccountException.class, () -> a.addChild(a));
    }

    @Test
    void addChildRefusesATwoNodeCycle() {
        Account a = new Account("a", "A", AccountNature.ASSET, BRL);
        Account b = new Account("b", "B", AccountNature.ASSET, BRL);
        a.addChild(b);

        assertThrows(CyclicAccountException.class, () -> b.addChild(a));
    }

    @Test
    void settingCodeDirectlyOnALedgerOwnedAccountStaysConsistentWithTheLedgerRegistry() {
        Ledger ledger = new Ledger();
        Account a = ledger.openRootAccount("a", "A", AccountNature.ASSET, BRL);
        Account b = ledger.openRootAccount("b", "B", AccountNature.ASSET, BRL);

        // calling Account.setCode() directly (not ledger.setAccountCode())
        // must still go through the ledger's whole-chart registry.
        a.setCode("100");
        assertEquals(a, ledger.findAccountByCode("100").orElse(null));

        assertThrows(DuplicateAccountCodeException.class, () -> b.setCode("100"));
        assertEquals("100", a.code().orElse(null), "a refused conflict must not have changed account A");
    }

    @Test
    void openAccountRefusesACommodityConflictingWithAnAlreadyRegisteredCode() {
        Ledger ledger = new Ledger();
        ledger.currency("BRL", 100);
        Account assets = ledger.openRootAccount("assets", "Assets", AccountNature.ASSET, BRL);

        Commodity rogueBrl = Commodity.currency("BRL", 1);
        assertThrows(CommodityConflictException.class,
                () -> ledger.openAccount("rogue", "Rogue", rogueBrl, assets));
    }
}
