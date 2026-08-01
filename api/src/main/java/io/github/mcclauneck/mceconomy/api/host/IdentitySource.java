package io.github.mcclauneck.mceconomy.api.host;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only view of the player's active MCIdentity identity.
 * <p>
 * Every wallet in this economy is keyed by an identity UUID rather than a player
 * UUID, so resolving the equipped identity is the first step of {@code /balance},
 * {@code /pay}, and account creation. The contract is pure Java: the binding to
 * the live MCIdentity plugin is reflective and lives in the bukkit module, which
 * is what lets this plugin build with no MCIdentity artifact on its classpath.
 * </p>
 * <p>
 * Every failure degrades to an empty result rather than an exception, so a
 * missing or incompatible identity plugin leaves the economy inert instead of
 * crediting the wrong wallet.
 * </p>
 */
public interface IdentitySource {

    /**
     * @return {@code true} when the identity host is loaded and ready to answer
     *         lookups.
     */
    boolean isAvailable();

    /**
     * Resolves the identity the given player currently has equipped.
     *
     * @param playerUuid The player to look up.
     * @return A future completing with the active identity UUID, or an empty
     *         optional when the player has none and on every failure path.
     */
    CompletableFuture<Optional<UUID>> activeIdentity(UUID playerUuid);
}
