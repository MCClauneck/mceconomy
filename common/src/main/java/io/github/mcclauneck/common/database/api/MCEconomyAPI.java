package io.github.mcclauneck.common.database.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.mcclauneck.api.database.IEconomyDatabase;
import io.github.mcclauneck.api.models.Account;
import io.github.mcclauneck.api.models.Currency;
import io.github.mcclauneck.api.models.LogScope;
import io.github.mcclauneck.api.models.Wallet;
import io.github.mcclauneck.common.http.MCEconomyHttpClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Remote implementation of the Economy Database backed by the MCEngine
 * server-expressjs central API.
 * <p>
 * Instead of talking to a local relational store, this backend forwards every
 * operation to the {@code POST /api/economy} endpoint over HTTP. It implements
 * {@link IEconomyDatabase} so it can be used interchangeably with the local
 * database backends through {@link io.github.mcclauneck.common.MCEconomyProvider}.
 */
public class MCEconomyAPI implements IEconomyDatabase {

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiToken;

    /**
     * Constructs the remote API backend.
     *
     * @param baseUrl  The base URL of the central server (e.g. {@code https://api.example.com}).
     * @param apiToken The Bearer token used to authenticate against the server.
     */
    public MCEconomyAPI(String baseUrl, String apiToken) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.endpoint = URI.create(normalized + "/api/economy");
        this.apiToken = apiToken;
        // Reuse the plugin-wide shared client so the core backend and every
        // extension share a single connection pool.
        this.httpClient = MCEconomyHttpClient.shared();
    }

    /**
     * Sends a JSON payload to the economy endpoint and returns the parsed response body.
     *
     * @param body The request payload describing the action and its arguments.
     * @return A CompletableFuture containing the parsed JSON response object.
     */
    private CompletableFuture<JsonObject> post(JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (response.statusCode() >= 400) {
                        String error = json.has("error") ? json.get("error").getAsString()
                                : "HTTP " + response.statusCode();
                        throw new RuntimeException("Economy API request failed: " + error);
                    }
                    return json;
                });
    }

    /**
     * Parses an {@link Account} from its JSON representation.
     */
    private static Account parseAccount(JsonObject json) {
        return new Account(
                json.get("account_type").getAsString(),
                UUID.fromString(json.get("account_uuid").getAsString()),
                Instant.parse(json.get("created_at").getAsString()),
                Instant.parse(json.get("updated_at").getAsString())
        );
    }

    /**
     * Parses a {@link Currency} from its JSON representation. Responses from
     * servers that predate the transferable flag default to transferable.
     */
    private static Currency parseCurrency(JsonObject json) {
        JsonElement transferable = json.get("is_transferable");
        return new Currency(
                json.get("id").getAsInt(),
                json.get("system_code").getAsString(),
                json.get("display_name").getAsString(),
                transferable == null || transferable.isJsonNull() || transferable.getAsBoolean(),
                Instant.parse(json.get("created_at").getAsString()),
                Instant.parse(json.get("updated_at").getAsString())
        );
    }

    /**
     * Parses a {@link Wallet} from its JSON representation.
     */
    private static Wallet parseWallet(JsonObject json) {
        return new Wallet(
                json.get("account_type").getAsString(),
                UUID.fromString(json.get("account_uuid").getAsString()),
                json.get("currency_id").getAsInt(),
                Long.parseLong(json.get("amount").getAsString()),
                Instant.parse(json.get("created_at").getAsString()),
                Instant.parse(json.get("updated_at").getAsString())
        );
    }

    @Override
    public CompletableFuture<Void> initialize() {
        // The remote server provisions its own schema on startup.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> close() {
        // Nothing to release for a stateless HTTP client.
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Account> createAccount(String accountType, UUID accountUuid) {
        JsonObject body = new JsonObject();
        body.addProperty("action", "create_account");
        body.addProperty("account_type", accountType);
        body.addProperty("account_uuid", accountUuid.toString());
        return post(body).thenApply(json -> parseAccount(json.getAsJsonObject("account")));
    }

    @Override
    public CompletableFuture<Optional<Account>> getAccount(String accountType, UUID accountUuid) {
        JsonObject body = new JsonObject();
        body.addProperty("action", "get_account");
        body.addProperty("account_type", accountType);
        body.addProperty("account_uuid", accountUuid.toString());
        return post(body).thenApply(json -> {
            JsonElement account = json.get("account");
            if (account == null || account.isJsonNull()) {
                return Optional.empty();
            }
            return Optional.of(parseAccount(account.getAsJsonObject()));
        });
    }

    @Override
    public CompletableFuture<Currency> createCurrency(String systemCode, String displayName, boolean transferable) {
        JsonObject body = new JsonObject();
        body.addProperty("action", "create_currency");
        body.addProperty("system_code", systemCode);
        body.addProperty("display_name", displayName);
        body.addProperty("is_transferable", transferable);
        return post(body).thenApply(json -> parseCurrency(json.getAsJsonObject("currency")));
    }

    @Override
    public CompletableFuture<List<Currency>> getAllCurrencies() {
        JsonObject body = new JsonObject();
        body.addProperty("action", "get_currencies");
        return post(body).thenApply(json -> {
            List<Currency> currencies = new ArrayList<>();
            JsonArray array = json.getAsJsonArray("currencies");
            for (JsonElement element : array) {
                currencies.add(parseCurrency(element.getAsJsonObject()));
            }
            return currencies;
        });
    }

    @Override
    public CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId) {
        JsonObject body = new JsonObject();
        body.addProperty("action", "get_wallet");
        body.addProperty("account_type", accountType);
        body.addProperty("account_uuid", accountUuid.toString());
        body.addProperty("currency_id", currencyId);
        return post(body).thenApply(json -> parseWallet(json.getAsJsonObject("wallet")));
    }

    @Override
    public CompletableFuture<Wallet> setWalletBalance(String accountType, UUID accountUuid, int currencyId, long newAmount) {
        JsonObject body = new JsonObject();
        body.addProperty("action", "set_wallet");
        body.addProperty("account_type", accountType);
        body.addProperty("account_uuid", accountUuid.toString());
        body.addProperty("currency_id", currencyId);
        body.addProperty("amount", Long.toString(newAmount));
        return post(body).thenApply(json -> parseWallet(json.getAsJsonObject("wallet")));
    }

    @Override
    public CompletableFuture<Void> insertLog(String accountType, UUID accountUuid, LogScope scope, int currencyId, long amount, UUID transactionUuid, String referenceType, String referenceId) {
        JsonObject body = new JsonObject();
        body.addProperty("action", "insert_log");
        body.addProperty("account_type", accountType);
        body.addProperty("account_uuid", accountUuid.toString());
        body.addProperty("scope", scope.toString());
        body.addProperty("currency_id", currencyId);
        body.addProperty("amount", Long.toString(amount));
        body.addProperty("transaction_uuid", transactionUuid != null ? transactionUuid.toString() : null);
        body.addProperty("reference_type", referenceType);
        body.addProperty("reference_id", referenceId);
        return post(body).thenApply(json -> null);
    }
}
