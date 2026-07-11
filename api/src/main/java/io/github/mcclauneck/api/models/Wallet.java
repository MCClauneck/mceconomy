package io.github.mcclauneck.api.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the economy_wallet table.
 */
public record Wallet(
    String accountType,
    UUID accountUuid,
    int currencyId,
    long amount,
    Instant createdAt,
    Instant updatedAt
) {}
