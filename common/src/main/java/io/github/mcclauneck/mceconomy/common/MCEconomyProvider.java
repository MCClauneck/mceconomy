package io.github.mcclauneck.mceconomy.common;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.models.Account;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.common.balance.BalanceLedger;
import io.github.mcclauneck.mceconomy.common.currency.CurrencyListParser;
import io.github.mcclauneck.mceconomy.common.currency.CurrencyRegistry;
import io.github.mcclauneck.mceconomy.common.currency.DefaultCurrencies;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The central provider orchestrating all economy operations.
 * <p>
 * It is the one entry point other plugins call, and a facade over the parts
 * that do the work: the {@link CurrencyRegistry} (the currency cache and the
 * configured currencies), the {@link BalanceLedger} (logged balance changes),
 * and the {@link IEconomyDatabase} for accounts and raw log rows.
 */
public class MCEconomyProvider {

    /**
     * The built-in fallback currencies used only when the configuration does not
     * supply any. The keys are the unique currency system codes and the values
     * their definitions. Insertion order is preserved so currencies are created
     * in a predictable order. Server owners are expected to manage the real list
     * through {@code config.yml}; this constant exists purely as a safety net.
     */
    public static final Map<String, CurrencyDefinition> DEFAULT_CURRENCIES = DefaultCurrencies.DEFINITIONS;

    private final IEconomyDatabase database;
    private final CurrencyRegistry currencies;
    private final BalanceLedger ledger;

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
        this.currencies = new CurrencyRegistry(database, managedCurrencies);
        this.ledger = new BalanceLedger(database);
    }

    /**
     * Parses a raw list of {@code "code,Display Name,transferable"} entries (as
     * written in {@code config.yml}) into an ordered map of system codes to
     * currency definitions. See {@link CurrencyListParser#parse(List)} for the
     * rules.
     *
     * @param entries The raw configuration entries; may be {@code null}.
     * @return An ordered map of system code to definition (never {@code null}).
     */
    public static Map<String, CurrencyDefinition> parseCurrencyList(List<String> entries) {
        return CurrencyListParser.parse(entries);
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
        return this.currencies.load();
    }

    /**
     * Gets a cached currency by its system code.
     * @param systemCode The currency system code.
     * @return The Currency object or null if not found.
     */
    public Currency getCurrency(String systemCode) {
        return this.currencies.get(systemCode);
    }

    /**
     * Retrieves all cached currencies.
     * @return A list of all loaded currencies.
     */
    public List<Currency> getCurrencies() {
        return this.currencies.all();
    }

    /**
     * Returns the cached currencies in a stable display order: the configured
     * (managed) currencies first, in configuration order, then any remaining
     * database currencies sorted by system code.
     *
     * @return The currencies in display order.
     */
    public List<Currency> getCurrenciesInDisplayOrder() {
        return this.currencies.displayOrder();
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
        return this.currencies.managed();
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
        return this.currencies.ensureManaged();
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
        return this.ledger.balance(accountType, accountUuid, currency);
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
        return this.ledger.set(accountType, accountUuid, currency, newBalance, scope, transactionUuid, referenceType, referenceId);
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
        return this.ledger.add(accountType, accountUuid, currency, amount, scope, transactionUuid, referenceType, referenceId);
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
        return this.ledger.remove(accountType, accountUuid, currency, amount, scope, transactionUuid, referenceType, referenceId);
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
        return this.ledger.transfer(fromAccountType, fromAccountUuid, toAccountType, toAccountUuid, currency, amount, transactionUuid, referenceType, referenceId);
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
