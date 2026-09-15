package com.cornerstone.ledger.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A node in the chart of accounts.
 *
 * <p>The tree is real: an {@code Account} holds its own {@link #parent} and
 * {@link #children}, and the invariants that matter attach to the operation
 * that could break them, not to a separate validation pass run over the
 * whole forest:
 *
 * <ul>
 *   <li>{@link #addChild(Account)} refuses a cycle, a nature mismatch, or a
 *       duplicate sibling name/code at the moment the edge would be
 *       created — including the reparenting path, where an already-attached
 *       account moves under a new parent, so attaching under one's own
 *       descendant (or under oneself) is refused there too, not just for a
 *       freshly-constructed child.</li>
 *   <li>{@link #acceptsPostings()} reads the object's own fields; no
 *       separate pass over the whole chart is needed because "has children"
 *       is {@code !children.isEmpty()} on the object itself.</li>
 * </ul>
 *
 * <p>The cost of this choice: an {@code Account} is not a value that can be
 * freely copied and compared by content — it is an entity, identity is
 * {@link #id}, and mutating the tree (adding a child) mutates a shared
 * object graph. That is the normal tradeoff of rich, stateful objects over
 * immutable data plus free functions, not a bug.
 */
public final class Account {

    private final String id;
    private String name;
    private final AccountNature nature;
    private AccountType type;
    private final Commodity commodity;
    private Account parent;
    private final List<Account> children = new ArrayList<>();
    private boolean placeholder;
    private boolean system;
    private boolean hidden;
    private String code;
    private String description;
    private String notes;
    private boolean taxRelated;
    private boolean autoInterestTransfer;
    private Ledger ledger;

    public Account(String id, String name, AccountNature nature, Commodity commodity) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = requireValidName(name);
        this.nature = Objects.requireNonNull(nature, "nature");
        this.type = AccountType.defaultFor(nature);
        this.commodity = Objects.requireNonNull(commodity, "commodity");
    }

    /** Called once by {@link Ledger#openRootAccount}/{@link Ledger#openAccount} right after construction. */
    void bindLedger(Ledger ledger) {
        this.ledger = ledger;
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("blank account name");
        }
        if (name.contains(":")) {
            // ':' is the path separator used by full_name()/path(); a name
            // containing it would produce a path that re-splits into more
            // levels than actually exist.
            throw new IllegalArgumentException(
                    "account name cannot contain \":\" (hierarchy separator): " + name);
        }
        return name;
    }

    /**
     * Attaches {@code child} under this account. Enforces, at the point of
     * attachment, the rules that matter for a tree edge: no cycle, nature
     * inheritance, and unique sibling names/codes.
     */
    public void addChild(Account child) {
        Objects.requireNonNull(child, "child");
        if (child == this || this.isDescendantOf(child)) {
            throw new CyclicAccountException(child, this);
        }
        if (child.nature != this.nature) {
            throw new NatureMismatchException(child, this);
        }
        boolean nameClash = children.stream().anyMatch(c -> c.name.equals(child.name));
        if (nameClash) {
            throw new DuplicateAccountException(child.name, this);
        }
        if (child.code != null) {
            children.stream()
                    .filter(c -> child.code.equals(c.code))
                    .findFirst()
                    .ifPresent(clash -> { throw new DuplicateAccountCodeException(child.code, clash); });
        }
        if (child.parent != null) {
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
    }

    /**
     * Whether this account can directly receive a {@link Posting}: not a
     * placeholder, not a system account, not hidden, and not itself a parent
     * of other accounts (a node with children is organizational, not a
     * posting target).
     */
    public boolean acceptsPostings() {
        return !placeholder && !system && !hidden && children.isEmpty();
    }

    /** Whether this account is eligible to be disabled: never a root, never a system account. */
    public boolean canBeDisabled() {
        return parent != null && !system;
    }

    public boolean isDescendantOf(Account other) {
        for (Account a = this.parent; a != null; a = a.parent) {
            if (a == other) {
                return true;
            }
        }
        return false;
    }

    /** Full path from the root, e.g. "Ativos:Banco:Conta Corrente". */
    public String path() {
        return path(":");
    }

    public String path(String separator) {
        return parent == null ? name : parent.path(separator) + separator + name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void rename(String newName) {
        String validated = requireValidName(newName);
        if (parent != null) {
            boolean clash = parent.children.stream()
                    .anyMatch(sibling -> sibling != this && sibling.name.equals(validated));
            if (clash) {
                throw new DuplicateAccountException(validated, parent);
            }
        }
        this.name = validated;
    }

    public AccountNature nature() {
        return nature;
    }

    public AccountType type() {
        return type;
    }

    public void setType(AccountType type) {
        if (type.nature() != this.nature) {
            throw new IllegalArgumentException(
                    "type " + type + " implies nature " + type.nature() + ", account is " + nature);
        }
        this.type = type;
    }

    public Commodity commodity() {
        return commodity;
    }

    public Optional<Account> parent() {
        return Optional.ofNullable(parent);
    }

    public List<Account> children() {
        return Collections.unmodifiableList(children);
    }

    public boolean isPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(boolean placeholder) {
        this.placeholder = placeholder;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** Soft-deletes the account: stops accepting new postings, history untouched. */
    public void hide() {
        this.hidden = true;
    }

    public void unhide() {
        this.hidden = false;
    }

    /** User-visible accounting code, e.g. "1.1.01" — distinct from {@link #id()}. */
    public Optional<String> code() {
        return Optional.ofNullable(code);
    }

    /**
     * Sets the accounting code. If this account belongs to a {@link Ledger}
     * (created via {@link Ledger#openRootAccount}/{@link Ledger#openAccount}),
     * delegates entirely to {@link Ledger#setAccountCode} — uniqueness across
     * the whole chart of accounts, not just siblings — so calling this
     * method directly can never desync the {@code Ledger}'s global registry.
     *
     * <p>Without a {@code Ledger} (a standalone account, built via {@link
     * #Account(String, String, AccountNature, Commodity)} directly), the
     * only guarantee possible is local: unique among direct siblings (the
     * same rule as {@link #name}), and only when the code isn't blank —
     * several accounts can go without a code.
     */
    public void setCode(String code) {
        if (ledger != null) {
            ledger.setAccountCode(this, code);
            return;
        }
        String normalized = blankToNull(code);
        if (normalized != null && parent != null) {
            parent.children.stream()
                    .filter(sibling -> sibling != this && normalized.equals(sibling.code))
                    .findFirst()
                    .ifPresent(clash -> { throw new DuplicateAccountCodeException(normalized, clash); });
        }
        this.code = normalized;
    }

    /** Sets {@link #code} directly, no validation — callers must have already validated uniqueness. */
    void assignCode(String normalizedCode) {
        this.code = normalizedCode;
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public void setDescription(String description) {
        this.description = blankToNull(description);
    }

    public Optional<String> notes() {
        return Optional.ofNullable(notes);
    }

    public void setNotes(String notes) {
        this.notes = blankToNull(notes);
    }

    public boolean isTaxRelated() {
        return taxRelated;
    }

    public void setTaxRelated(boolean taxRelated) {
        this.taxRelated = taxRelated;
    }

    public boolean isAutoInterestTransfer() {
        return autoInterestTransfer;
    }

    public void setAutoInterestTransfer(boolean autoInterestTransfer) {
        this.autoInterestTransfer = autoInterestTransfer;
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Account other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return path();
    }
}
