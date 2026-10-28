package io.github.mcclauneck.mceconomy.common.database.sqlite;

import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.api.models.Wallet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips every query of the shared SQL plumbing against a real SQLite
 * file, so the dialect hooks (integer booleans, text timestamps and UUIDs,
 * {@code INSERT OR IGNORE}) stay wired to the right statements.
 */
class MCEconomySQLiteTest {

    @TempDir
    Path folder;

    private MCEconomySQLite database;

    @BeforeEach
    void open() {
        this.database = new MCEconomySQLite(new File(this.folder.toFile(), "economy.db"));
        this.database.initialize().join();
    }

    @AfterEach
    void close() {
        this.database.close().join();
    }

    @Test
    void upsertsCurrencyAndReadsItBack() {
        Currency created = this.database.createCurrency("gold", "Gold", true).join();
        assertEquals("gold", created.systemCode());
        assertTrue(created.transferable());

        Currency updated = this.database.createCurrency("gold", "Gold Coin", false).join();
        assertEquals(created.id(), updated.id(), "the upsert keeps the row");
        assertEquals("Gold Coin", updated.displayName());
        assertFalse(updated.transferable());
        assertEquals(1, this.database.getAllCurrencies().join().size());
    }

    @Test
    void createsAccountAndWalletOnFirstRead() {
        UUID account = UUID.randomUUID();
        assertTrue(this.database.getAccount("identity", account).join().isEmpty());

        Currency coin = this.database.createCurrency("coin", "Coin", true).join();
        Wallet wallet = this.database.getWallet("identity", account, coin.id()).join();
        assertEquals(0L, wallet.amount());
        assertEquals(account, wallet.accountUuid());
        assertTrue(this.database.getAccount("identity", account).join().isPresent());

        Wallet set = this.database.setWalletBalance("identity", account, coin.id(), 250L).join();
        assertEquals(250L, set.amount());
    }

    @Test
    void writesLogsWithAndWithoutTransaction() {
        UUID account = UUID.randomUUID();
        Currency coin = this.database.createCurrency("coin", "Coin", true).join();
        this.database.createAccount("identity", account).join();

        UUID transaction = UUID.randomUUID();
        this.database.insertLog("identity", account, LogScope.RECEIVE, coin.id(), 10L, transaction, "test", "a").join();
        this.database.insertLog("identity", account, LogScope.SEND, coin.id(), -10L, transaction, "test", "b").join();
        this.database.insertLog("identity", account, LogScope.SPEND, coin.id(), -1L, null, "test", "c").join();
    }
}
