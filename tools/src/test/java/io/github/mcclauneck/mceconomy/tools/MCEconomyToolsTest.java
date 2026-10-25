package io.github.mcclauneck.mceconomy.tools;

import io.github.mcclauneck.mceconomy.api.models.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the MCAgents proxy adapter and the read-only economy tools, using a
 * stand-in for MCAgents' {@code AITool} interface and {@code AIAccount} record.
 */
class MCEconomyToolsTest {

    /** Same shape as {@code io.github.mcpaimon.mcagents.tools.AITool}. */
    public interface FakeAITool {
        String getName();
        String getDescription();
        String getParametersJsonSchema();
        List<String> getCategories();
        CompletableFuture<String> execute(Map<String, Object> arguments, FakeAccount account);
    }

    /** Same accessors as {@code io.github.mcpaimon.mcagents.api.model.AIAccount}. */
    public record FakeAccount(String accountType, String accountUuid, int platformId, String token) {
    }

    private static final UUID ONLINE = UUID.randomUUID();
    private static final UUID IDENTITY = UUID.randomUUID();
    private static final Currency COIN = new Currency(1, "coin", "Coin", true, Instant.EPOCH, Instant.EPOCH);
    private static final Currency GEM = new Currency(2, "gem", "Gem", false, Instant.EPOCH, Instant.EPOCH);

    /** In-memory economy: one online player with one equipped identity. */
    private static final class StubPort implements EconomyToolPort {
        final Map<String, Long> balances = new HashMap<>(Map.of("coin", 1234L, "gem", 5L));
        final Set<UUID> balanceReads = new java.util.HashSet<>();

        @Override public boolean isEconomyReady() { return true; }
        @Override public boolean isIdentityReady() { return true; }
        @Override public boolean isOnlinePlayer(UUID playerUuid) { return ONLINE.equals(playerUuid); }

        @Override
        public CompletableFuture<Optional<UUID>> activeIdentity(UUID playerUuid) {
            return CompletableFuture.completedFuture(ONLINE.equals(playerUuid) ? Optional.of(IDENTITY) : Optional.empty());
        }

        @Override public List<Currency> currencies() { return List.of(COIN, GEM); }

        @Override
        public CompletableFuture<Long> balance(UUID identityUuid, Currency currency) {
            this.balanceReads.add(identityUuid);
            return CompletableFuture.completedFuture(this.balances.get(currency.systemCode()));
        }
    }

    private static FakeAITool proxy(PluginTool tool) {
        return (FakeAITool) MCAgentsToolAdapter.createProxy(FakeAITool.class, tool);
    }

    @Test
    @DisplayName("the proxy maps every AITool method onto the wrapped tool")
    void proxyMapsCalls() {
        StubPort port = new StubPort();
        PluginTool tool = new GetMyBalancesTool(port);
        FakeAITool ai = proxy(tool);

        assertEquals("economy_get_my_balances", ai.getName());
        assertEquals(tool.getDescription(), ai.getDescription());
        assertEquals(tool.getParametersJsonSchema(), ai.getParametersJsonSchema());
        assertEquals(List.of(EconomyTools.CATEGORY), ai.getCategories());
        assertEquals(ai, ai);
        assertNotEquals(ai, proxy(tool));
        assertEquals(System.identityHashCode(ai), ai.hashCode());
        assertTrue(ai.toString().contains("economy_get_my_balances"));

        String result = ai.execute(Map.of(), new FakeAccount("player", ONLINE.toString(), 1, "secret")).join();
        assertTrue(result.contains("Coin (coin): 1,234"), result);
        assertTrue(result.contains("Gem (gem): 5"), result);
        assertEquals(Set.of(IDENTITY), port.balanceReads, "balances are read from the identity wallet only");
    }

