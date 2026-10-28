package io.github.mcclauneck.mceconomy.common;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.models.Account;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.api.models.Wallet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the currency configuration parsing, the configuration-to-database
 * reconciliation, and the non-transferable currency guard.
 */
class MCEconomyProviderCurrencyTest {

    /**
     * In-memory {@link IEconomyDatabase} stub recording currency writes and
     * serving wallets from a map.
     */
    private static class StubDatabase implements IEconomyDatabase {

        final Map<String, Currency> currencies = new HashMap<>();
        final List<String> createCurrencyCalls = new ArrayList<>();
        final Map<String, Long> balances = new HashMap<>();
        int nextId = 1;

        @Override
        public CompletableFuture<Void> initialize() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> close() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Account> createAccount(String accountType, UUID accountUuid) {
            return CompletableFuture.completedFuture(
                    new Account(accountType, accountUuid, Instant.now(), Instant.now()));
        }

        @Override
        public CompletableFuture<Optional<Account>> getAccount(String accountType, UUID accountUuid) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Currency> createCurrency(String systemCode, String displayName, boolean transferable) {
            createCurrencyCalls.add(systemCode);
            Currency existing = currencies.get(systemCode);
            int id = existing != null ? existing.id() : nextId++;
            Currency saved = new Currency(id, systemCode, displayName, transferable, Instant.now(), Instant.now());
            currencies.put(systemCode, saved);
            return CompletableFuture.completedFuture(saved);
        }

        @Override
        public CompletableFuture<List<Currency>> getAllCurrencies() {
            return CompletableFuture.completedFuture(new ArrayList<>(currencies.values()));
        }

        @Override
        public CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId) {
            long amount = balances.getOrDefault(accountType + accountUuid + currencyId, 0L);
            return CompletableFuture.completedFuture(
                    new Wallet(accountType, accountUuid, currencyId, amount, Instant.now(), Instant.now()));
        }

        @Override
        public CompletableFuture<Wallet> setWalletBalance(String accountType, UUID accountUuid, int currencyId, long newAmount) {
            balances.put(accountType + accountUuid + currencyId, newAmount);
            return getWallet(accountType, accountUuid, currencyId);
        }

