package io.github.mcclauneck.mceconomy.api.models;

/**
 * A currency as declared in the plugin configuration.
 * <p>
 * Definitions describe the currencies the server owner wants to exist; the
 * provider reconciles them into the {@code economy_currency} table on enable,
 * producing the persisted {@link Currency} rows. {@code transferable} maps to
 * the {@code is_transferable} column — when {@code false} the currency can
 * never move between accounts.
 */
public record CurrencyDefinition(
    String systemCode,
    String displayName,
    boolean transferable
) {}
