package io.github.mcclauneck.mceconomy.bukkit.config;

import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for parsing the structured and legacy {@code currencies} configuration.
 */
class CurrencyConfigTest {

    private static YamlConfiguration load(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException(e);
        }
        return config;
    }

    @Test
    @DisplayName("structured section parses display name, transferable flag, and texture")
    void testStructuredSection() {
        YamlConfiguration config = load("""
                currencies:
                  coin:
                    display_name: Coin
                    transferable: true
                    texture: texture-coin
                  gold:
                    display_name: Gold
                    transferable: false
                    texture: texture-gold
                  bare: {}
                """);

        Map<String, CurrencyDefinition> definitions = CurrencyConfig.parseDefinitions(config);
        assertEquals(List.of("coin", "gold", "bare"), List.copyOf(definitions.keySet()),
                "configuration order is preserved");
        assertTrue(definitions.get("coin").transferable());
        assertFalse(definitions.get("gold").transferable());
        assertEquals("Gold", definitions.get("gold").displayName());
        assertTrue(definitions.get("bare").transferable(), "missing flag defaults to true");
        assertEquals("bare", definitions.get("bare").displayName(), "missing display name falls back to the code");

        Map<String, String> textures = CurrencyConfig.parseTextures(config);
        assertEquals("texture-coin", textures.get("coin"));
        assertEquals("texture-gold", textures.get("gold"));
        assertFalse(textures.containsKey("bare"), "currencies without a texture are absent");
    }

    @Test
    @DisplayName("legacy list format still parses and carries no textures")
    void testLegacyList() {
        YamlConfiguration config = load("""
                currencies:
                  - coin,Coin,true
                  - gold,Gold,false
                """);

        Map<String, CurrencyDefinition> definitions = CurrencyConfig.parseDefinitions(config);
        assertEquals(2, definitions.size());
        assertTrue(definitions.get("coin").transferable());
        assertFalse(definitions.get("gold").transferable());

        assertTrue(CurrencyConfig.parseTextures(config).isEmpty());
    }

    @Test
    @DisplayName("a missing currencies section yields empty results")
    void testMissingSection() {
        YamlConfiguration config = load("enable: true");
        assertTrue(CurrencyConfig.parseDefinitions(config).isEmpty());
        assertTrue(CurrencyConfig.parseTextures(config).isEmpty());
    }
}
