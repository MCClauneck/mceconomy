package io.github.mcclauneck.mceconomy.common.balance;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.api.models.Wallet;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Reads and moves wallet balances, writing one log row for every change.
 * <p>
 * Every write is "set the wallet, then log the difference", so a balance never
 * changes without its {@code economy_logs} row.
 */
public class BalanceLedger {

    private final IEconomyDatabase database;

    /**
     * @param database The database the wallets and logs are stored in.
     */
    public BalanceLedger(IEconomyDatabase database) {
        this.database = database;
    }

    /**
     * @return The wallet's balance, or {@code 0} when {@code currency} is {@code null}.
     */
    public CompletableFuture<Long> balance(String accountType, UUID accountUuid, Currency currency) {
        if (currency == null) return CompletableFuture.completedFuture(0L);
        return this.database.getWallet(accountType, accountUuid, currency.id())
                .thenApply(Wallet::amount);
    }

    /**
     * Sets an absolute balance and logs the difference; logs nothing when the
     * balance is unchanged.
     *
     * @return The new balance.
     * @throws IllegalArgumentException When {@code newBalance} is negative.
     */
    public CompletableFuture<Long> set(String accountType, UUID accountUuid, Currency currency, long newBalance,
                                       LogScope scope, UUID transactionUuid, String referenceType, String referenceId) {
        if (newBalance < 0) throw new IllegalArgumentException("Balance cannot be negative");

        return this.database.getWallet(accountType, accountUuid, currency.id()).thenCompose(wallet -> {
            long difference = newBalance - wallet.amount();
            if (difference == 0) return CompletableFuture.completedFuture(wallet.amount());
            return writeAndLog(accountType, accountUuid, currency, newBalance, difference,
                    scope, transactionUuid, referenceType, referenceId);
        });
    }

    /**
     * Adds to a balance and logs the amount.
     *
     * @return The new balance.
     * @throws IllegalArgumentException When {@code amount} is negative.
     */
    public CompletableFuture<Long> add(String accountType, UUID accountUuid, Currency currency, long amount,
                                       LogScope scope, UUID transactionUuid, String referenceType, String referenceId) {
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");

        return this.database.getWallet(accountType, accountUuid, currency.id()).thenCompose(wallet ->
                writeAndLog(accountType, accountUuid, currency, wallet.amount() + amount, amount,
                        scope, transactionUuid, referenceType, referenceId));
    }

    /**
     * Removes from a balance and logs the negated amount.
     *
     * @return The new balance, or a future failed with {@link IllegalStateException}
     *         when the balance is insufficient.
     * @throws IllegalArgumentException When {@code amount} is negative.
     */
    public CompletableFuture<Long> remove(String accountType, UUID accountUuid, Currency currency, long amount,
                                          LogScope scope, UUID transactionUuid, String referenceType, String referenceId) {
        if (amount < 0) throw new IllegalArgumentException("Amount cannot be negative");

        return this.database.getWallet(accountType, accountUuid, currency.id()).thenCompose(wallet -> {
            if (wallet.amount() < amount) {
                return CompletableFuture.failedFuture(new IllegalStateException("Insufficient funds"));
            }
            return writeAndLog(accountType, accountUuid, currency, wallet.amount() - amount, -amount,
                    scope, transactionUuid, referenceType, referenceId);
        });
    }

    /**
     * Moves an amount between two accounts: a {@code send} debit, then a
     * {@code receive} credit, under one transaction UUID. Non-transferable
     * currencies fail with {@link IllegalStateException} without touching any balance.
     *
     * @return {@code true} once both sides are written.
     * @throws IllegalArgumentException When {@code amount} is not positive.
     */
    public CompletableFuture<Boolean> transfer(String fromAccountType, UUID fromAccountUuid,
                                               String toAccountType, UUID toAccountUuid,
                                               Currency currency, long amount, UUID transactionUuid,
                                               String referenceType, String referenceId) {
        if (amount <= 0) throw new IllegalArgumentException("Transfer amount must be positive");
        if (!currency.transferable()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Currency " + currency.systemCode() + " is not transferable"));
        }

        return remove(fromAccountType, fromAccountUuid, currency, amount, LogScope.SEND, transactionUuid, referenceType, referenceId)
                .thenCompose(newSenderBalance -> add(toAccountType, toAccountUuid, currency, amount, LogScope.RECEIVE, transactionUuid, referenceType, referenceId)
                        .thenApply(newReceiverBalance -> true));
    }

    /**
     * Writes the new wallet amount, then the log row for {@code logAmount}.
     *
     * @return The wallet's amount as stored.
     */
    private CompletableFuture<Long> writeAndLog(String accountType, UUID accountUuid, Currency currency,
                                                long newBalance, long logAmount, LogScope scope,
                                                UUID transactionUuid, String referenceType, String referenceId) {
        return this.database.setWalletBalance(accountType, accountUuid, currency.id(), newBalance)
                .thenCompose(updatedWallet -> this.database.insertLog(accountType, accountUuid, scope, currency.id(),
                                logAmount, transactionUuid, referenceType, referenceId)
                        .thenApply(v -> updatedWallet.amount()));
    }
}
