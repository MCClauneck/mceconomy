package io.github.mcclauneck.mceconomy.common.currency;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The in-memory currency cache and the configured ("managed") currencies it
 * keeps present in the database.
 */
public class CurrencyRegistry {

    private final IEconomyDatabase database;
    private final Map<String, Currency> currencyCache = new ConcurrentHashMap<>();

    /**
     * The currencies kept present in the economy, keyed by their unique system
     * codes. Sourced from the configuration so server owners can add, remove,
     * rename, or (un)lock currencies without code changes. Insertion order is
     * preserved.
     */
    private final Map<String, CurrencyDefinition> managedCurrencies;

    /**
     * When {@code managedCurrencies} is {@code null} or empty the
     * {@link DefaultCurrencies} are used instead, ensuring the economy always
     * has at least one currency to work with.
     *
     * @param database          The database the currencies are stored in.
     * @param managedCurrencies The configured currencies keyed by system code.
     */
    public CurrencyRegistry(IEconomyDatabase database, Map<String, CurrencyDefinition> managedCurrencies) {
        this.database = database;
        if (managedCurrencies == null || managedCurrencies.isEmpty()) {
            this.managedCurrencies = DefaultCurrencies.DEFINITIONS;
        } else {
            this.managedCurrencies = Collections.unmodifiableMap(new LinkedHashMap<>(managedCurrencies));
        }
    }

    /**
     * Caches all currencies from the database into memory.
     * @return A CompletableFuture.
     */
    public CompletableFuture<Void> load() {
        return this.database.getAllCurrencies().thenAccept(currencies -> {
            currencyCache.clear();
            for (Currency c : currencies) {
                currencyCache.put(c.systemCode().toLowerCase(), c);
            }
        });
    }

    /**
     * @param systemCode The currency system code, in any case.
     * @return The cached currency, or {@code null} if not found.
     */
    public Currency get(String systemCode) {
        return currencyCache.get(systemCode.toLowerCase());
    }

    /**
     * @return A copy of every cached currency, in no particular order.
     */
    public List<Currency> all() {
        return new ArrayList<>(currencyCache.values());
    }

    /**
     * @return The unmodifiable, ordered map of managed system codes to definitions.
     */
    public Map<String, CurrencyDefinition> managed() {
        return this.managedCurrencies;
    }

    /**
     * Returns the cached currencies in a stable display order: the managed
     * currencies first, in configuration order, then any remaining database
     * currencies sorted by system code.
     *
     * @return The currencies in display order.
     */
    public List<Currency> displayOrder() {
        Map<String, Currency> remaining = new LinkedHashMap<>();
        all().stream()
                .sorted((a, b) -> a.systemCode().compareToIgnoreCase(b.systemCode()))
                .forEach(currency -> remaining.put(currency.systemCode().toLowerCase(), currency));

        List<Currency> ordered = new ArrayList<>(remaining.size());
        for (String code : this.managedCurrencies.keySet()) {
            Currency currency = remaining.remove(code.toLowerCase());
            if (currency != null) {
                ordered.add(currency);
            }
        }
        ordered.addAll(remaining.values());
        return ordered;
    }

    /**
     * Ensures that every managed currency exists and matches its configured
     * definition, creating missing currencies and updating the display name or
     * transferability of currencies whose configuration changed.
     * <p>
     * Out-of-date currencies are determined from the in-memory cache, so
     * {@link #load()} should have completed at least once beforehand. If any
     * currencies are written the cache is refreshed afterwards. When every
     * managed currency is already up to date no database calls are made.
     *
     * @return A CompletableFuture that completes once all managed currencies exist.
     */
    public CompletableFuture<Void> ensureManaged() {
        List<CompletableFuture<Currency>> writes = new ArrayList<>();
        for (CurrencyDefinition definition : this.managedCurrencies.values()) {
            Currency existing = get(definition.systemCode());
            boolean upToDate = existing != null
                    && existing.displayName().equals(definition.displayName())
                    && existing.transferable() == definition.transferable();
            if (!upToDate) {
                writes.add(this.database.createCurrency(
                        definition.systemCode(), definition.displayName(), definition.transferable()));
            }
        }

        if (writes.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]))
                .thenCompose(ignored -> load());
    }
}
