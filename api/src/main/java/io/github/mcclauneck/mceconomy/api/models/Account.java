package io.github.mcclauneck.mceconomy.api.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a row of the shared {@code accounts} table (the {@code accounts}
 * collection on MongoDB) that every account-keyed economy row references.
 */
public record Account(
    String accountType,
    UUID accountUuid,
    Instant createdAt,
    Instant updatedAt
) {}
