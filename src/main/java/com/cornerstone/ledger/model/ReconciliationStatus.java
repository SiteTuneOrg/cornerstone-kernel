package com.cornerstone.ledger.model;

/**
 * How far a {@link Posting} has progressed toward being matched against an
 * external record (a bank statement, an OFX import): not yet checked
 * ({@link #PENDING}), seen on the statement but not formally reconciled
 * ({@link #CLEARED}), or matched and closed ({@link #RECONCILED}).
 */
public enum ReconciliationStatus {
    PENDING,
    CLEARED,
    RECONCILED
}
