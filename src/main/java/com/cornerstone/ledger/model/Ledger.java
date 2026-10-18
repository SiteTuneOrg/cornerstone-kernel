package com.cornerstone.ledger.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * A book: a chart of accounts plus the entries posted against it.
 *
 * <p>This class is the aggregate root: it owns the only door through which a
 * {@link Commodity} is registered, an {@link Account} is created, or a
 * {@link JournalEntry} is posted, so it can guarantee (not just document)
 * that nothing in {@link #entries()} ever references an account this ledger
 * doesn't know about.
 *
 * <p>{@link #currency}/{@link #asset} keep a registry of commodities rather
 * than letting callers construct them ad hoc, because
 * {@link Commodity#equals} compares only {@code code}: two independently
 * constructed {@code Commodity.currency("BRL", 100)} and
 * {@code Commodity.currency("BRL", 1)} would compare EQUAL despite disagreeing
 * on scale, and {@link Money} arithmetic (which checks {@code equals}, not
 * scale) would silently mix them. Routing every commodity through the ledger
 * makes it the one place a commodity code resolves to a shape, refusing a
 * second, different shape for the same code outright.
 *
 * <p>{@link #rootAccountOrCreate}/{@link #accountOrCreate} exist for the same
 * reason: a caller building a chart incrementally, row by row (a CSV
 * importer, typically), needs "the account named X, creating it on first
 * use" — which is exactly what {@link #accountsById} already answers
 * internally. Without these, every such caller re-implements its own
 * {@code Map<String, Account>} in front of a lookup the ledger already does,
 * one cache per layer that consumes this kernel instead of one.
 */
public final class Ledger {

    private final Map<String, Commodity> commoditiesByCode = new LinkedHashMap<>();
    private final Map<String, Account> accountsById = new LinkedHashMap<>();
    private final Map<String, Account> accountsByCode = new LinkedHashMap<>();
    private final List<Account> roots = new ArrayList<>();
    private final Map<String, JournalEntry> entriesById = new LinkedHashMap<>();
    private final Set<Posting> postedPostings = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Posting, ReconciliationStatus> reconciliationStatuses = new IdentityHashMap<>();

    /** Registers (or returns the already-registered) currency commodity for {@code code}. */
    public Commodity currency(String code, long scale) {
        return registerCommodity(Commodity.currency(code, scale));
    }

    /** Registers (or returns the already-registered) asset commodity for {@code code}. */
    public Commodity asset(String code, long scale) {
        return registerCommodity(Commodity.asset(code, scale));
    }

    private Commodity registerCommodity(Commodity requested) {
        Commodity resolved = resolveCommodity(requested);
        commoditiesByCode.putIfAbsent(resolved.code(), resolved);
        return resolved;
    }

    /**
     * The registered commodity for {@code requested}'s code, or
     * {@code requested} itself when the code is new — WITHOUT registering it,
     * so a call refused later (a blank name, a reused id) leaves no commodity
     * behind. Refuses a code registered under another scale or kind.
     */
    private Commodity resolveCommodity(Commodity requested) {
        Commodity existing = commoditiesByCode.get(requested.code());
        if (existing == null) {
            return requested;
        }
        if (existing.scale() != requested.scale() || existing.kind() != requested.kind()) {
            throw new CommodityConflictException(existing, requested);
        }
        return existing;
    }

    /** Refuses a commodity this ledger never registered, or one registered under another shape. */
    private void requireRegistered(Commodity commodity) {
        if (!commoditiesByCode.containsKey(commodity.code())) {
            throw new UnknownCommodityException(commodity);
        }
        resolveCommodity(commodity);
    }

    public Optional<Commodity> findCommodity(String code) {
        return Optional.ofNullable(commoditiesByCode.get(code));
    }

    public List<Commodity> commodities() {
        return List.copyOf(commoditiesByCode.values());
    }

    /**
     * Opens a root account (no parent) — the top of one arm of the chart of
     * accounts. {@code commodity} is resolved through the same registry
     * {@link #currency}/{@link #asset} use (registering it on first use, or
     * refusing a conflicting redefinition via {@link CommodityConflictException})
     * — an account can never be opened against a commodity code this ledger
     * has registered under a different scale or kind.
     */
    public Account openRootAccount(String id, String name, AccountNature nature, Commodity commodity) {
        requireUnusedId(id);
        Account account = new Account(id, name, nature, resolveCommodity(commodity));
        registerCommodity(account.commodity());
        account.bindLedger(this);
        accountsById.put(id, account);
        roots.add(account);
        return account;
    }

    /**
     * Opens {@code name} as a child of {@code parent}, inheriting nature-mismatch/duplicate-name
     * checks from {@link Account#addChild}. Refuses if {@code parent} isn't
     * owned by this ledger — see {@link #requireOwnedByThisLedger}. See
     * {@link #openRootAccount} for how {@code commodity} is resolved.
     */
    public Account openAccount(String id, String name, Commodity commodity, Account parent) {
        requireOwnedByThisLedger(parent);
        requireUnusedId(id);
        Account account = new Account(id, name, parent.nature(), resolveCommodity(commodity));
        account.bindLedger(this);
        parent.addChild(account);
        registerCommodity(account.commodity());
        accountsById.put(id, account);
        return account;
    }

    /**
     * The root account with this id if it exists; otherwise, creates it.
     * If an account with this id already exists but with a different name,
     * nature or commodity, refuses — an id collision between two DIFFERENT
     * intended accounts is a caller bug, not something to paper over by
     * silently handing back the wrong-shaped account.
     */
    public Account rootAccountOrCreate(String id, String name, AccountNature nature, Commodity commodity) {
        Commodity resolved = resolveCommodity(commodity);
        Account existing = accountsById.get(id);
        if (existing == null) {
            return openRootAccount(id, name, nature, resolved);
        }
        requireSameShape(existing, id, name, nature, resolved, Optional.empty());
        return existing;
    }

    /** Like {@link #rootAccountOrCreate}, for a child of {@code parent}. */
    public Account accountOrCreate(String id, String name, Commodity commodity, Account parent) {
        requireOwnedByThisLedger(parent);
        Commodity resolved = resolveCommodity(commodity);
        Account existing = accountsById.get(id);
        if (existing == null) {
            return openAccount(id, name, resolved, parent);
        }
        requireSameShape(existing, id, name, parent.nature(), resolved, Optional.of(parent));
        return existing;
    }

    private static void requireSameShape(
            Account existing, String id, String name, AccountNature nature, Commodity commodity, Optional<Account> parent) {
        boolean same = existing.name().equals(Account.normalizeName(name))
                && existing.nature() == nature
                && existing.commodity().equals(commodity)
                && existing.parent().equals(parent);
        if (!same) {
            throw AccountIdConflictException.differentShape(id);
        }
    }

    private void requireUnusedId(String id) {
        if (accountsById.containsKey(id)) {
            throw AccountIdConflictException.alreadyInUse(id);
        }
    }

    public Optional<Account> findAccount(String id) {
        return Optional.ofNullable(accountsById.get(id));
    }

    /** Called by {@link Account#addChild} when a root of this ledger is moved under another account. */
    void detachRoot(Account account) {
        roots.remove(account);
    }

    public List<Account> roots() {
        return Collections.unmodifiableList(roots);
    }

    public List<Account> accounts() {
        return List.copyOf(accountsById.values());
    }

    /**
     * Posts an already-validated {@link JournalEntry} (its {@code Builder}
     * refused to produce it unbalanced). Two things left to check here: that
     * every account it touches actually belongs to THIS ledger — a
     * {@code JournalEntry} built with an {@link Account} from a different
     * {@code Ledger} instance would otherwise post silently — and that this
     * exact {@link JournalEntry#id()} hasn't already been posted (whether
     * it's a retried call with the same object, or a second, distinct entry
     * built with a reused id): either would otherwise double-count every
     * posting in it with no signal to the caller.
     *
     * <p>{@code build()} validated the entry's shape against the accounts as
     * they were then; this checks it against the book as it is now. Every
     * commodity it carries (the entry's currency, each posting's money and
     * quantity) must be the one this ledger registered under that code —
     * {@link Commodity#equals} compares only the code, so an ad hoc
     * {@code BRL} of another scale would otherwise add centavos to reais —
     * and every account must still accept postings, since an
     * {@link Account} may have gained a child, been hidden or become a
     * placeholder after the entry was built.
     */
    public void post(JournalEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entriesById.containsKey(entry.id())) {
            throw new DuplicateJournalEntryException(entry.id());
        }
        // Validate every posting before mutating anything — a rejected
        // entry (e.g. its 3rd of 5 postings turns out foreign) must leave
        // no trace in entriesById/postedPostings, not a partial one.
        requireRegistered(entry.currency());
        for (Posting posting : entry.postings()) {
            requireOwnedByThisLedger(posting.account());
            requireRegistered(posting.amount().commodity());
            posting.quantity().ifPresent(q -> requireRegistered(q.commodity()));
        }
        JournalEntry.Builder.requirePostable(entry.postings());
        entriesById.put(entry.id(), entry);
        postedPostings.addAll(entry.postings());
    }

    /** Posted entries, in the order they were posted. */
    public List<JournalEntry> entries() {
        return List.copyOf(entriesById.values());
    }

    /**
     * {@link ReconciliationStatus} of {@code posting} — {@link ReconciliationStatus#PENDING}
     * until set otherwise. Does not check that {@code posting} belongs to
     * this ledger: an unknown posting simply reads as {@code PENDING}, the
     * same as one that genuinely hasn't been reconciled yet.
     */
    public ReconciliationStatus reconciliationStatusOf(Posting posting) {
        return reconciliationStatuses.getOrDefault(posting, ReconciliationStatus.PENDING);
    }

    /**
     * Marks {@code posting}'s progress toward being matched against an
     * external record — a bank statement, an OFX import — the same
     * mechanism most accounting tools use for this. Pairs with
     * {@link Account#code()} as the account-level half of that
     * correlation: {@code code} identifies which account a statement line
     * refers to, reconciliation status tracks whether a given posting has
     * been matched against one yet.
     */
    public void setReconciliationStatus(Posting posting, ReconciliationStatus status) {
        Objects.requireNonNull(posting, "posting");
        Objects.requireNonNull(status, "status");
        requirePostingBelongsToThisLedger(posting);
        reconciliationStatuses.put(posting, status);
    }

    private void requirePostingBelongsToThisLedger(Posting posting) {
        if (!postedPostings.contains(posting)) {
            throw new ForeignPostingException(posting);
        }
    }

    /**
     * Sets {@code account}'s {@link Account#code()}, enforcing uniqueness
     * across the WHOLE chart of accounts this ledger owns — not just
     * siblings, the narrower guarantee {@link Account#setCode} falls back to
     * for a standalone account with no owning {@code Ledger}. {@code account}
     * is always bound to the {@code Ledger} that opened it (see
     * {@link Account#bindLedger}), and its own {@code setCode} redirects
     * here whenever it's bound — so this is the only path that ever mutates
     * a ledger-owned account's code, and {@link #accountsByCode} can never
     * drift out of sync with what each account actually reports.
     */
    public void setAccountCode(Account account, String code) {
        requireOwnedByThisLedger(account);
        String normalized = Account.blankToNull(code);
        if (normalized != null) {
            Account owner = accountsByCode.get(normalized);
            if (owner != null && owner != account) {
                throw new DuplicateAccountCodeException(normalized, owner);
            }
        }
        Optional<String> previousCode = account.code();
        account.assignCode(normalized);
        previousCode.ifPresent(accountsByCode::remove);
        if (normalized != null) {
            accountsByCode.put(normalized, account);
        }
    }

    public Optional<Account> findAccountByCode(String code) {
        return Optional.ofNullable(accountsByCode.get(code));
    }

    private void requireOwnedByThisLedger(Account account) {
        if (accountsById.get(account.id()) != account) {
            throw new ForeignAccountException(account);
        }
    }

    /**
     * Signed balance of {@code account}: positive means a balance on its
     * normal side, negative means the account went the "wrong" way (an
     * overdrawn asset, a liability with a debit balance) — a legitimate,
     * reportable state that {@link Money}'s non-negative contract cannot
     * represent, which is why this returns a plain {@code long} rather than
     * a {@code Money}. Refuses an {@code account} not owned by this ledger —
     * {@link Account#equals} is id-based, so without this check a foreign
     * account whose id happens to collide with one of this ledger's own
     * would silently read this ledger's real balance for that id.
     *
     * <p>Each posting counts its {@link Money}, in its entry's currency. For an
     * account posted from entries in more than one currency (an exchange
     * leg), that sum mixes currencies: read {@link #unitsOf} instead.
     */
    public long balanceOf(Account account) {
        requireOwnedByThisLedger(account);
        long total = 0;
        for (JournalEntry entry : entriesById.values()) {
            for (Posting posting : entry.postingsFor(account)) {
                total = LedgerArithmetic.add(total, posting.signedAmount(), "Ledger.balanceOf");
            }
        }
        return total;
    }

    /**
     * Signed unit count of a position account: how many units of
     * {@code account.commodity()} are currently held. Empty for a cash
     * account (use {@link #unitsOf} for the amount of its currency it holds),
     * so a caller cannot mistake "never traded" for "confirmed zero
     * position". Refuses an {@code account} not owned by this ledger, same
     * reason as {@link #balanceOf}.
     */
    public OptionalLong quantityOf(Account account) {
        requireOwnedByThisLedger(account);
        if (account.commodity().kind() != CommodityKind.ASSET) {
            return OptionalLong.empty();
        }
        // Every posting on a position account carries a Quantity, so its units are its quantity.
        return OptionalLong.of(unitsOf(account));
    }

    /**
     * Signed balance of {@code account} in its OWN commodity: units for a
     * position account, and for a cash account the amount of its currency it
     * holds — its own-currency postings count their {@link Money}, and its
     * postings from entries in another currency (exchange legs, see
     * {@link Posting}) count their {@link Quantity}. Unlike {@link #balanceOf},
     * which adds each posting's money in its entry's currency, this never
     * mixes two currencies. Refuses an {@code account} not owned by this
     * ledger, same reason as {@link #balanceOf}.
     */
    public long unitsOf(Account account) {
        requireOwnedByThisLedger(account);
        long total = 0;
        for (JournalEntry entry : entriesById.values()) {
            for (Posting posting : entry.postingsFor(account)) {
                total = LedgerArithmetic.add(total, posting.signedQuantity().orElseGet(posting::signedAmount),
                        "Ledger.unitsOf");
            }
        }
        return total;
    }

    /**
     * Trial balance: every account whose signed balance is not zero (an
     * account that moved and closed at zero, such as a settled TRADING leg,
     * is left out), mapped to that balance. Like {@link #balanceOf}, each
     * posting counts its {@link Money} in its entry's currency, so in a book
     * with entries in more than one currency a line can add two currencies:
     * read {@link #unitsOf} per account instead. A single pass over every posted entry's postings
     * — not {@link #balanceOf} called once per account, which would re-walk
     * every entry for every account instead of once total.
     */
    public Map<Account, Long> trialBalance() {
        Map<Account, Long> totals = new LinkedHashMap<>();
        for (JournalEntry entry : entriesById.values()) {
            for (Posting posting : entry.postings()) {
                totals.merge(posting.account(), posting.signedAmount(),
                        (a, b) -> LedgerArithmetic.add(a, b, "Ledger.trialBalance"));
            }
        }
        totals.values().removeIf(balance -> balance == 0);
        return totals;
    }
}
