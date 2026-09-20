package com.cornerstone.ledger.model;

/**
 * Account species, in the vocabulary the interchange formats use, each
 * carrying the {@link AccountNature}
 * it implies. Several species collapse onto the same nature — that is the
 * whole reason this enum exists separately from {@code AccountNature}
 * instead of replacing it.
 */
public enum AccountType {
    ASSET(AccountNature.ASSET),
    BANK(AccountNature.ASSET),
    CASH(AccountNature.ASSET),
    STOCK(AccountNature.ASSET),
    MUTUAL(AccountNature.ASSET),
    RECEIVABLE(AccountNature.ASSET),
    TRADING(AccountNature.ASSET),
    LIABILITY(AccountNature.LIABILITY),
    CREDIT(AccountNature.LIABILITY),
    PAYABLE(AccountNature.LIABILITY),
    EQUITY(AccountNature.EQUITY),
    INCOME(AccountNature.REVENUE),
    EXPENSE(AccountNature.EXPENSE);

    private final AccountNature nature;

    AccountType(AccountNature nature) {
        this.nature = nature;
    }

    public AccountNature nature() {
        return nature;
    }

    /** Most generic type for a given nature — used when no species is declared. */
    public static AccountType defaultFor(AccountNature nature) {
        return switch (nature) {
            case ASSET -> ASSET;
            case LIABILITY -> LIABILITY;
            case EQUITY -> EQUITY;
            case REVENUE -> INCOME;
            case EXPENSE -> EXPENSE;
        };
    }
}
