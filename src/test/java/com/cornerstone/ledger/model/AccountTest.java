package com.cornerstone.ledger.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Account} as a standalone entity, built directly (no {@link Ledger}):
 * the tree-edge rules enforced by {@link Account#addChild}, the local-only
 * code uniqueness of {@link Account#setCode}, and the flags that decide
 * whether it accepts postings.
 */
class AccountTest {

    private static final Commodity BRL = Commodity.currency("BRL", 100);

    private static Account asset(String id, String name) {
        return new Account(id, name, AccountNature.ASSET, BRL);
    }

    @Test
    void refusesABlankOrColonName() {
        assertThrows(IllegalArgumentException.class, () -> asset("a", "  "));
        assertThrows(IllegalArgumentException.class, () -> asset("a", "Assets:Bank"));
    }

    @Test
    void refusesToAttachAnAccountUnderItselfOrUnderItsOwnDescendant() {
        Account assets = asset("assets", "Assets");
        Account bank = asset("bank", "Bank");
        assets.addChild(bank);

        assertThrows(CyclicAccountException.class, () -> assets.addChild(assets));
        assertThrows(CyclicAccountException.class, () -> bank.addChild(assets));
    }

    @Test
    void refusesAChildOfADifferentNature() {
        Account assets = asset("assets", "Assets");
        Account salary = new Account("salary", "Salary", AccountNature.REVENUE, BRL);
        assertThrows(NatureMismatchException.class, () -> assets.addChild(salary));
    }

    @Test
    void refusesADuplicateSiblingNameOrCode() {
        Account assets = asset("assets", "Assets");
        Account bank = asset("bank", "Bank");
        bank.setCode("1.1");
        assets.addChild(bank);

        assertThrows(DuplicateAccountException.class, () -> assets.addChild(asset("bank2", "Bank")));

        Account cash = asset("cash", "Cash");
        cash.setCode("1.1");
        assertThrows(DuplicateAccountCodeException.class, () -> assets.addChild(cash));
    }

    @Test
    void reparentingMovesTheAccountOutOfItsPreviousParent() {
        Account assets = asset("assets", "Assets");
        Account banks = asset("banks", "Banks");
        Account checking = asset("checking", "Checking");
        assets.addChild(banks);
        assets.addChild(checking);

        banks.addChild(checking);

        assertEquals(1, assets.children().size());
        assertEquals(Optional.of(banks), checking.parent());
        assertEquals("Assets:Banks:Checking", checking.path());
        assertEquals("Assets/Banks/Checking", checking.path("/"));
        assertEquals("Assets:Banks:Checking", checking.toString());
        assertTrue(checking.isDescendantOf(assets));
        assertFalse(assets.isDescendantOf(checking));
    }

    @Test
    void childrenViewIsReadOnly() {
        Account assets = asset("assets", "Assets");
        assertThrows(UnsupportedOperationException.class, () -> assets.children().add(asset("x", "X")));
    }

    @Test
    void acceptsPostingsOnlyAsAVisibleNonPlaceholderNonSystemLeaf() {
        Account assets = asset("assets", "Assets");
        Account bank = asset("bank", "Bank");
        assertTrue(bank.acceptsPostings());

        assets.addChild(bank);
        assertFalse(assets.acceptsPostings(), "a parent is organizational");

        bank.setPlaceholder(true);
        assertTrue(bank.isPlaceholder());
        assertFalse(bank.acceptsPostings());
        bank.setPlaceholder(false);

        bank.hide();
        assertTrue(bank.isHidden());
        assertFalse(bank.acceptsPostings());
        bank.unhide();
        assertFalse(bank.isHidden());

        bank.setSystem(true);
        assertTrue(bank.isSystem());
        assertFalse(bank.acceptsPostings());
    }

    @Test
    void canBeDisabledOnlyWhenNeitherRootNorSystem() {
        Account assets = asset("assets", "Assets");
        Account bank = asset("bank", "Bank");
        assets.addChild(bank);

        assertFalse(assets.canBeDisabled());
        assertTrue(bank.canBeDisabled());
        bank.setSystem(true);
        assertFalse(bank.canBeDisabled());
    }

    @Test
    void renameRefusesASiblingsNameButAllowsKeepingItsOwn() {
        Account assets = asset("assets", "Assets");
        Account bank = asset("bank", "Bank");
        assets.addChild(bank);
        assets.addChild(asset("cash", "Cash"));

        assertThrows(DuplicateAccountException.class, () -> bank.rename("Cash"));
        bank.rename("Bank");
        bank.rename("Checking");
        assertEquals("Checking", bank.name());
        assertThrows(IllegalArgumentException.class, () -> bank.rename("A:B"));
    }

    @Test
    void setTypeRefusesATypeOfAnotherNature() {
        Account bank = asset("bank", "Bank");
        assertEquals(AccountType.ASSET, bank.type());

        bank.setType(AccountType.BANK);
        assertEquals(AccountType.BANK, bank.type());
        assertThrows(IllegalArgumentException.class, () -> bank.setType(AccountType.CREDIT));
    }

    @Test
    void standaloneSetCodeIsUniqueAmongSiblingsOnlyAndBlankClearsIt() {
        Account assets = asset("assets", "Assets");
        Account bank = asset("bank", "Bank");
        Account cash = asset("cash", "Cash");
        assets.addChild(bank);
        assets.addChild(cash);

        bank.setCode("1.1");
        assertEquals(Optional.of("1.1"), bank.code());
        assertThrows(DuplicateAccountCodeException.class, () -> cash.setCode("1.1"));
        bank.setCode("1.1"); // re-setting its own code is not a clash

        bank.setCode("  ");
        assertEquals(Optional.empty(), bank.code());
        cash.setCode("1.1");
        assertEquals(Optional.of("1.1"), cash.code());
    }

    @Test
    void optionalTextFieldsTreatBlankAsAbsent() {
        Account bank = asset("bank", "Bank");

        bank.setDescription("Main checking account");
        bank.setNotes("opened 2020");
        assertEquals(Optional.of("Main checking account"), bank.description());
        assertEquals(Optional.of("opened 2020"), bank.notes());

        bank.setDescription(" ");
        bank.setNotes(null);
        assertEquals(Optional.empty(), bank.description());
        assertEquals(Optional.empty(), bank.notes());
    }

    @Test
    void booleanFlagsRoundTrip() {
        Account bank = asset("bank", "Bank");
        assertFalse(bank.isTaxRelated());
        assertFalse(bank.isAutoInterestTransfer());

        bank.setTaxRelated(true);
        bank.setAutoInterestTransfer(true);
        assertTrue(bank.isTaxRelated());
        assertTrue(bank.isAutoInterestTransfer());
    }

    @Test
    void identityIsTheIdAlone() {
        Account a = asset("bank", "Bank");
        Account sameId = new Account("bank", "Other", AccountNature.EXPENSE, BRL);
        assertEquals(a, sameId);
        assertEquals(a.hashCode(), sameId.hashCode());
        assertNotEquals(a, asset("cash", "Bank"));
        assertEquals(AccountNature.ASSET, a.nature());
        assertEquals(BRL, a.commodity());
    }
}
