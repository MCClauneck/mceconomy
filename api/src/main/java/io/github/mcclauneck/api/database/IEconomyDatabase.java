package io.github.mcclauneck.api.database;

import io.github.mcclauneck.api.models.Account;
import io.github.mcclauneck.api.models.Currency;
import io.github.mcclauneck.api.models.LogScope;
import io.github.mcclauneck.api.models.Wallet;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core interface for the Economy Database operations.
 * All methods must be asynchronous using CompletableFuture.
 */
public interface IEconomyDatabase {

    /**
     * Initializes the database connection and creates required schemas and tables.
     * @return A CompletableFuture representing the initialization task.
     */
    CompletableFuture<Void> initialize();

    /**
     * Closes the database connection gracefully.
     * @return A CompletableFuture representing the closing task.
     */
    CompletableFuture<Void> close();

    /**
     * Creates a new economy account if it does not exist.
     * @param accountType The type of the account (e.g., "player", "guild").
     * @param accountUuid The unique identifier for the account.
     * @return A CompletableFuture containing the created or existing Account.
     */
    CompletableFuture<Account> createAccount(String accountType, UUID accountUuid);

    /**
     * Retrieves an existing economy account.
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier for the account.
     * @return A CompletableFuture containing an Optional Account.
     */
    CompletableFuture<Optional<Account>> getAccount(String accountType, UUID accountUuid);

    /**
     * Creates a currency in the system, or updates its display name and
     * transferability when the system code already exists (upsert).
     * @param systemCode The unique system code for the currency (e.g., "gold").
     * @param displayName The display name of the currency.
     * @param transferable Whether the currency can be moved between accounts.
     * @return A CompletableFuture containing the created or updated Currency.
     */
    CompletableFuture<Currency> createCurrency(String systemCode, String displayName, boolean transferable);

    /**
     * Retrieves all registered currencies.
     * @return A CompletableFuture containing a list of all currencies.
     */
    CompletableFuture<List<Currency>> getAllCurrencies();

    /**
     * Retrieves a wallet for a specific account and currency.
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier for the account.
     * @param currencyId The ID of the currency.
     * @return A CompletableFuture containing the Wallet.
     */
    CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId);

    /**
     * Updates the balance of a wallet safely.
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier for the account.
     * @param currencyId The ID of the currency.
     * @param newAmount The new total amount to set.
     * @return A CompletableFuture containing the updated Wallet.
     */
    CompletableFuture<Wallet> setWalletBalance(String accountType, UUID accountUuid, int currencyId, long newAmount);

    /**
     * Inserts a transaction log into the database.
     * @param accountType The type of the account.
     * @param accountUuid The unique identifier for the account.
     * @param scope The scope of the log (e.g., SPEND, RECEIVE).
     * @param currencyId The ID of the currency.
     * @param amount The transaction amount.
     * @param transactionUuid The unique transaction receipt ID.
     * @param referenceType The type of reference (e.g., "shop").
     * @param referenceId The ID of the reference.
     * @return A CompletableFuture representing the insertion task.
     */
    CompletableFuture<Void> insertLog(String accountType, UUID accountUuid, LogScope scope, int currencyId, long amount, UUID transactionUuid, String referenceType, String referenceId);
}
