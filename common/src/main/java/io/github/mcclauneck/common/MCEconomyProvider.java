package io.github.mcclauneck.common;

import io.github.mcclauneck.api.database.IEconomyDatabase;
import io.github.mcclauneck.api.models.Account;
import io.github.mcclauneck.api.models.Currency;
import io.github.mcclauneck.api.models.CurrencyDefinition;
import io.github.mcclauneck.api.models.LogScope;
import io.github.mcclauneck.api.models.Wallet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The central provider orchestrating all economy operations.
 * It abstracts database interactions and provides high-level transaction logic.
 */
public class MCEconomyProvider {

    /**
     * The built-in fallback currencies used only when the configuration does not
     * supply any. The keys are the unique currency system codes and the values
     * their definitions. Insertion order is preserved so currencies are created
     * in a predictable order. Server owners are expected to manage the real list
     * through {@code config.yml}; this constant exists purely as a safety net.
     */
    public static final Map<String, CurrencyDefinition> DEFAULT_CURRENCIES;

    static {
        Map<String, CurrencyDefinition> defaults = new LinkedHashMap<>();
        defaults.put("coin", new CurrencyDefinition("coin", "Coin", true));
        defaults.put("copper", new CurrencyDefinition("copper", "Copper", true));
        defaults.put("silver", new CurrencyDefinition("silver", "Silver", true));
        defaults.put("gold", new CurrencyDefinition("gold", "Gold", false));
        defaults.put("point", new CurrencyDefinition("point", "Point", false));
        defaults.put("token", new CurrencyDefinition("token", "Token", false));
        DEFAULT_CURRENCIES = Collections.unmodifiableMap(defaults);
    }

    private final IEconomyDatabase database;
    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();

    /**
     * The currencies this provider keeps present in the economy, keyed by their
     * unique system codes. These are sourced from the configuration so server
     * owners can add, remove, rename, or (un)lock currencies without code
     * changes. Insertion order is preserved.
     */
    private final Map<String, CurrencyDefinition> managedCurrencies;

    /**
     * Constructs the MCEconomyProvider using the built-in fallback currencies.
     * @param database The database implementation.
     */
    public MCEconomyProvider(IEconomyDatabase database) {
        this(database, null);
    }

    /**
     * Constructs the MCEconomyProvider with a configurable set of currencies.
     * <p>
     * When {@code managedCurrencies} is {@code null} or empty the built-in
     * {@link #DEFAULT_CURRENCIES} are used instead, ensuring the economy always
     * has at least one currency to work with.
     *
     * @param database The database implementation.
     * @param managedCurrencies The configured currencies keyed by system code,
     *                          typically parsed from {@code config.yml}.
     */
    public MCEconomyProvider(IEconomyDatabase database, Map<String, CurrencyDefinition> managedCurrencies) {
        this.database = database;
        if (managedCurrencies == null || managedCurrencies.isEmpty()) {
            this.managedCurrencies = DEFAULT_CURRENCIES;
        } else {
            this.managedCurrencies = Collections.unmodifiableMap(new LinkedHashMap<>(managedCurrencies));
        }
    }

    /**
     * Parses a raw list of {@code "code,Display Name,transferable"} entries (as
     * written in {@code config.yml}) into an ordered map of system codes to
     * currency definitions.
     * <p>
     * Each entry is split on commas: the first part is the system code
     * (lower-cased), the second the display name, and the optional third whether
     * the currency is transferable ({@code true} unless it equals
     * {@code "false"}, case-insensitively). When an entry has no display name
     * the code is reused as the display name. Blank entries and entries without
     * a usable code are skipped, and later duplicates of a code overwrite
     * earlier ones. Insertion order is preserved.
     *
     * @param entries The raw configuration entries; may be {@code null}.
     * @return An ordered map of system code to definition (never {@code null}).
     */
    public static Map<String, CurrencyDefinition> parseCurrencyList(List<String> entries) {
        Map<String, CurrencyDefinition> parsed = new LinkedHashMap<>();
        if (entries == null) {
            return parsed;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String[] parts = entry.split(",", 3);
            String code = parts[0].trim().toLowerCase();
            if (code.isEmpty()) {
                continue;
            }
            String display = parts.length > 1 ? parts[1].trim() : "";
            if (display.isEmpty()) {
                display = code;
            }
            boolean transferable = parts.length <= 2 || !"false".equalsIgnoreCase(parts[2].trim());
            parsed.put(code, new CurrencyDefinition(code, display, transferable));
        }
        return parsed;
    }

    /**
     * Shuts down the provider and closes database connections.
     * @return A CompletableFuture representing the shutdown process.
     */
    public CompletableFuture<Void> shutdown() {
        return this.database.close();
    }

    /**
     * Caches all currencies from the database into memory.
     * @return A CompletableFuture.
     */
    public CompletableFuture<Void> loadCurrencies() {
        return this.database.getAllCurrencies().thenAccept(currencies -> {
            currencyCache.clear();
            for (Currency c : currencies) {
                currencyCache.put(c.systemCode().toLowerCase(), c);
            }
        });
    }

