package io.github.mcclauneck.mceconomy.common.database.mongodb;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.models.Account;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.api.models.Wallet;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MongoDB implementation of the Economy Database.
 * <p>
 * Account documents live in the shared {@value #ACCOUNTS} collection, the
 * MongoDB counterpart of the shared SQL {@code accounts} table: one document
 * per account, keyed {@code {type}_{uuid}}, with {@code account_type},
 * {@code account_uuid}, {@code created_at} and {@code updated_at}. MongoDB has
 * no foreign keys, so every account-keyed write upserts the account first.
 */
public class MCEconomyMongoDB implements IEconomyDatabase {

    /** The shared collection every account document lives in. */
    static final String ACCOUNTS = "accounts";

    private final MongoClient mongoClient;
    private final MongoDatabase database;

    /**
     * Constructs the MongoDB connection.
     *
     * @param uri The MongoDB connection URI.
     * @param dbName The database name.
     */
    public MCEconomyMongoDB(String uri, String dbName) {
        this.mongoClient = MongoClients.create(uri);
        this.database = mongoClient.getDatabase(dbName);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        // MongoDB creates collections lazily, so no rigid setup is necessary.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(mongoClient::close);
    }

    @Override
    public CompletableFuture<Account> createAccount(String accountType, UUID accountUuid) {
        return CompletableFuture.supplyAsync(() -> readAccount(ensureAccount(accountType, accountUuid)));
    }

    /**
     * Upserts the shared account document, so every account-keyed write that
     * follows has its account.
     *
     * @return The account document after the upsert.
     */
    private Document ensureAccount(String accountType, UUID accountUuid) {
        MongoCollection<Document> accounts = database.getCollection(ACCOUNTS);
        String id = accountType + "_" + accountUuid.toString();
        return accounts.findOneAndUpdate(
            Filters.eq("_id", id),
            Updates.combine(
                Updates.setOnInsert("account_type", accountType),
                Updates.setOnInsert("account_uuid", accountUuid.toString()),
                Updates.setOnInsert("created_at", Instant.now().toString()),
                Updates.set("updated_at", Instant.now().toString())
            ),
            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
    }

    /** Maps a shared account document to an {@link Account}. */
    private static Account readAccount(Document doc) {
        return new Account(
            doc.getString("account_type"),
            UUID.fromString(doc.getString("account_uuid")),
            Instant.parse(doc.getString("created_at")),
            Instant.parse(doc.getString("updated_at"))
        );
    }

    @Override
    public CompletableFuture<Optional<Account>> getAccount(String accountType, UUID accountUuid) {
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> accounts = database.getCollection(ACCOUNTS);
            String id = accountType + "_" + accountUuid.toString();
            Document doc = accounts.find(Filters.eq("_id", id)).first();
            return Optional.ofNullable(doc).map(MCEconomyMongoDB::readAccount);
        });
    }

    @Override
    public CompletableFuture<Currency> createCurrency(String systemCode, String displayName, boolean transferable) {
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> currencies = database.getCollection("economy_currency");
            Document existing = currencies.findOneAndUpdate(
                Filters.eq("system_code", systemCode),
                Updates.combine(
                    Updates.set("display_name", displayName),
                    Updates.set("is_transferable", transferable),
                    Updates.set("updated_at", Instant.now().toString())
                ),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );

            if (existing != null) {
                return readCurrency(existing);
            }

            // Generate an auto-incrementing integer ID for compatibility with the API
            MongoCollection<Document> counters = database.getCollection("counters");
            Document counter = counters.findOneAndUpdate(
                Filters.eq("_id", "currency_id"),
                Updates.inc("seq", 1),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
            );
            int newId = counter.getInteger("seq");

            Document doc = new Document("_id", newId)
                .append("system_code", systemCode)
                .append("display_name", displayName)
                .append("is_transferable", transferable)
                .append("created_at", Instant.now().toString())
                .append("updated_at", Instant.now().toString());

            currencies.insertOne(doc);
            return readCurrency(doc);
        });
    }

    @Override
    public CompletableFuture<List<Currency>> getAllCurrencies() {
        return CompletableFuture.supplyAsync(() -> {
            List<Currency> list = new ArrayList<>();
            MongoCollection<Document> currencies = database.getCollection("economy_currency");
            for (Document doc : currencies.find()) {
                list.add(readCurrency(doc));
            }
            return list;
        });
    }

    /**
     * Maps a {@code economy_currency} document to a {@link Currency}. Documents
     * written before the transferable flag existed default to transferable.
     */
    private static Currency readCurrency(Document doc) {
        Boolean transferable = doc.getBoolean("is_transferable");
        return new Currency(
            doc.getInteger("_id"),
            doc.getString("system_code"),
            doc.getString("display_name"),
            transferable == null || transferable,
            Instant.parse(doc.getString("created_at")),
            Instant.parse(doc.getString("updated_at"))
        );
    }

    @Override
    public CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            ensureAccount(accountType, accountUuid);

            MongoCollection<Document> wallets = database.getCollection("economy_wallet");
            String id = accountType + "_" + accountUuid.toString() + "_" + currencyId;
            
            Document doc = wallets.findOneAndUpdate(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.setOnInsert("account_type", accountType),
                    Updates.setOnInsert("account_uuid", accountUuid.toString()),
                    Updates.setOnInsert("currency_id", currencyId),
                    Updates.setOnInsert("amount", 0L),
                    Updates.setOnInsert("created_at", Instant.now().toString()),
                    Updates.set("updated_at", Instant.now().toString())
                ),
                new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
            );
            
            return new Wallet(
                doc.getString("account_type"), 
                UUID.fromString(doc.getString("account_uuid")), 
                doc.getInteger("currency_id"), 
                doc.getLong("amount"), 
                Instant.parse(doc.getString("created_at")), 
                Instant.parse(doc.getString("updated_at"))
            );
        });
    }

    @Override
    public CompletableFuture<Wallet> setWalletBalance(String accountType, UUID accountUuid, int currencyId, long newAmount) {
        return CompletableFuture.supplyAsync(() -> {
            MongoCollection<Document> wallets = database.getCollection("economy_wallet");
            String id = accountType + "_" + accountUuid.toString() + "_" + currencyId;
            
            wallets.updateOne(
                Filters.eq("_id", id),
                Updates.combine(
                    Updates.set("amount", newAmount),
                    Updates.set("updated_at", Instant.now().toString())
                )
            );
            
            return getWallet(accountType, accountUuid, currencyId).join();
        });
    }

    @Override
    public CompletableFuture<Void> insertLog(String accountType, UUID accountUuid, LogScope scope, int currencyId, long amount, UUID transactionUuid, String referenceType, String referenceId) {
        return CompletableFuture.runAsync(() -> {
            ensureAccount(accountType, accountUuid);
            if (transactionUuid != null) {
                MongoCollection<Document> txs = database.getCollection("economy_transactions");
                txs.updateOne(
                    Filters.eq("_id", transactionUuid.toString()),
                    Updates.combine(
                        Updates.setOnInsert("account_type", accountType),
                        Updates.setOnInsert("account_uuid", accountUuid.toString()),
                        Updates.setOnInsert("status", "completed"),
                        Updates.setOnInsert("created_at", Instant.now().toString())
                    ),
                    new com.mongodb.client.model.UpdateOptions().upsert(true)
                );
            }

            MongoCollection<Document> logs = database.getCollection("economy_logs");
            Document doc = new Document("account_type", accountType)
                .append("account_uuid", accountUuid.toString())
                .append("scope", scope.toString())
                .append("currency_id", currencyId)
                .append("amount", amount)
                .append("transaction_uuid", transactionUuid != null ? transactionUuid.toString() : null)
                .append("reference_type", referenceType)
                .append("reference_id", referenceId)
                .append("created_at", Instant.now().toString());
            
            logs.insertOne(doc);
        });
    }
}
