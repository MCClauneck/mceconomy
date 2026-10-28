package io.github.mcclauneck.mceconomy.common.database.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mcclauneck.mceconomy.common.database.sql.AbstractSqlEconomyDatabase;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * MySQL implementation of the Economy Database: the MySQL schema and the
 * {@code INSERT IGNORE} / {@code ON DUPLICATE KEY} statements. The queries
 * themselves are shared through {@link AbstractSqlEconomyDatabase}.
 */
public class MCEconomyMySQL extends AbstractSqlEconomyDatabase {

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
        super(createDataSource(host, port, database, username, password), "Failed to initialize MySQL schema");
        initialize();
    }

    private static HikariDataSource createDataSource(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    @Override
    protected void createSchema(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_account (account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid))");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_currency (id INT AUTO_INCREMENT PRIMARY KEY, system_code VARCHAR(50) NOT NULL UNIQUE, display_name VARCHAR(100) NOT NULL, is_transferable BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)");
        // Migrate databases created before the column existed. MySQL has no
        // ADD COLUMN IF NOT EXISTS, so the duplicate-column error is swallowed.
        addColumnIfMissing(stmt, "ALTER TABLE economy_currency ADD COLUMN is_transferable BOOLEAN NOT NULL DEFAULT TRUE");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_wallet (account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, currency_id), FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_transactions (transaction_uuid VARCHAR(36) PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'completed', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE)");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid VARCHAR(36) NOT NULL, scope ENUM('send', 'receive', 'spend', 'donate') NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL, transaction_uuid VARCHAR(36), reference_type VARCHAR(50), reference_id VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE RESTRICT, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT, FOREIGN KEY (transaction_uuid) REFERENCES economy_transactions (transaction_uuid) ON DELETE SET NULL)");
    }

    @Override
    protected String insertAccountIfAbsentSql() {
        return "INSERT IGNORE INTO economy_account (account_type, account_uuid) VALUES (?, ?)";
    }

    @Override
    protected String insertWalletIfAbsentSql() {
        return "INSERT IGNORE INTO economy_wallet (account_type, account_uuid, currency_id, amount) VALUES (?, ?, ?, 0)";
    }

    @Override
    protected String insertTransactionIfAbsentSql() {
        return "INSERT IGNORE INTO economy_transactions (transaction_uuid, account_type, account_uuid, status) VALUES (?, ?, ?, 'completed')";
    }

    @Override
    protected String updateWalletSql() {
        return "UPDATE economy_wallet SET amount = ? WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
    }

    @Override
    protected String upsertCurrencySql() {
        return "INSERT INTO economy_currency (system_code, display_name, is_transferable) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), is_transferable = VALUES(is_transferable)";
    }
}
