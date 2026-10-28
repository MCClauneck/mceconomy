package io.github.mcclauneck.mceconomy.common.database.sql;

import com.zaxxer.hikari.HikariDataSource;
import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.models.Account;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.api.models.Wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The JDBC plumbing the SQL backends share: the Hikari pool, the asynchronous
 * wrapping, and every query whose text is the same in each dialect.
 * <p>
 * A dialect supplies its schema, the statements whose syntax differs (the
 * "insert if absent" and upsert forms, the log insert, the wallet update), and
 * how it binds and reads UUIDs, booleans and timestamps. The tables, columns,
 * and the order of every read and write are exactly those of the dialect
 * classes before this base existed.
 */
public abstract class AbstractSqlEconomyDatabase implements IEconomyDatabase {

    /** The connection pool every query borrows from. */
    protected final HikariDataSource dataSource;

    /** The message the schema failure is reported with, naming the dialect. */
    private final String schemaFailureMessage;

    /**
     * @param dataSource           The configured connection pool.
     * @param schemaFailureMessage The message {@link #initialize()} fails with.
     */
    protected AbstractSqlEconomyDatabase(HikariDataSource dataSource, String schemaFailureMessage) {
        this.dataSource = dataSource;
        this.schemaFailureMessage = schemaFailureMessage;
    }

    /**
     * Creates every table (and type) the dialect needs, if it is missing, and
     * migrates older schemas.
     *
     * @param stmt A statement on an open connection.
     * @throws SQLException When a statement fails.
     */
    protected abstract void createSchema(Statement stmt) throws SQLException;

    /** @return The insert that creates an account row unless it exists (two parameters: type, uuid). */
    protected abstract String insertAccountIfAbsentSql();

    /** @return The insert that creates a wallet row at 0 unless it exists (type, uuid, currency id). */
    protected abstract String insertWalletIfAbsentSql();

    /** @return The insert that records a transaction receipt unless it exists (tx uuid, type, uuid). */
    protected abstract String insertTransactionIfAbsentSql();

    /** @return The update that sets a wallet's amount (amount, type, uuid, currency id). */
    protected abstract String updateWalletSql();

    /** @return The currency upsert (system code, display name, transferable). */
    protected abstract String upsertCurrencySql();

    /**
     * @return The log insert (type, uuid, scope, currency id, amount, tx uuid,
     *         reference type, reference id).
     */
    protected String insertLogSql() {
        return "INSERT INTO economy_logs (account_type, account_uuid, scope, currency_id, amount, transaction_uuid, reference_type, reference_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    }

    /**
     * Binds a UUID, or SQL {@code NULL} for {@code null}. Stored as text by default.
     */
    protected void bindUuid(PreparedStatement ps, int index, UUID uuid) throws SQLException {
        ps.setString(index, uuid != null ? uuid.toString() : null);
    }

    /** Reads a UUID column. Stored as text by default. */
    protected UUID readUuid(ResultSet rs, String column) throws SQLException {
        return UUID.fromString(rs.getString(column));
    }

    /** Binds a boolean. A native boolean by default. */
    protected void bindBoolean(PreparedStatement ps, int index, boolean value) throws SQLException {
        ps.setBoolean(index, value);
    }

    /** Reads a boolean column. A native boolean by default. */
    protected boolean readBoolean(ResultSet rs, String column) throws SQLException {
        return rs.getBoolean(column);
    }

    /** Reads a timestamp column. A native timestamp by default. */
    protected Instant readInstant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    /**
     * Runs an {@code ALTER TABLE ... ADD COLUMN} and ignores the error a column
     * that already exists raises, for dialects without {@code ADD COLUMN IF NOT EXISTS}.
     *
     * @param stmt The statement to run it on.
     * @param sql  The {@code ALTER TABLE} statement.
     */
    protected static void addColumnIfMissing(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            // Column already exists.
        }
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                createSchema(stmt);
            } catch (SQLException e) {
                throw new RuntimeException(this.schemaFailureMessage, e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(dataSource::close);
    }

    @Override
    public CompletableFuture<Account> createAccount(String accountType, UUID accountUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(insertAccountIfAbsentSql())) {
                ps.setString(1, accountType);
                bindUuid(ps, 2, accountUuid);
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
                bindUuid(ps, 2, accountUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new Account(rs.getString("account_type"), readUuid(rs, "account_uuid"),
                                readInstant(rs, "created_at"), readInstant(rs, "updated_at")));
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
            try {
                return upsertCurrency(systemCode, displayName, transferable);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Runs the currency upsert, then reads the row back by its system code.
     * A dialect whose upsert can return the row overrides this with one query.
     *
     * @return The created or updated currency.
     * @throws SQLException When a statement fails or the row cannot be read back.
     */
    protected Currency upsertCurrency(String systemCode, String displayName, boolean transferable) throws SQLException {
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(upsertCurrencySql())) {
            ps.setString(1, systemCode);
            ps.setString(2, displayName);
            bindBoolean(ps, 3, transferable);
            ps.executeUpdate();
        }

        String fetchSql = "SELECT * FROM economy_currency WHERE system_code = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(fetchSql)) {
            ps.setString(1, systemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readCurrency(rs);
                }
            }
        }
        throw new SQLException("Failed to fetch currency.");
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
    protected Currency readCurrency(ResultSet rs) throws SQLException {
        return new Currency(rs.getInt("id"), rs.getString("system_code"), rs.getString("display_name"),
                readBoolean(rs, "is_transferable"),
                readInstant(rs, "created_at"), readInstant(rs, "updated_at"));
    }

    @Override
    public CompletableFuture<Wallet> getWallet(String accountType, UUID accountUuid, int currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            // Ensure the account exists
            createAccount(accountType, accountUuid).join();

            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(insertWalletIfAbsentSql())) {
                ps.setString(1, accountType);
                bindUuid(ps, 2, accountUuid);
                ps.setInt(3, currencyId);
                ps.execute();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            String fetchSql = "SELECT * FROM economy_wallet WHERE account_type = ? AND account_uuid = ? AND currency_id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(fetchSql)) {
                ps.setString(1, accountType);
                bindUuid(ps, 2, accountUuid);
                ps.setInt(3, currencyId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Wallet(rs.getString("account_type"), readUuid(rs, "account_uuid"),
                                rs.getInt("currency_id"), rs.getLong("amount"),
                                readInstant(rs, "created_at"), readInstant(rs, "updated_at"));
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
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(updateWalletSql())) {
                ps.setLong(1, newAmount);
                ps.setString(2, accountType);
                bindUuid(ps, 3, accountUuid);
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
            // First ensure the transaction receipt exists, if one is given
            if (transactionUuid != null) {
                try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(insertTransactionIfAbsentSql())) {
                    bindUuid(ps, 1, transactionUuid);
                    ps.setString(2, accountType);
                    bindUuid(ps, 3, accountUuid);
                    ps.execute();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(insertLogSql())) {
                ps.setString(1, accountType);
                bindUuid(ps, 2, accountUuid);
                ps.setString(3, scope.toString());
                ps.setInt(4, currencyId);
                ps.setLong(5, amount);
                bindUuid(ps, 6, transactionUuid);
                ps.setString(7, referenceType);
                ps.setString(8, referenceId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
