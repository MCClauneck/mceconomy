package io.github.mcclauneck.bukkit.config;

import io.github.mcclauneck.api.models.CurrencyDefinition;
import io.github.mcclauneck.common.MCEconomyProvider;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the {@code currencies} section of {@code config.yml}.
 * <p>
 * The preferred format is a section keyed by system code:
 * <pre>{@code
 * currencies:
 *   coin:
 *     display_name: Coin
 *     transferable: true
 *     texture: <base64>
 * }</pre>
 * The legacy list format ({@code "code,Display Name,transferable"}) is still
 * accepted so existing configurations keep working; it carries no textures.
 */
public final class CurrencyConfig {

    private CurrencyConfig() {
    }

    /**
     * Reads the configured currency definitions.
     *
     * @param config the root configuration (the section named {@code currencies} is read)
     * @return the configured currencies keyed by system code, in configuration order
     */
    public static Map<String, CurrencyDefinition> parseDefinitions(ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("currencies");
        if (section == null) {
            // Legacy list format.
            return MCEconomyProvider.parseCurrencyList(config.getStringList("currencies"));
        }

        Map<String, CurrencyDefinition> currencies = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String code = key.trim().toLowerCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            ConfigurationSection currency = section.getConfigurationSection(key);
            String displayName = currency != null ? currency.getString("display_name", code) : code;
            boolean transferable = currency == null || currency.getBoolean("transferable", true);
            currencies.put(code, new CurrencyDefinition(code, displayName, transferable));
        }
        return currencies;
    }

    /**
     * Reads the base64 head texture configured for each currency, used by the
     * {@code /balance} GUI. Currencies without a texture are simply absent.
     *
     * @param config the root configuration (the section named {@code currencies} is read)
     * @return base64 textures keyed by lower-case currency system code
     */
    public static Map<String, String> parseTextures(ConfigurationSection config) {
        Map<String, String> textures = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("currencies");
        if (section == null) {
            return textures;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection currency = section.getConfigurationSection(key);
            String texture = currency != null ? currency.getString("texture") : null;
            if (texture != null && !texture.isBlank()) {
                textures.put(key.trim().toLowerCase(Locale.ROOT), texture);
            }
        }
        return textures;
    }
}
