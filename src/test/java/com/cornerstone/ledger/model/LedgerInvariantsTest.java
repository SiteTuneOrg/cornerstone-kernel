package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The invariants of issues #7 and #8: what post, addChild and the value types refuse. */
class LedgerInvariantsTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);
    private static final Commodity PETR4 = Commodity.asset("PETR4", 1);
    private static final LocalDate DAY = LocalDate.of(2026, 10, 18);

    private record Chart(Ledger ledger, Account assets, Account bank, Account salary) {
    }

    private static Chart chart() {
        Ledger ledger = new Ledger();
        ledger.currency("BRL", 100);
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Banco", BRL, assets);
        Account revenue = ledger.openRootAccount("revenue", "Receitas", AccountNature.REVENUE, BRL);
        Account salary = ledger.openAccount("salary", "Salário", BRL, revenue);
        return new Chart(ledger, assets, bank, salary);
    }

    private static JournalEntry salary(Chart c, String id, Commodity currency, long amount) {
        return JournalEntry.builder(id, DAY, "salário", currency)
                .debit(c.bank(), Money.of(amount, currency))
                .credit(c.salary(), Money.of(amount, currency))
                .build();
    }

    // --- post checks commodities against the registry --------------------------------------

    @Test
    void postRefusesACurrencyRegisteredUnderAnotherScale() {
        Chart c = chart();
        c.ledger().post(salary(c, "e1", BRL, 1000));
        JournalEntry wrongScale = salary(c, "e2", Commodity.currency("BRL", 1), 10);

        assertThrows(CommodityConflictException.class, () -> c.ledger().post(wrongScale));
        assertEquals(1000, c.ledger().balanceOf(c.bank()));
        assertEquals(1, c.ledger().entries().size());
    }

    @Test
    void postRefusesACurrencyTheLedgerNeverRegistered() {
        Chart c = chart();
        Commodity gbp = Commodity.currency("GBP", 100);
        JournalEntry inGbp = JournalEntry.builder("e1", DAY, "libras", gbp)
                .debit(c.bank(), Money.of(100, gbp), Quantity.of(500, BRL))
                .credit(c.salary(), Money.of(100, gbp), Quantity.of(500, BRL))
                .build();

        assertThrows(UnknownCommodityException.class, () -> c.ledger().post(inGbp));
        assertTrue(c.ledger().entries().isEmpty());
    }

    @Test
    void postRefusesAQuantityInAnAdHocCommodityOfAnotherScale() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account bank = ledger.openAccount("bank", "Banco", BRL, assets);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);
        Commodity petr4Fine = Commodity.asset("PETR4", 1000);
        // Posting checks the quantity's code against the account's, not its scale.
        JournalEntry buy = JournalEntry.builder("buy", DAY, "compra", BRL)
                .debit(stock, Money.of(3000, BRL), Quantity.of(100, petr4Fine))
                .credit(bank, Money.of(3000, BRL))
                .build();

        assertThrows(CommodityConflictException.class, () -> ledger.post(buy));
        assertTrue(ledger.entries().isEmpty());
    }

    // --- post re-checks postability ---------------------------------------------------------

    @Test
    void postRefusesAnAccountHiddenAfterTheEntryWasBuilt() {
        Chart c = chart();
        JournalEntry entry = salary(c, "e1", BRL, 1000);
        c.bank().hide();

        assertThrows(AccountNotPostableException.class, () -> c.ledger().post(entry));
        assertTrue(c.ledger().entries().isEmpty());
    }

    @Test
    void postRefusesAnAccountThatGainedAChildAfterTheEntryWasBuilt() {
        Chart c = chart();
        JournalEntry entry = salary(c, "e1", BRL, 1000);
        c.ledger().openAccount("bank-sub", "Conta Corrente", BRL, c.bank());

        assertThrows(AccountNotPostableException.class, () -> c.ledger().post(entry));
    }

    @Test
    void postRefusesAnAccountThatBecameAPlaceholderAfterTheEntryWasBuilt() {
        Chart c = chart();
        JournalEntry entry = salary(c, "e1", BRL, 1000);
        c.salary().setPlaceholder(true);

        assertThrows(AccountNotPostableException.class, () -> c.ledger().post(entry));
    }

    // --- addChild respects the owning ledger ------------------------------------------------

    @Test
    void addChildRefusesGraftingAStandaloneAccountUnderALedgersAccount() {
        Chart c = chart();
        Account rogue = new Account("rogue", "Avulsa", AccountNature.ASSET, BRL);

        assertThrows(ForeignAccountException.class, () -> c.bank().addChild(rogue));
        assertTrue(c.bank().acceptsPostings());
        assertTrue(c.bank().children().isEmpty());
    }

    @Test
    void addChildRefusesALedgersAccountUnderAStandaloneOne() {
        Chart c = chart();
        Account standalone = new Account("standalone", "Avulsa", AccountNature.ASSET, BRL);

        ForeignAccountException refused = assertThrows(ForeignAccountException.class, () -> standalone.addChild(c.assets()));
        assertEquals("cannot attach \"Ativos\" under \"Avulsa\": they belong to different books (no Ledger vs a Ledger)",
                refused.getMessage());
        assertTrue(c.ledger().roots().contains(c.assets()));
    }

    @Test
    void addChildRefusesAnAccountOfAnotherLedger() {
        Chart a = chart();
        Chart b = chart();

        assertThrows(ForeignAccountException.class, () -> b.bank().addChild(a.assets()));
        assertEquals(Optional.empty(), a.assets().parent());
        assertEquals("Ativos", a.assets().path());
    }

    @Test
    void movingARootUnderAnotherAccountTakesItOutOfRoots() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account loose = ledger.openRootAccount("loose", "Investimentos", AccountNature.ASSET, BRL);

        assets.addChild(loose);

        assertEquals(List.of(assets), ledger.roots());
        assertEquals("Ativos:Investimentos", loose.path());
    }

    @Test
    void anExchangeLegWithoutAQuantityIsAShapeRefusal() {
        Commodity usd = Commodity.currency("USD", 100);
        Account broker = new Account("broker", "Broker USD", AccountNature.ASSET, usd);

        PostingShapeException refused = assertThrows(PostingShapeException.class,
                () -> Posting.debit(broker, Money.of(500, BRL)));
        assertEquals("broker", refused.accountId());
        assertEquals(Optional.empty(), refused.quantity());
    }

    @Test
    void standaloneAccountsStillFormATreeOfTheirOwn() {
        Account parent = new Account("p", "Pai", AccountNature.ASSET, BRL);
        Account child = new Account("c", "Filho", AccountNature.ASSET, BRL);

        parent.addChild(child);

        assertEquals("Pai:Filho", child.path());
    }

    @Test
    void hidingIsUnconditionalEvenWhereCanBeDisabledAdvisesAgainstIt() {
        Chart c = chart();
        c.salary().setSystem(true);
        assertFalse(c.salary().canBeDisabled());
        assertFalse(c.assets().canBeDisabled());

        c.salary().hide();
        c.assets().hide();

        assertTrue(c.salary().isHidden());
        assertTrue(c.assets().isHidden());
    }

    // --- a refusal leaves no commodity behind -----------------------------------------------

    @Test
    void openAccountRefusedByNameRegistersNoCommodity() {
        Chart c = chart();
        Commodity usd = Commodity.currency("USD", 100);

        assertThrows(IllegalArgumentException.class, () -> c.ledger().openAccount("usd", "  ", usd, c.assets()));
        assertThrows(IllegalArgumentException.class,
                () -> c.ledger().openRootAccount("usd", "A:B", AccountNature.ASSET, usd));
        assertEquals(Optional.empty(), c.ledger().findCommodity("USD"));
    }

    @Test
    void openAccountRefusedByADuplicateSiblingRegistersNoCommodity() {
        Chart c = chart();
        Commodity usd = Commodity.currency("USD", 100);

        assertThrows(DuplicateAccountException.class, () -> c.ledger().openAccount("usd", "Banco", usd, c.assets()));
        assertEquals(Optional.empty(), c.ledger().findCommodity("USD"));
        assertEquals(Optional.empty(), c.ledger().findAccount("usd"));
    }

    @Test
    void orCreateRefusedByShapeRegistersNoCommodity() {
        Chart c = chart();
        Commodity usd = Commodity.currency("USD", 100);

        assertThrows(AccountIdConflictException.class, () -> c.ledger().accountOrCreate("bank", "Banco", usd, c.assets()));
        assertThrows(AccountIdConflictException.class,
                () -> c.ledger().rootAccountOrCreate("assets", "Ativos", AccountNature.ASSET, usd));
        assertEquals(Optional.empty(), c.ledger().findCommodity("USD"));
    }

    // --- sibling names ----------------------------------------------------------------------

    @Test
    void siblingNamesAreComparedExactlySoStoredBooksStillValidate() {
        Ledger ledger = new Ledger();
        Account expenses = ledger.openRootAccount("exp", "Despesas", AccountNature.EXPENSE, BRL);
        ledger.openAccount("rent", "Aluguél", BRL, expenses);

        assertEquals("Aluguél ", ledger.openAccount("rent2", "Aluguél ", BRL, expenses).name());
        assertEquals("Alugue\u0301l", ledger.openAccount("rent3", "Alugue\u0301l", BRL, expenses).name());
        assertEquals("aluguél", ledger.openAccount("rent4", "aluguél", BRL, expenses).name());
        assertThrows(DuplicateAccountException.class, () -> ledger.openAccount("rent5", "Aluguél", BRL, expenses));
    }

    // --- overflow stays in the hierarchy ----------------------------------------------------

    @Test
    void balanceOverflowIsAnArithmeticRangeException() {
        Chart c = chart();
        c.ledger().post(salary(c, "e1", BRL, Long.MAX_VALUE));
        c.ledger().post(salary(c, "e2", BRL, 1));

        ArithmeticRangeException balance = assertThrows(ArithmeticRangeException.class, () -> c.ledger().balanceOf(c.bank()));
        assertEquals(ArithmeticRangeException.Reason.LONG_OVERFLOW, balance.reason());
        assertEquals("Ledger.balanceOf", balance.context());
        assertEquals(Optional.of(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)), balance.value());
        assertEquals("Ledger.unitsOf",
                assertThrows(ArithmeticRangeException.class, () -> c.ledger().unitsOf(c.bank())).context());
        assertEquals("Ledger.trialBalance",
                assertThrows(ArithmeticRangeException.class, () -> c.ledger().trialBalance()).context());
    }

    @Test
    void anEntryWhoseTotalsOverflowIsAnArithmeticRangeException() {
        Chart c = chart();
        JournalEntry.Builder builder = JournalEntry.builder("e", DAY, "grande", BRL)
                .debit(c.bank(), Money.of(Long.MAX_VALUE, BRL))
                .debit(c.bank(), Money.of(1, BRL))
                .credit(c.salary(), Money.of(1, BRL));

        assertEquals("Money.plus", assertThrows(ArithmeticRangeException.class, builder::build).context());
    }

    @Test
    void trialBalanceLeavesOutAnAccountThatClosedAtZero() {
        Chart c = chart();
        c.ledger().post(salary(c, "e1", BRL, 1000));
        Account cash = c.ledger().openAccount("cash", "Caixa", BRL, c.assets());
        c.ledger().post(JournalEntry.builder("e2", DAY, "saque", BRL)
                .debit(cash, Money.of(1000, BRL))
                .credit(c.bank(), Money.of(1000, BRL))
                .build());

        assertFalse(c.ledger().trialBalance().containsKey(c.bank()));
        assertEquals(1000L, c.ledger().trialBalance().get(cash));
    }

    // --- Posting shape carries its data -----------------------------------------------------

    @Test
    void aPostingShapeRefusalCarriesWhatWasGiven() {
        Ledger ledger = new Ledger();
        Account assets = ledger.openRootAccount("assets", "Ativos", AccountNature.ASSET, BRL);
        Account stock = ledger.openAccount("petr4", "PETR4", PETR4, assets);

        PostingShapeException refused = assertThrows(PostingShapeException.class,
                () -> Posting.debit(stock, Money.of(100, BRL)));
        assertEquals("petr4", refused.accountId());
        assertEquals(Money.of(100, BRL), refused.amount());
        assertEquals(Optional.empty(), refused.quantity());
        assertTrue(refused instanceof LedgerException);
    }

    // --- Price and ExchangeRate -------------------------------------------------------------

    @Test
    void priceAndExchangeRateAreValues() {
        Commodity usd = Commodity.currency("USD", 100);
        Price a = Price.of(Quantity.of(10, PETR4), Money.of(3550, BRL), DAY);
        Price b = Price.of(Quantity.of(10, PETR4), Money.of(3550, BRL), DAY);
        ExchangeRate x = ExchangeRate.of(Money.of(100, usd), Money.of(525, BRL), DAY);
        ExchangeRate y = ExchangeRate.of(Money.of(100, usd), Money.of(525, BRL), DAY);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, Price.of(Quantity.of(10, PETR4), Money.of(3551, BRL), DAY));
        assertNotEquals(a, Price.of(Quantity.of(11, PETR4), Money.of(3550, BRL), DAY));
        assertNotEquals(a, Price.of(Quantity.of(10, PETR4), Money.of(3550, BRL), DAY.plusDays(1)));
        assertNotEquals(a, (Object) "quote");
        assertEquals(x, y);
        assertEquals(x.hashCode(), y.hashCode());
        assertNotEquals(x, ExchangeRate.of(Money.of(100, usd), Money.of(526, BRL), DAY));
        assertNotEquals(x, ExchangeRate.of(Money.of(101, usd), Money.of(525, BRL), DAY));
        assertNotEquals(x, ExchangeRate.of(Money.of(100, usd), Money.of(525, BRL), DAY.plusDays(1)));
        assertNotEquals(x, (Object) "rate");
    }

    @Test
    void exchangeRateRefusesAZeroQuoteAndPriceAcceptsAWorthlessPaper() {
        Commodity usd = Commodity.currency("USD", 100);

        assertThrows(IllegalArgumentException.class, () -> ExchangeRate.of(Money.of(100, usd), Money.zero(BRL), DAY));
        assertEquals(Money.zero(BRL), Price.of(Quantity.of(10, PETR4), Money.zero(BRL), DAY).valueOf(Quantity.of(5, PETR4)));
    }
}