        @Override
        public CompletableFuture<Void> insertLog(String accountType, UUID accountUuid, LogScope scope, int currencyId,
                                                 long amount, UUID transactionUuid, String referenceType, String referenceId) {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    @DisplayName("parseCurrencyList reads the transferable flag and defaults it to true")
    void testParseCurrencyList() {
        Map<String, CurrencyDefinition> parsed = MCEconomyProvider.parseCurrencyList(Arrays.asList(
                "coin,Coin,true",
                "gold,Gold,false",
                "point,Point,FALSE",
                "copper,Copper",
                "bare",
                " , ,false",
                null));

        assertEquals(5, parsed.size());
        assertTrue(parsed.get("coin").transferable());
        assertFalse(parsed.get("gold").transferable());
        assertFalse(parsed.get("point").transferable(), "flag parsing is case-insensitive");
        assertTrue(parsed.get("copper").transferable(), "missing flag defaults to true");
        assertEquals("bare", parsed.get("bare").displayName(), "missing display name falls back to the code");
        assertTrue(parsed.get("bare").transferable());
    }

    @Test
    @DisplayName("built-in defaults keep gold, point, and token non-transferable")
    void testDefaultCurrencies() {
        assertTrue(MCEconomyProvider.DEFAULT_CURRENCIES.get("coin").transferable());
        assertTrue(MCEconomyProvider.DEFAULT_CURRENCIES.get("copper").transferable());
        assertTrue(MCEconomyProvider.DEFAULT_CURRENCIES.get("silver").transferable());
        assertFalse(MCEconomyProvider.DEFAULT_CURRENCIES.get("gold").transferable());
        assertFalse(MCEconomyProvider.DEFAULT_CURRENCIES.get("point").transferable());
        assertFalse(MCEconomyProvider.DEFAULT_CURRENCIES.get("token").transferable());
    }

    @Test
    @DisplayName("ensureDefaultCurrencies creates missing currencies and syncs changed flags")
    void testEnsureDefaultCurrencies() {
        StubDatabase database = new StubDatabase();
        // "gold" already exists but is transferable in the database, while the
        // configuration now locks it; "coin" matches; "token" is missing.
        database.currencies.put("gold", new Currency(1, "gold", "Gold", true, Instant.now(), Instant.now()));
        database.currencies.put("coin", new Currency(2, "coin", "Coin", true, Instant.now(), Instant.now()));
        database.nextId = 3;

        Map<String, CurrencyDefinition> managed = MCEconomyProvider.parseCurrencyList(Arrays.asList(
                "coin,Coin,true", "gold,Gold,false", "token,Token,false"));
        MCEconomyProvider provider = new MCEconomyProvider(database, managed);

        provider.loadCurrencies().join();
        provider.ensureDefaultCurrencies().join();

        assertEquals(Arrays.asList("gold", "token"), database.createCurrencyCalls,
                "only the out-of-date and missing currencies are written");
        assertFalse(provider.getCurrency("gold").transferable(), "changed flag is applied and re-cached");
        assertFalse(provider.getCurrency("token").transferable());

        // A second run is a no-op.
        database.createCurrencyCalls.clear();
        provider.ensureDefaultCurrencies().join();
        assertTrue(database.createCurrencyCalls.isEmpty());
    }

    @Test
    void testCurrenciesInDisplayOrder() {
        StubDatabase database = new StubDatabase();
        database.currencies.put("zinc", new Currency(1, "zinc", "Zinc", true, Instant.now(), Instant.now()));
        database.currencies.put("gold", new Currency(2, "gold", "Gold", true, Instant.now(), Instant.now()));
        database.currencies.put("amber", new Currency(3, "amber", "Amber", true, Instant.now(), Instant.now()));
        database.currencies.put("coin", new Currency(4, "coin", "Coin", true, Instant.now(), Instant.now()));

        MCEconomyProvider provider = new MCEconomyProvider(database,
                MCEconomyProvider.parseCurrencyList(Arrays.asList("gold,Gold", "coin,Coin")));
        provider.loadCurrencies().join();

        assertEquals(Arrays.asList("gold", "coin", "amber", "zinc"),
                provider.getCurrenciesInDisplayOrder().stream().map(Currency::systemCode).toList(),
                "configured currencies first in configuration order, then the rest by code");
    }

    @Test
    @DisplayName("transferBalance rejects non-transferable currencies without touching balances")
    void testTransferGuard() {
        StubDatabase database = new StubDatabase();
        MCEconomyProvider provider = new MCEconomyProvider(database);
        Currency gold = new Currency(1, "gold", "Gold", false, Instant.now(), Instant.now());

        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.setWalletBalance("identity", sender, gold.id(), 500L).join();

        CompletionException thrown = assertThrows(CompletionException.class, () ->
                provider.transferBalance("identity", sender, "identity", receiver,
                        gold, 100, UUID.randomUUID(), "test", "transfer").join());
        assertInstanceOf(IllegalStateException.class, thrown.getCause());

        assertEquals(500L, provider.getBalance("identity", sender, gold).join(), "sender balance is untouched");
        assertEquals(0L, provider.getBalance("identity", receiver, gold).join(), "receiver balance is untouched");
    }

    @Test
    @DisplayName("transferBalance still moves transferable currencies")
    void testTransferAllowed() {
        StubDatabase database = new StubDatabase();
        MCEconomyProvider provider = new MCEconomyProvider(database);
        Currency coin = new Currency(1, "coin", "Coin", true, Instant.now(), Instant.now());

        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        database.setWalletBalance("identity", sender, coin.id(), 500L).join();

        assertTrue(provider.transferBalance("identity", sender, "identity", receiver,
                coin, 100, UUID.randomUUID(), "test", "transfer").join());
        assertEquals(400L, provider.getBalance("identity", sender, coin).join());
        assertEquals(100L, provider.getBalance("identity", receiver, coin).join());
    }
}