    /**
     * Gets a cached currency by its system code.
     * @param systemCode The currency system code.
     * @return The Currency object or null if not found.
     */
    public Currency getCurrency(String systemCode) {
        return currencyCache.get(systemCode.toLowerCase());
    }

    /**
     * Retrieves all cached currencies.
     * @return A list of all loaded currencies.
     */
    public List<Currency> getCurrencies() {
        return new ArrayList<>(currencyCache.values());
    }

    /**
     * Returns the currencies this provider manages, keyed by system code.
     * <p>
     * The returned map is unmodifiable and reflects either the configured
     * currencies or the built-in fallback when none were configured.
     *
     * @return An unmodifiable, ordered map of system code to definition.
     */
    public Map<String, CurrencyDefinition> getManagedCurrencies() {
        return this.managedCurrencies;
    }

    /**
     * Ensures that every managed currency exists and matches its configured
     * definition, creating missing currencies and updating the display name or
     * transferability of currencies whose configuration changed.
     * <p>
     * The managed currencies come from the configuration (see
     * {@link #parseCurrencyList(List)}), so adding, removing, renaming, or
     * (un)locking entries in {@code config.yml} changes which currencies are
     * guaranteed to exist and how they behave. Out-of-date currencies are
     * determined from the in-memory cache, so {@link #loadCurrencies()} should
     * have completed at least once beforehand. If any currencies are written the
     * cache is refreshed afterwards. When every managed currency is already up
     * to date no database calls are made.
     *
     * @return A CompletableFuture that completes once all managed currencies exist.
     */
    public CompletableFuture<Void> ensureDefaultCurrencies() {
        List<CompletableFuture<Currency>> writes = new ArrayList<>();
        for (CurrencyDefinition definition : this.managedCurrencies.values()) {
            Currency existing = getCurrency(definition.systemCode());
            boolean upToDate = existing != null
                    && existing.displayName().equals(definition.displayName())
                    && existing.transferable() == definition.transferable();
            if (!upToDate) {
                writes.add(this.database.createCurrency(
                        definition.systemCode(), definition.displayName(), definition.transferable()));
            }
        }

        if (writes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]))
                .thenCompose(ignored -> loadCurrencies());
    }

    /**
     * Explicitly creates an economy account.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @return A CompletableFuture containing the account.
     */
    public CompletableFuture<Account> createAccount(String accountType, UUID accountUuid) {
        return this.database.createAccount(accountType, accountUuid);
    }

    /**
     * Checks if an account exists in the database.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @return A CompletableFuture returning true if the account exists, false otherwise.
     */
    public CompletableFuture<Boolean> hasAccount(String accountType, UUID accountUuid) {
        return this.database.getAccount(accountType, accountUuid)
                .thenApply(Optional::isPresent);
    }

    /**
     * Retrieves the current balance for an account's wallet.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @param currency The currency object.
     * @return A CompletableFuture containing the current balance.
     */
    public CompletableFuture<Long> getBalance(String accountType, UUID accountUuid, Currency currency) {
        if (currency == null) return CompletableFuture.completedFuture(0L);
        return this.database.getWallet(accountType, accountUuid, currency.id())
                .thenApply(Wallet::amount);
    }

    /**
     * Checks if an account has at least the specified amount of balance.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @param currency The currency object.
     * @param amount The amount to check against.
     * @return A CompletableFuture returning true if the balance is sufficient.
     */
    public CompletableFuture<Boolean> hasBalance(String accountType, UUID accountUuid, Currency currency, long amount) {
        return getBalance(accountType, accountUuid, currency)
                .thenApply(balance -> balance >= amount);
    }

    /**
     * Sets an absolute balance for an account's wallet and logs the difference.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @param currency The currency object.
     * @param newBalance The exact new balance to set.
     * @param scope The reason/scope for the transaction.
     * @param transactionUuid A unique UUID for this transaction (Idempotency).
     * @param referenceType The type of action generating the change.
     * @param referenceId The specific reference ID.
     * @return A CompletableFuture containing the new balance.
     */
    public CompletableFuture<Long> setBalance(String accountType, UUID accountUuid, Currency currency, long newBalance, LogScope scope, UUID transactionUuid, String referenceType, String referenceId) {
        if (newBalance < 0) throw new IllegalArgumentException("Balance cannot be negative");

        return this.database.getWallet(accountType, accountUuid, currency.id()).thenCompose(wallet -> {
            long difference = newBalance - wallet.amount();
            if (difference == 0) return CompletableFuture.completedFuture(wallet.amount());

            return this.database.setWalletBalance(accountType, accountUuid, currency.id(), newBalance)
                    .thenCompose(updatedWallet -> this.database.insertLog(accountType, accountUuid, scope, currency.id(), difference, transactionUuid, referenceType, referenceId)
                            .thenApply(v -> updatedWallet.amount()));
        });
    }

    /**
     * Adds currency to an account's wallet and logs the transaction.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @param currency The currency object.
     * @param amount The amount to add.
     * @param scope The reason/scope for adding (e.g. RECEIVE).
     * @param transactionUuid A unique UUID for this transaction (Idempotency).
     * @param referenceType The type of action generating the addition.
     * @param referenceId The specific reference ID.
     * @return A CompletableFuture containing the new balance.
     */
    public CompletableFuture<Long> addBalance(String accountType, UUID accountUuid, Currency currency, long amount, LogScope scope, UUID transactionUuid, String referenceType, String referenceId) {
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");
        
        return this.database.getWallet(accountType, accountUuid, currency.id()).thenCompose(wallet -> {
            long newBalance = wallet.amount() + amount;
            return this.database.setWalletBalance(accountType, accountUuid, currency.id(), newBalance)
                    .thenCompose(updatedWallet -> this.database.insertLog(accountType, accountUuid, scope, currency.id(), amount, transactionUuid, referenceType, referenceId)
                            .thenApply(v -> updatedWallet.amount()));
        });
    }

    /**
     * Removes currency from an account's wallet and logs the transaction.
     * @param accountType The type of account.
     * @param accountUuid The unique UUID of the account.
     * @param currency The currency object.
     * @param amount The amount to remove.
     * @param scope The reason/scope for removal (e.g. SPEND).
     * @param transactionUuid A unique UUID for this transaction (Idempotency).
     * @param referenceType The type of action generating the removal.
     * @param referenceId The specific reference ID.
     * @return A CompletableFuture containing the new balance, or exceptionally completes if insufficient funds.
     */
    public CompletableFuture<Long> removeBalance(String accountType, UUID accountUuid, Currency currency, long amount, LogScope scope, UUID transactionUuid, String referenceType, String referenceId) {
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");

        return this.database.getWallet(accountType, accountUuid, currency.id()).thenCompose(wallet -> {
            if (wallet.amount() < amount) {
                return CompletableFuture.failedFuture(new IllegalStateException("Insufficient funds"));
            }
            long newBalance = wallet.amount() - amount;
            return this.database.setWalletBalance(accountType, accountUuid, currency.id(), newBalance)
                    .thenCompose(updatedWallet -> this.database.insertLog(accountType, accountUuid, scope, currency.id(), -amount, transactionUuid, referenceType, referenceId)
                            .thenApply(v -> updatedWallet.amount()));
        });
    }

    /**
     * Transfers currency from one account to another securely.
     * <p>
     * Non-transferable currencies (where {@link Currency#transferable()} is
     * {@code false}, mirroring the {@code is_transferable} column) can never
     * move between accounts: the returned future completes exceptionally with
     * an {@link IllegalStateException} without touching any balance.
     * @param fromAccountType The account type of the sender.
     * @param fromAccountUuid The UUID of the sender.
     * @param toAccountType The account type of the receiver.
     * @param toAccountUuid The UUID of the receiver.
     * @param currency The currency object.
     * @param amount The amount to transfer.
     * @param transactionUuid A unique UUID for this transaction.
     * @param referenceType The type of reference (e.g. "player_transfer").
     * @param referenceId The specific reference ID.
     * @return A CompletableFuture returning true if successful, exceptionally completes otherwise.
     */
    public CompletableFuture<Boolean> transferBalance(String fromAccountType, UUID fromAccountUuid, String toAccountType, UUID toAccountUuid, Currency currency, long amount, UUID transactionUuid, String referenceType, String referenceId) {
        if (amount <= 0) throw new IllegalArgumentException("Transfer amount must be positive");
        if (!currency.transferable()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Currency " + currency.systemCode() + " is not transferable"));
        }

        return removeBalance(fromAccountType, fromAccountUuid, currency, amount, LogScope.SEND, transactionUuid, referenceType, referenceId)
                .thenCompose(newSenderBalance -> addBalance(toAccountType, toAccountUuid, currency, amount, LogScope.RECEIVE, transactionUuid, referenceType, referenceId)
                        .thenApply(newReceiverBalance -> true));
    }

    /**
     * Inserts a transaction record into the database independently.
     * @param transactionUuid The unique transaction ID.
     * @param accountType The account type.
     * @param accountUuid The account UUID.
     * @return A CompletableFuture representing the completion.
     */
    public CompletableFuture<Void> insertTransaction(UUID transactionUuid, String accountType, UUID accountUuid) {
        return this.database.insertLog(accountType, accountUuid, LogScope.RECEIVE, 0, 0, transactionUuid, "system", "manual_transaction");
    }

    /**
     * Logs an economic action to the database.
     * @param accountType The account type.
     * @param accountUuid The account UUID.
     * @param scope The action scope (e.g., SPEND, RECEIVE).
     * @param currency The currency.
     * @param amount The transaction amount.
     * @param transactionUuid Unique transaction ID (can be null).
     * @param referenceType The reference action type.
     * @param referenceId The reference ID.
     * @return A CompletableFuture.
     */
    public CompletableFuture<Void> insertLog(String accountType, UUID accountUuid, LogScope scope, Currency currency, long amount, UUID transactionUuid, String referenceType, String referenceId) {
        return this.database.insertLog(accountType, accountUuid, scope, currency.id(), amount, transactionUuid, referenceType, referenceId);
    }
}
