package io.github.mcclauneck.mceconomy.tools;

import io.github.mcclauneck.mceconomy.api.models.Currency;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The platform-free view of MCEconomy that the AI tools read through.
 *
 * <p>The bukkit module implements it over the live provider and the account
 * binding. Every method is read-only and none touches a player entity, so the
 * tools are safe to run from whatever thread MCAgents calls them on.</p>
 */
public interface EconomyToolPort {

    /**
     * @return {@code true} while the MCEconomy provider is enabled and serving reads.
     */
    boolean isEconomyReady();

    /**
     * @return {@code true} when the account binding is loaded and answering lookups.
     */
    boolean isAccountReady();

    /**
     * Checks whether a player with this UUID is online on this server.
     *
     * @param playerUuid The player to look for.
     * @return {@code true} when the player is online.
     */
    boolean isOnlinePlayer(UUID playerUuid);

    /**
     * Resolves the account a player's wallets are keyed by: the player UUID
     * under account type {@code player}.
     *
     * @param playerUuid The player.
     * @return The account UUID, or empty when there is none.
     */
    CompletableFuture<Optional<UUID>> accountUuid(UUID playerUuid);

    /**
     * @return Every cached currency, in the same order the {@code /balance} GUI shows them.
     */
    List<Currency> currencies();

    /**
     * Reads one balance of a player's wallet, asynchronously.
     *
     * @param accountUuid The account whose wallet is read.
     * @param currency    The currency.
     * @return The balance.
     */
    CompletableFuture<Long> balance(UUID accountUuid, Currency currency);
}
