package io.github.mcclauneck.api.models;

import java.time.Instant;

/**
 * Represents the economy_currency table.
 */
public record Currency(
    int id,
    String systemCode,
    String displayName,
    Instant createdAt,
    Instant updatedAt
) {}
