package io.github.mcclauneck.mceconomy.api.models;

import java.time.Instant;

/**
 * Represents the economy_currency table.
 * <p>
 * {@code transferable} mirrors the {@code is_transferable} column: when
 * {@code false} the currency can never move between accounts (for example a
 * premium or reward currency), and transfer operations must be rejected.
 */
public record Currency(
    int id,
    String systemCode,
    String displayName,
    boolean transferable,
    Instant createdAt,
    Instant updatedAt
) {}
