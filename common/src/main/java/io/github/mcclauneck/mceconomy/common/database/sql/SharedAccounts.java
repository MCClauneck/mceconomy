package io.github.mcclauneck.mceconomy.common.database.sql;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The shared {@code accounts} table, per SQL dialect.
 * <p>
 * MCMultiverse, MCRealm and every account-keyed plugin on the same database
 * create {@code accounts} with one canonical statement per dialect, so the
 * first plugin to start creates it and the others find it there. The
 * statement is read from {@code schema/accounts/{dialect}.sql}, a byte-for-byte
 * copy of the canonical file whose SHA-256 a unit test pins. MCEconomy never
 * creates {@code account_property}; it belongs to MCMultiverse and MCRealm.
 */
public enum SharedAccounts {

    /** SQLite: {@code TEXT} key columns. */
    SQLITE("sqlite", "INSERT OR IGNORE INTO accounts (account_type, account_uuid) VALUES (?, ?)"),

    /** MySQL: {@code VARCHAR(50)} type and {@code VARCHAR(36)} UUID. */
    MYSQL("mysql", "INSERT IGNORE INTO accounts (account_type, account_uuid) VALUES (?, ?)"),

    /** PostgreSQL: {@code VARCHAR(50)} type and native {@code UUID}. */
    POSTGRESQL("postgresql", "INSERT INTO accounts (account_type, account_uuid) VALUES (?, ?) ON CONFLICT (account_type, account_uuid) DO NOTHING");

    /** The schema folder name of the dialect. */
    private final String dialect;

    /** The insert creating an account row unless it exists (two parameters: type, uuid). */
    private final String insertIfAbsentSql;

    SharedAccounts(String dialect, String insertIfAbsentSql) {
        this.dialect = dialect;
        this.insertIfAbsentSql = insertIfAbsentSql;
    }

    /** @return The classpath resource holding the canonical statement. */
    public String resource() {
        return "/schema/accounts/" + this.dialect + ".sql";
    }

    /**
     * Reads the canonical file exactly as shipped.
     *
     * @return The file content, including its trailing newline.
     */
    public String canonicalFile() {
        try (InputStream in = SharedAccounts.class.getResourceAsStream(resource())) {
            if (in == null) {
                throw new IllegalStateException("Missing shared schema resource " + resource());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read shared schema resource " + resource(), e);
        }
    }

    /** @return The {@code CREATE TABLE IF NOT EXISTS accounts} statement, without the file's trailing newline. */
    public String createTableSql() {
        return canonicalFile().strip();
    }

    /** @return The insert creating an account row unless it exists (two parameters: type, uuid). */
    public String insertIfAbsentSql() {
        return this.insertIfAbsentSql;
    }
}
