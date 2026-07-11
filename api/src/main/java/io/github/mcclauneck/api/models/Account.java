package io.github.mcclauneck.api.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the economy_account table.
 */
public record Account(
    String accountType,
    UUID accountUuid,
    Instant createdAt,
    Instant updatedAt
) {}
