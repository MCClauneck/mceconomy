package io.github.mcclauneck.mceconomy.common.database.sql;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the shared {@code accounts} statement of every dialect to the SHA-256
 * of the canonical file, so MCEconomy creates exactly the table MCMultiverse,
 * MCRealm and the other account-keyed plugins create.
 */
class SharedAccountsTest {

    /** The SHA-256 of each canonical {@code schema/accounts/{dialect}.sql}. */
    private static final Map<SharedAccounts, String> CANONICAL_SHA256 = Map.of(
            SharedAccounts.MYSQL, "53f036befa991a4aad1f89bfb4f6d1d83cdcbea2f912d5de22b3a0f56d244f8b",
            SharedAccounts.POSTGRESQL, "6c83151993d0e21479d525ac8e418994c03f143dc4cbad949a29fabc8a375939",
            SharedAccounts.SQLITE, "2ec386587f97449964726254eb6a87ab6600b5902960e07571fe85110fff7c1c");

    @Test
    void everyDialectIsPinned() {
        assertEquals(SharedAccounts.values().length, CANONICAL_SHA256.size());
    }

    @Test
    void canonicalFilesMatchTheirPinnedHashes() throws NoSuchAlgorithmException {
        for (SharedAccounts dialect : SharedAccounts.values()) {
            byte[] bytes = dialect.canonicalFile().getBytes(StandardCharsets.UTF_8);
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(CANONICAL_SHA256.get(dialect), sha256, dialect.resource());
        }
    }

    @Test
    void executedStatementIsTheCanonicalLine() {
        for (SharedAccounts dialect : SharedAccounts.values()) {
            String statement = dialect.createTableSql();
            assertEquals(dialect.canonicalFile(), statement + "\n", dialect.resource());
            assertTrue(statement.startsWith("CREATE TABLE IF NOT EXISTS accounts ("), statement);
            assertFalse(statement.contains("account_property"), statement);
        }
    }
}
