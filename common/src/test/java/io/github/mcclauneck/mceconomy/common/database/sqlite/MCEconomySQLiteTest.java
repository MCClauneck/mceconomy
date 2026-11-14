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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
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
        assertTrue(this.database.getAccount("player", account).join().isEmpty());

        Currency coin = this.database.createCurrency("coin", "Coin", true).join();
        Wallet wallet = this.database.getWallet("player", account, coin.id()).join();
        assertEquals(0L, wallet.amount());
        assertEquals(account, wallet.accountUuid());
        assertTrue(this.database.getAccount("player", account).join().isPresent());

        Wallet set = this.database.setWalletBalance("player", account, coin.id(), 250L).join();
        assertEquals(250L, set.amount());
    }

    @Test
    void writesLogsWithAndWithoutTransaction() {
        UUID account = UUID.randomUUID();
        Currency coin = this.database.createCurrency("coin", "Coin", true).join();
        this.database.createAccount("player", account).join();

        UUID transaction = UUID.randomUUID();
        this.database.insertLog("player", account, LogScope.RECEIVE, coin.id(), 10L, transaction, "test", "a").join();
        this.database.insertLog("player", account, LogScope.SEND, coin.id(), -10L, transaction, "test", "b").join();
        this.database.insertLog("player", account, LogScope.SPEND, coin.id(), -1L, null, "test", "c").join();
    }

    @Test
    void createsTheSharedAccountsTableAndNoPrivateOne() throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Connection conn = openFile(); Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        assertTrue(tables.contains("accounts"), tables.toString());
        assertFalse(tables.contains("economy_account"), tables.toString());
        assertFalse(tables.contains("account_property"), tables.toString());
    }

    @Test
    void everyAccountKeyedTableReferencesAccounts() throws SQLException {
        for (String table : new String[] {"economy_wallet", "economy_transactions", "economy_logs"}) {
            Set<String> referenced = new HashSet<>();
            try (Connection conn = openFile(); Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(" + table + ")")) {
                while (rs.next()) {
                    referenced.add(rs.getString("table") + "." + rs.getString("from") + "->" + rs.getString("to"));
                }
            }
            assertTrue(referenced.contains("accounts.account_type->account_type"), table + " " + referenced);
            assertTrue(referenced.contains("accounts.account_uuid->account_uuid"), table + " " + referenced);
        }
    }

    @Test
    void logsAnAccountWrittenForTheFirstTime() {
        UUID account = UUID.randomUUID();
        Currency coin = this.database.createCurrency("coin", "Coin", true).join();

        this.database.insertLog("player", account, LogScope.RECEIVE, coin.id(), 5L, UUID.randomUUID(), "test", "first").join();

        assertTrue(this.database.getAccount("player", account).join().isPresent(), "the account row is inserted first");
    }

    @Test
    void reusesAnAccountsTableAnotherPluginCreated() {
        UUID account = UUID.randomUUID();
        this.database.createAccount("player", account).join();
        this.database.close().join();

        // A second start, as when MCMultiverse or MCRealm created the table first.
        this.database = new MCEconomySQLite(new File(this.folder.toFile(), "economy.db"));
        this.database.initialize().join();

        assertTrue(this.database.getAccount("player", account).join().isPresent());
    }

    private Connection openFile() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + new File(this.folder.toFile(), "economy.db").getAbsolutePath());
    }
}