    @Test
    @DisplayName("the balance tool narrows to one currency and rejects unknown codes")
    void balanceFilter() {
        FakeAITool ai = proxy(new GetMyBalancesTool(new StubPort()));
        FakeAccount me = new FakeAccount("player", ONLINE.toString(), 1, "t");

        String one = ai.execute(Map.of("currency", "GEM"), me).join();
        assertTrue(one.contains("Gem (gem): 5"), one);
        assertFalse(one.contains("Coin"), one);

        String unknown = ai.execute(Map.of("currency", "dollar"), me).join();
        assertTrue(unknown.startsWith("Error: Unknown currency 'dollar'"), unknown);
    }

    @Test
    @DisplayName("every tool fails closed for a caller that is not an online player")
    void rejectsNonPlayers() {
        StubPort port = new StubPort();
        for (PluginTool tool : EconomyTools.create(port)) {
            FakeAITool ai = proxy(tool);
            for (FakeAccount caller : List.of(
                    new FakeAccount("server", ONLINE.toString(), 1, "t"),
                    new FakeAccount("player", UUID.randomUUID().toString(), 1, "t"),
                    new FakeAccount("player", "not-a-uuid", 1, "t"))) {
                String result = ai.execute(Map.of(), caller).join();
                assertTrue(result.startsWith("Error:") && result.endsWith("only available to an in-game player."),
                        tool.getName() + " -> " + result);
            }
            assertTrue(ai.execute(null, null).join().startsWith("Error:"));
        }
        assertTrue(port.balanceReads.isEmpty(), "no wallet is read for a refused caller");
    }

    @Test
    @DisplayName("the currency tool lists codes, names, and transferability")
    void listsCurrencies() {
        String result = proxy(new ListCurrenciesTool(new StubPort()))
                .execute(Map.of(), new FakeAccount("player", ONLINE.toString(), 1, "t")).join();
        assertTrue(result.contains("coin: Coin (transferable with /pay)"), result);
        assertTrue(result.contains("gem: Gem (not transferable)"), result);
    }

    @Test
    @DisplayName("every tool has a snake_case name and a schema that is a valid JSON object")
    void schemasAreValidJson() {
        for (PluginTool tool : EconomyTools.create(new StubPort())) {
            assertTrue(tool.getName().matches("[a-z]+(_[a-z]+)*"), tool.getName());
            String schema = tool.getParametersJsonSchema();
            assertTrue(schema.trim().startsWith("{"), schema);
            assertTrue(new JsonChecker(schema).valid(), schema);
        }
    }

    /** Minimal strict JSON syntax checker, so the test needs no JSON library. */
    private static final class JsonChecker {
        private final String s;
        private int i;

        JsonChecker(String s) { this.s = s; }

        boolean valid() {
            try {
                value();
                ws();
                return i == s.length();
            } catch (RuntimeException e) {
                return false;
            }
        }

        private void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        private void expect(char c) { ws(); if (s.charAt(i++) != c) throw new IllegalStateException(); }

        private void value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++; ws();
                if (s.charAt(i) == '}') { i++; return; }
                do { ws(); string(); expect(':'); value(); ws(); } while (s.charAt(i++) == ',');
                if (s.charAt(i - 1) != '}') throw new IllegalStateException();
            } else if (c == '[') {
                i++; ws();
                if (s.charAt(i) == ']') { i++; return; }
                do { value(); ws(); } while (s.charAt(i++) == ',');
                if (s.charAt(i - 1) != ']') throw new IllegalStateException();
            } else if (c == '"') {
                string();
            } else if (s.startsWith("true", i) || s.startsWith("null", i)) {
                i += 4;
            } else if (s.startsWith("false", i)) {
                i += 5;
            } else {
                int start = i;
                while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) i++;
                if (start == i) throw new IllegalStateException();
            }
        }

        private void string() {
            if (s.charAt(i++) != '"') throw new IllegalStateException();
            while (s.charAt(i) != '"') { if (s.charAt(i) == '\\') i++; i++; }
            i++;
        }
    }
}
