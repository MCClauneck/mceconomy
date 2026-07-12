package io.github.mcclauneck.common.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mcclauneck.api.database.IEconomyDatabase;
import io.github.mcclauneck.api.models.Account;
import io.github.mcclauneck.api.models.Currency;
import io.github.mcclauneck.api.models.LogScope;
import io.github.mcclauneck.api.models.Wallet;

import java.io.File;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQLite implementation of the Economy Database.
 */
public class MCEconomySQLite implements IEconomyDatabase {

    private final HikariDataSource dataSource;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructs the SQLite database connection.
     *
     * @param databaseFile The file to store the SQLite database.
     */
    public MCEconomySQLite(File databaseFile) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite handles concurrent writes poorly, so limit pool size
        this.dataSource = new HikariDataSource(config);
        initialize();
    }

    private Timestamp parseTimestamp(String dateStr) {
        if (dateStr == null) return new Timestamp(System.currentTimeMillis());
        try {
            return new Timestamp(dateFormat.parse(dateStr).getTime());
        } catch (ParseException e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_account (account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid))");
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_currency (id INTEGER PRIMARY KEY AUTOINCREMENT, system_code TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, is_transferable INTEGER NOT NULL DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                addTransferableColumnIfMissing(stmt);
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_wallet (account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, currency_id INTEGER NOT NULL, amount INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, currency_id), FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_transactions (transaction_uuid TEXT PRIMARY KEY, account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'completed', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE)");
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, scope TEXT NOT NULL, currency_id INTEGER NOT NULL, amount INTEGER NOT NULL, transaction_uuid TEXT, reference_type TEXT, reference_id TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE RESTRICT, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT, FOREIGN KEY (transaction_uuid) REFERENCES economy_transactions (transaction_uuid) ON DELETE SET NULL)");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize SQLite schema", e);
            }
        });
    }

    /**
     * Adds the {@code is_transferable} column to databases created before the
     * column existed. SQLite has no {@code ADD COLUMN IF NOT EXISTS}, so the
     * duplicate-column error on already-migrated files is swallowed.
     */
    private void addTransferableColumnIfMissing(Statement stmt) {
        try {
            stmt.execute("ALTER TABLE economy_currency ADD COLUMN is_transferable INTEGER NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            // Column already exists.
        }
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(dataSource::close);
    }

    @Override
    public CompletableFuture<Account> createAccount(String accountType, UUID accountUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT OR IGNORE INTO economy_account (account_type, account_uuid) VALUES (?, ?)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType);
                ps.setString(2, accountUuid.toString());
                ps.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return getAccount(accountType, accountUuid).join().orElseThrow();
        });
    }

    @Override
    public CompletableFuture<Optional<Account>> getAccount(String accountType, UUID accountUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM economy_account WHERE account_type = ? AND account_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType);
                ps.setString(2, accountUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Account(rs.getString("account_type"), UUID.fromString(rs.getString("account_uuid")), parseTimestamp(rs.getString("created_at")).toInstant(), parseTimestamp(rs.getString("updated_at")).toInstant()));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Currency> createCurrency(String systemCode, String displayName, boolean transferable) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO economy_currency (system_code, display_name, is_transferable) VALUES (?, ?, ?) "
                    + "ON CONFLICT (system_code) DO UPDATE SET display_name = excluded.display_name, is_transferable = excluded.is_transferable, updated_at = CURRENT_TIMESTAMP";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, systemCode);
                ps.setString(2, displayName);
                ps.setInt(3, transferable ? 1 : 0);
                ps.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            String fetchSql = "SELECT * FROM economy_currency WHERE system_code = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement fetchPs = conn.prepareStatement(fetchSql)) {
                fetchPs.setString(1, systemCode);
                try (ResultSet rs = fetchPs.executeQuery()) {
                    if (rs.next()) {
                        return readCurrency(rs);
                    }
                }
                throw new SQLException("Failed to retrieve currency after insert.");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<List<Currency>> getAllCurrencies() {
        return CompletableFuture.supplyAsync(() -> {
            List<Currency> currencies = new ArrayList<>();
            String sql = "SELECT * FROM economy_currency";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    currencies.add(readCurrency(rs));
                }
                return currencies;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Maps the current {@code economy_currency} result-set row to a {@link Currency}.
     */
    private Currency readCurrency(ResultSet rs) throws SQLException {
        return new Currency(rs.getInt("id"), rs.getString("system_code"), rs.getString("display_name"),
                rs.getInt("is_transferable") != 0,
                parseTimestamp(rs.getString("created_at")).toInstant(), parseTimestamp(rs.getString("updated_at")).toInstant());
    }

    @Override
    public CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            createAccount(accountType, accountUuid).join();
            
            String sql = "INSERT OR IGNORE INTO economy_wallet (account_type, account_uuid, currency_id, amount) VALUES (?, ?, ?, 0)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType);
                ps.setString(2, accountUuid.toString());
                ps.setInt(3, currencyId);
                ps.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            String fetchSql = "SELECT * FROM economy_wallet WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(fetchSql)) {
                ps.setString(1, accountType);
                ps.setString(2, accountUuid.toString());
                ps.setInt(3, currencyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Wallet(rs.getString("account_type"), UUID.fromString(rs.getString("account_uuid")), rs.getInt("currency_id"), rs.getLong("amount"), parseTimestamp(rs.getString("created_at")).toInstant(), parseTimestamp(rs.getString("updated_at")).toInstant());
                    }
                    throw new SQLException("Wallet retrieval failed.");
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Wallet> setWalletBalance(String accountType, UUID accountUuid, int currencyId, long newAmount) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE economy_wallet SET amount = ?, updated_at = CURRENT_TIMESTAMP WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, newAmount);
                ps.setString(2, accountType);
                ps.setString(3, accountUuid.toString());
                ps.setInt(4, currencyId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return getWallet(accountType, accountUuid, currencyId).join();
        });
    }

    @Override
    public CompletableFuture<Void> insertLog(String accountType, UUID accountUuid, LogScope scope, int currencyId, long amount, UUID transactionUuid, String referenceType, String referenceId) {
        return CompletableFuture.runAsync(() -> {
            if (transactionUuid != null) {
                String txSql = "INSERT OR IGNORE INTO economy_transactions (transaction_uuid, account_type, account_uuid, status) VALUES (?, ?, ?, 'completed')";
                try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(txSql)) {
                    ps.setString(1, transactionUuid.toString());
                    ps.setString(2, accountType);
                    ps.setString(3, accountUuid.toString());
                    ps.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            String logSql = "INSERT INTO economy_logs (account_type, account_uuid, scope, currency_id, amount, transaction_uuid, reference_type, reference_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(logSql)) {
                ps.setString(1, accountType);
                ps.setString(2, accountUuid.toString());
                ps.setString(3, scope.toString());
                ps.setInt(4, currencyId);
                ps.setLong(5, amount);
                ps.setString(6, transactionUuid != null ? transactionUuid.toString() : null);
                ps.setString(7, referenceType);
                ps.setString(8, referenceId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
