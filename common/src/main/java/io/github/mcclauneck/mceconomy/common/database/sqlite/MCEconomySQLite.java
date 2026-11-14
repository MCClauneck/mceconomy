package io.github.mcclauneck.mceconomy.common.database.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mcclauneck.mceconomy.common.database.sql.AbstractSqlEconomyDatabase;
import io.github.mcclauneck.mceconomy.common.database.sql.SharedAccounts;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;

/**
 * SQLite implementation of the Economy Database: the SQLite schema,
 * {@code INSERT OR IGNORE} / {@code ON CONFLICT} statements, integer booleans,
 * and text timestamps. The queries themselves are shared through
 * {@link AbstractSqlEconomyDatabase}.
 */
public class MCEconomySQLite extends AbstractSqlEconomyDatabase {

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructs the SQLite database connection.
     *
     * @param databaseFile The file to store the SQLite database.
     */
    public MCEconomySQLite(File databaseFile) {
        super(createDataSource(databaseFile), "Failed to initialize SQLite schema", SharedAccounts.SQLITE);
        initialize();
    }

    private static HikariDataSource createDataSource(File databaseFile) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite handles concurrent writes poorly, so limit pool size
        return new HikariDataSource(config);
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
    protected void createSchema(Statement stmt) throws SQLException {
        stmt.execute("PRAGMA foreign_keys = ON;");

        stmt.execute("CREATE TABLE IF NOT EXISTS economy_currency (id INTEGER PRIMARY KEY AUTOINCREMENT, system_code TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL, is_transferable INTEGER NOT NULL DEFAULT 1, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        // Migrate files created before the column existed. SQLite has no
        // ADD COLUMN IF NOT EXISTS, so the duplicate-column error is swallowed.
        addColumnIfMissing(stmt, "ALTER TABLE economy_currency ADD COLUMN is_transferable INTEGER NOT NULL DEFAULT 1");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_wallet (account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, currency_id INTEGER NOT NULL, amount INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, currency_id), FOREIGN KEY (account_type, account_uuid) REFERENCES accounts (account_type, account_uuid) ON DELETE CASCADE, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT)");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_transactions (transaction_uuid TEXT PRIMARY KEY, account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'completed', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES accounts (account_type, account_uuid) ON DELETE CASCADE)");
        stmt.execute("CREATE TABLE IF NOT EXISTS economy_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, account_type TEXT NOT NULL, account_uuid TEXT NOT NULL, scope TEXT NOT NULL, currency_id INTEGER NOT NULL, amount INTEGER NOT NULL, transaction_uuid TEXT, reference_type TEXT, reference_id TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid) REFERENCES accounts (account_type, account_uuid) ON DELETE RESTRICT, FOREIGN KEY (currency_id) REFERENCES economy_currency (id) ON DELETE RESTRICT, FOREIGN KEY (transaction_uuid) REFERENCES economy_transactions (transaction_uuid) ON DELETE SET NULL)");
    }

    @Override
    protected String insertWalletIfAbsentSql() {
        return "INSERT OR IGNORE INTO economy_wallet (account_type, account_uuid, currency_id, amount) VALUES (?, ?, ?, 0)";
    }

    @Override
    protected String insertTransactionIfAbsentSql() {
        return "INSERT OR IGNORE INTO economy_transactions (transaction_uuid, account_type, account_uuid, status) VALUES (?, ?, ?, 'completed')";
    }

    @Override
    protected String updateWalletSql() {
        return "UPDATE economy_wallet SET amount = ?, updated_at = CURRENT_TIMESTAMP WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
    }

    @Override
    protected String upsertCurrencySql() {
        return "INSERT INTO economy_currency (system_code, display_name, is_transferable) VALUES (?, ?, ?) "
                + "ON CONFLICT (system_code) DO UPDATE SET display_name = excluded.display_name, is_transferable = excluded.is_transferable, updated_at = CURRENT_TIMESTAMP";
    }

    /** SQLite stores booleans as integers. */
    @Override
    protected void bindBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
        ps.setInt(index, value ? 1 : 0);
    }

    /** SQLite stores booleans as integers. */
    @Override
    protected boolean readBoolean(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) != 0;
    }

    /** SQLite stores timestamps as text. */
    @Override
    protected Instant readInstant(ResultSet rs, String column) throws SQLException {
        return parseTimestamp(rs.getString(column)).toInstant();
    }
}
