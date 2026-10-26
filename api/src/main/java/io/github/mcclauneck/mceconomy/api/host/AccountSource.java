package io.github.mcclauneck.mceconomy.api.host;

import io.github.mcclauneck.mceconomy.api.models.AccountTypes;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves the economy account a player's money belongs to.
 * <p>
 * An account is an {@code account_type} plus an {@code account_uuid}. Which
 * pair a player maps to depends on what the server runs: with MCIdentity the
 * account is the player's active identity, and without it the account is the
 * player. Callers read the type off the source and never test which binding is
 * active, so {@code /balance}, {@code /pay} and the AI tools take one path for
 * both.
 * </p>
 */
public interface AccountSource {

    /**
     * The account type every account resolved by this source is keyed under.
     * Constant for the lifetime of the source.
     *
     * @return the account type, never {@code null}
     */
    String accountType();

    /**
     * @return {@code true} when {@link #accountUuid(UUID)} can be served
     */
    boolean isReady();

    /**
     * Resolves the account UUID a player's money is keyed by.
     *
     * @param playerUuid the player to resolve an account for
     * @return a future completing with the account UUID, or an empty optional
     *         when the player currently has no account under this binding
     */
    CompletableFuture<Optional<UUID>> accountUuid(UUID playerUuid);

    /**
     * The binding used when MCIdentity is not installed: a player's money
     * belongs to the player, keyed by the player UUID under
     * {@link AccountTypes#PLAYER}. Always ready, always resolves.
     *
     * @return a source keying wallets to player accounts
     */
    static AccountSource players() {
        return new AccountSource() {
            @Override
            public String accountType() {
                return AccountTypes.PLAYER;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public CompletableFuture<Optional<UUID>> accountUuid(UUID playerUuid) {
                return CompletableFuture.completedFuture(Optional.of(playerUuid));
            }
        };
    }

    /**
     * The binding used when MCIdentity is installed and bound: a player's money
     * belongs to their active identity, keyed by the identity UUID under
     * {@link AccountTypes#IDENTITY}.
     *
     * @param identities the MCIdentity binding
     * @return a source keying wallets to identity accounts
     */
    static AccountSource identities(IdentitySource identities) {
        Objects.requireNonNull(identities, "identities");
        return new AccountSource() {
            @Override
            public String accountType() {
                return AccountTypes.IDENTITY;
            }

            @Override
            public boolean isReady() {
                return identities.isAvailable();
            }

            @Override
            public CompletableFuture<Optional<UUID>> accountUuid(UUID playerUuid) {
                return identities.activeIdentity(playerUuid);
            }
        };
    }
}
