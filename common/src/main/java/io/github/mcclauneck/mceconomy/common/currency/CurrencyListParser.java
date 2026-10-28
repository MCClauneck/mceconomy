package io.github.mcclauneck.mceconomy.common.currency;

import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the {@code "code,Display Name,transferable"} currency entries written
 * in {@code config.yml}.
 */
public final class CurrencyListParser {

    private CurrencyListParser() {
    }

    /**
     * Parses a raw list of {@code "code,Display Name,transferable"} entries into
     * an ordered map of system codes to currency definitions.
     * <p>
     * Each entry is split on commas: the first part is the system code
     * (lower-cased), the second the display name, and the optional third whether
     * the currency is transferable ({@code true} unless it equals
     * {@code "false"}, case-insensitively). When an entry has no display name
     * the code is reused as the display name. Blank entries and entries without
     * a usable code are skipped, and later duplicates of a code overwrite
     * earlier ones. Insertion order is preserved.
     *
     * @param entries The raw configuration entries; may be {@code null}.
     * @return An ordered map of system code to definition (never {@code null}).
     */
    public static Map<String, CurrencyDefinition> parse(List<String> entries) {
        Map<String, CurrencyDefinition> parsed = new LinkedHashMap<>();
        if (entries == null) {
            return parsed;
        }
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }
            String[] parts = entry.split(",", 3);
            String code = parts[0].trim().toLowerCase();
            if (code.isEmpty()) {
                continue;
            }
            String display = parts.length > 1 ? parts[1].trim() : "";
            if (display.isEmpty()) {
                display = code;
            }
            boolean transferable = parts.length <= 2 || !"false".equalsIgnoreCase(parts[2].trim());
            parsed.put(code, new CurrencyDefinition(code, display, transferable));
        }
        return parsed;
    }
}
