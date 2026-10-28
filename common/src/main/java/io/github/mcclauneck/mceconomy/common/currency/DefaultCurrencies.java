package io.github.mcclauneck.mceconomy.common.currency;

import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The built-in fallback currencies, used only when the configuration supplies
 * none. Server owners are expected to manage the real list through
 * {@code config.yml}; this exists purely as a safety net.
 */
public final class DefaultCurrencies {

    /**
     * The fallback currencies keyed by their unique system codes. Insertion
     * order is preserved so currencies are created in a predictable order.
     */
    public static final Map<String, CurrencyDefinition> DEFINITIONS;

    static {
        Map<String, CurrencyDefinition> defaults = new LinkedHashMap<>();
        defaults.put("coin", new CurrencyDefinition("coin", "Coin", true));
        defaults.put("copper", new CurrencyDefinition("copper", "Copper", true));
        defaults.put("silver", new CurrencyDefinition("silver", "Silver", true));
        defaults.put("gold", new CurrencyDefinition("gold", "Gold", false));
        defaults.put("point", new CurrencyDefinition("point", "Point", false));
        defaults.put("token", new CurrencyDefinition("token", "Token", false));
        DEFINITIONS = Collections.unmodifiableMap(defaults);
    }

    private DefaultCurrencies() {
    }
}
