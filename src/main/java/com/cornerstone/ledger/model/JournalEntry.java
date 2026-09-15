package com.cornerstone.ledger.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A balanced double-entry transaction: two or more {@link Posting}s whose
 * debits sum to exactly its credits.
 *
 * <p>The main design decision here is that a {@code JournalEntry} <b>cannot
 * exist in an unbalanced state</b> — there is no public constructor, only
 * {@link Builder#build()}, and it validates before returning. "Valid by
 * construction" removes an entire class of bug: an object built in one
 * place and handed to another can never reach a caller half-validated,
 * because there is no way to obtain one that hasn't passed the check.
 */
public final class JournalEntry {

    private final String id;
    private final LocalDate date;
    private final String description;
    private final Commodity currency;
    private final List<Posting> postings;
    private final String referenceId;

    private JournalEntry(Builder builder) {
        this.id = builder.id;
        this.date = builder.date;
        this.description = builder.description;
        this.currency = builder.currency;
        this.postings = List.copyOf(builder.postings);
        this.referenceId = builder.referenceId;
    }

    public static Builder builder(String id, LocalDate date, String description, Commodity currency) {
        return new Builder(id, date, description, currency);
    }

    public String id() {
        return id;
    }

    public LocalDate date() {
        return date;
    }

    public String description() {
        return description;
    }

    public Commodity currency() {
        return currency;
    }

    public List<Posting> postings() {
        return postings;
    }

    public Optional<String> referenceId() {
        return Optional.ofNullable(referenceId);
    }

    public Money totalDebits() {
        return postings.stream()
                .filter(p -> p.side() == NormalSide.DEBIT)
                .map(Posting::amount)
                .reduce(Money.zero(currency), Money::plus);
    }

    public Money totalCredits() {
        return postings.stream()
                .filter(p -> p.side() == NormalSide.CREDIT)
                .map(Posting::amount)
                .reduce(Money.zero(currency), Money::plus);
    }

    public List<Posting> postingsFor(Account account) {
        return postings.stream().filter(p -> p.account().equals(account)).toList();
    }

    @Override
    public String toString() {
        return date + " " + description + " (" + postings.size() + " postings, " + totalDebits() + ")";
    }

    /**
     * Accumulates postings and validates on {@link #build()}. Every setter
     * returns {@code this} so a caller composes an entry as one expression;
     * nothing about the entry is observable — and nothing else can construct
     * a {@code JournalEntry} — until the invariant holds.
     */
    public static final class Builder {
        private final String id;
        private final LocalDate date;
        private final String description;
        private final Commodity currency;
        private final List<Posting> postings = new ArrayList<>();
        private String referenceId;

        private Builder(String id, LocalDate date, String description, Commodity currency) {
            this.id = Objects.requireNonNull(id, "id");
            this.date = Objects.requireNonNull(date, "date");
            this.description = Objects.requireNonNull(description, "description");
            this.currency = Objects.requireNonNull(currency, "currency");
        }

        public Builder debit(Account account, Money amount) {
            return posting(Posting.debit(account, amount));
        }

        public Builder debit(Account account, Money amount, String memo) {
            return posting(Posting.debit(account, amount, memo));
        }

        public Builder credit(Account account, Money amount) {
            return posting(Posting.credit(account, amount));
        }

        public Builder credit(Account account, Money amount, String memo) {
            return posting(Posting.credit(account, amount, memo));
        }

        /** For a position account: {@code amount} is cost/proceeds in this entry's currency, {@code quantity} the unit change. */
        public Builder debit(Account account, Money amount, Quantity quantity) {
            return posting(Posting.debit(account, amount, quantity));
        }

        public Builder credit(Account account, Money amount, Quantity quantity) {
            return posting(Posting.credit(account, amount, quantity));
        }

        public Builder debit(Account account, Money amount, Quantity quantity, String memo) {
            return posting(Posting.debit(account, amount, quantity, memo));
        }

        public Builder credit(Account account, Money amount, Quantity quantity, String memo) {
            return posting(Posting.credit(account, amount, quantity, memo));
        }

        public Builder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        private Builder posting(Posting posting) {
            if (!posting.amount().commodity().equals(currency)) {
                throw new CurrencyMismatchException(currency, posting.amount().commodity());
            }
            postings.add(posting);
            return this;
        }

        /**
         * Validates and returns the entry. Requires at least two postings
         * and debits == credits — the two structural invariants of a
         * balanced double-entry transaction, enforced here at the one point
         * where an instance could come into being.
         *
         * <p>One exception: a SINGLE quantity-only posting (zero money, a
         * real unit change) is valid on its own — a stock split or a
         * zero-cost bonus issue moves no money at all, so there is no second
         * leg for it to balance against. {@link Posting}'s own no-op guard
         * already rules out the degenerate "single posting, nothing
         * happened" case, so admitting exactly one posting here is safe.
         */
        public JournalEntry build() {
            boolean standaloneQuantityAdjustment = postings.size() == 1
                    && postings.get(0).amount().isZero()
                    && postings.get(0).quantity().isPresent();
            if (postings.size() < 2 && !standaloneQuantityAdjustment) {
                throw new InsufficientPostingsException(id, postings.size());
            }
            for (Account account : postings.stream().map(Posting::account).distinct().toList()) {
                if (!account.acceptsPostings()) {
                    throw new AccountNotPostableException(account, reasonNotPostable(account));
                }
            }
            JournalEntry entry = new JournalEntry(this);
            Money debits = entry.totalDebits();
            Money credits = entry.totalCredits();
            if (!debits.equals(credits)) {
                throw new UnbalancedEntryException(debits, credits);
            }
            return entry;
        }

        private static String reasonNotPostable(Account account) {
            if (account.isPlaceholder()) return "is a placeholder account";
            if (account.isSystem()) return "is managed by the system";
            if (account.isHidden()) return "is hidden";
            return "has child accounts";
        }
    }
}
