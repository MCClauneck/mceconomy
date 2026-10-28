package io.github.mcclauneck.mceconomy.common.database.postgresql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.common.database.sql.AbstractSqlEconomyDatabase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * PostgreSQL implementation of the Economy Database: the PostgreSQL schema
 * (native {@code UUID} columns, the {@code economy_log_scope} enum, and the
 * archive tables), {@code ON CONFLICT} statements, and a currency upsert that
 * returns its row. The queries themselves are shared through
 * {@link AbstractSqlEconomyDatabase}.
 */
public class MCEconomyPostgreSQL extends AbstractSqlEconomyDatabase {

    /**
     * Constructs the PostgreSQL connection pool.
     *
     * @param host     The database host.
     * @param port     The database port.
     * @param database The database name.
     * @param username The database user username.
     * @param password The database user password.
     */
    public MCEconomyPostgreSQL(String host, int port, String database, String username, String password) {
        super(createDataSource(host, port, database, username, password), "Failed to initialize PostgreSQL schema for Economy");
        initialize();
    }

    private static HikariDataSource createDataSource(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        return new HikariDataSource(config);
    }

    @Override
    protected void createSchema(Statement stmt) throws SQLException {
        // Create ENUM
        stmt.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'economy_log_scope') THEN CREATE TYPE economy_log_scope AS ENUM ('send', 'receive', 'spend', 'donate'); END IF; END$$;");

        // Main Tables
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_account (account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid))");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_currency (id SERIAL PRIMARY KEY, system_code VARCHAR(50) NOT NULL UNIQUE, display_name VARCHAR(100) NOT NULL, is_transferable BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        // Migrate databases created before the column existed.
        stmt.execute("ALTER TABLE economy_currency ADD COLUMN IF NOT EXISTS is_transferable BOOLEAN NOT NULL DEFAULT TRUE");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_wallet (account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, currency_id), FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_transactions (transaction_uuid UUID PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'completed', created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE CASCADE)");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_logs (id BIGSERIAL PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, scope economy_log_scope NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL, transaction_uuid UUID, reference_type VARCHAR(50), reference_id VARCHAR(100), created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES economy_account (account_type, account_uuid) ON DELETE RESTRICT, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT, FOREIGN KEY (transaction_uuid) REFERENCES economy_transactions (transaction_uuid) ON DELETE SET NULL)");

        // Archive Tables
        stmt.execute("CREATE TABLE IF NOT EXISTS archive_economy_account (account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, archived_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid))");
        stmt.execute("CREATE TABLE IF NOT EXISTS archive_economy_currency (id INT PRIMARY KEY, system_code VARCHAR(50) NOT NULL, display_name VARCHAR(100) NOT NULL, is_transferable BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, archived_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("ALTER TABLE archive_economy_currency ADD COLUMN IF NOT EXISTS is_transferable BOOLEAN NOT NULL DEFAULT TRUE");
        stmt.execute("CREATE TABLE IF NOT EXISTS archive_economy_wallet (account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, currency_id INT NOT NULL, amount BIGINT, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, archived_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, currency_id))");
        stmt.execute("CREATE TABLE IF NOT EXISTS archive_economy_logs (id BIGINT PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, scope economy_log_scope NOT NULL, currency_id INT NOT NULL, amount BIGINT NOT NULL, transaction_uuid UUID, reference_type VARCHAR(50), reference_id VARCHAR(100), created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, archived_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        stmt.execute("CREATE TABLE IF NOT EXISTS archive_economy_transactions (transaction_uuid UUID PRIMARY KEY, account_type VARCHAR(50) NOT NULL, account_uuid UUID NOT NULL, status VARCHAR(20), created_at TIMESTAMPTZ, archived_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    protected String insertAccountIfAbsentSql() {
        return "INSERT INTO economy_account (account_type, account_uuid) VALUES (?, ?) ON CONFLICT (account_type, account_uuid) DO NOTHING";
    }

    @Override
    protected String insertWalletIfAbsentSql() {
        return "INSERT INTO economy_wallet (account_type, account_uuid, currency_id, amount) VALUES (?, ?, ?, 0) ON CONFLICT (account_type, account_uuid, currency_id) DO NOTHING";
    }

    @Override
    protected String insertTransactionIfAbsentSql() {
        return "INSERT INTO economy_transactions (transaction_uuid, account_type, account_uuid, status) VALUES (?, ?, ?, 'completed') ON CONFLICT (transaction_uuid) DO NOTHING";
    }

    @Override
    protected String updateWalletSql() {
        return "UPDATE economy_wallet SET amount = ?, updated_at = CURRENT_TIMESTAMP WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
    }

    @Override
    protected String upsertCurrencySql() {
        return "INSERT INTO economy_currency (system_code, display_name, is_transferable) VALUES (?, ?, ?) "
                + "ON CONFLICT (system_code) DO UPDATE SET display_name = EXCLUDED.display_name, is_transferable = EXCLUDED.is_transferable, updated_at = CURRENT_TIMESTAMP "
                + "RETURNING id, system_code, display_name, is_transferable, created_at, updated_at";
    }

    @Override
    protected String insertLogSql() {
        return "INSERT INTO economy_logs (account_type, account_uuid, scope, currency_id, amount, transaction_uuid, reference_type, reference_id) VALUES (?, ?, ?::economy_log_scope, ?, ?, ?, ?, ?)";
    }

    /** Runs the upsert and reads the row it returns, in one query. */
    @Override
    protected Currency upsertCurrency(String systemCode, String displayName, boolean transferable) throws SQLException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(upsertCurrencySql())) {
            ps.setString(1, systemCode);
            ps.setString(2, displayName);
            ps.setBoolean(3, transferable);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readCurrency(rs);
                }
            }
        }
        throw new SQLException("Failed to create or fetch currency.");
    }

    /** PostgreSQL stores UUIDs natively. */
    @Override
    protected void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        ps.setObject(index, uuid);
    }

    /** PostgreSQL stores UUIDs natively. */
    @Override
    protected UUID readUuid(ResultSet rs, String column) throws SQLException {
        return (UUID) rs.getObject(column);
    }
}
