package io.github.mcclauneck.bukkit.gui;

/**
 * One row of the balance GUI: a currency plus the viewing identity's balance,
 * resolved asynchronously before the GUI is opened.
 *
 * @param systemCode   the currency system code
 * @param displayName  the human-readable currency name
 * @param transferable whether the currency can move between accounts
 * @param texture      the base64 head texture configured for the currency, or {@code null}
 * @param balance      the identity's balance for the currency
 */
public record CurrencyBalanceEntry(
    String systemCode,
    String displayName,
    boolean transferable,
    String texture,
    long balance
) {}
