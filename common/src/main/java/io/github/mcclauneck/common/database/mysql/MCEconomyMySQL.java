package io.github.mcclauneck.common.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mcclauneck.api.database.IEconomyDatabase;
import io.github.mcclauneck.api.models.Account;
import io.github.mcclauneck.api.models.Currency;
import io.github.mcclauneck.api.models.LogScope;
import io.github.mcclauneck.api.models.Wallet;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MySQL implementation of the Economy Database.
 */
public class MCEconomyMySQL implements IEconomyDatabase {

    private final HikariDataSource dataSource;

    /**
     * Constructs the MySQL database connection.
     *
     * @param host     The database host.
     * @param port     The database port.
     * @param database The database name.
     * @param username The database user username.
     * @param password The database user password.
     */
    public MCEconomyMySQL(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(config);
        initialize();
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_account (account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid))");
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_currency (id INT AUTO_INCREMENT PRIMARY KEY, system_code VARCHAR(50) NOT NULL UNIQUE, display_name VARCHAR(100) NOT NULL, is_transferable BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
                addTransferableColumnIfMissing(stmt);
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_wallet (account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, currency_id), FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_transactions (transaction_uuid VARCHAR(36) PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'completed', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE)");
                stmt.execute("CREATE TABLE IF NOT EXISTS economy_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, scope ENUM('send', 'receive', 'spend', 'donate') NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL, transaction_uuid VARCHAR(36), reference_type VARCHAR(50), reference_id VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE RESTRICT, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT, FOREIGN KEY (transaction_uuid) REFERENCES economy_transactions (transaction_uuid) ON DELETE SET NULL)");
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize MySQL schema", e);
            }
        });
    }

    /**
     * Adds the {@code is_transferable} column to databases created before the
     * column existed. MySQL has no {@code ADD COLUMN IF NOT EXISTS}, so the
     * duplicate-column error on already-migrated schemas is swallowed.
     */
    private void addTransferableColumnIfMissing(Statement stmt) {
        try {
            stmt.execute("ALTER TABLE economy_currency ADD COLUMN is_transferable BOOLEAN NOT NULL DEFAULT TRUE");
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
            String sql = "INSERT IGNORE INTO economy_account (account_type, account_uuid) VALUES (?, ?)";
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
                        return Optional.of(new Account(rs.getString("account_type"), UUID.fromString(rs.getString("account_uuid")), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
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
                    + "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), is_transferable = VALUES(is_transferable)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, systemCode);
                ps.setString(2, displayName);
                ps.setBoolean(3, transferable);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            String fetchSql = "SELECT * FROM economy_currency WHERE system_code = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(fetchSql)) {
                ps.setString(1, systemCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return readCurrency(rs);
                    }
                }
                throw new SQLException("Failed to fetch currency.");
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
                rs.getBoolean("is_transferable"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    @Override
    public CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            createAccount(accountType, accountUuid).join();
            
            String sql = "INSERT IGNORE INTO economy_wallet (account_type, account_uuid, currency_id, amount) VALUES (?, ?, ?, 0)";
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
                        return new Wallet(rs.getString("account_type"), UUID.fromString(rs.getString("account_uuid")), rs.getInt("currency_id"), rs.getLong("amount"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
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
            String sql = "UPDATE economy_wallet SET amount = ? WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
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
                String txSql = "INSERT IGNORE INTO economy_transactions (transaction_uuid, account_type, account_uuid, status) VALUES (?, ?, ?, 'completed')";
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
