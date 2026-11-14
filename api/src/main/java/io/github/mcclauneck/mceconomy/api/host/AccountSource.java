package io.github.mcclauneck.mceconomy.api.host;

import io.github.mcclauneck.mceconomy.api.models.AccountTypes;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves the economy account a player's money belongs to.
 * <p>
 * An account is an {@code account_type} plus an {@code account_uuid}. A
 * player's money belongs to the player: type {@link AccountTypes#PLAYER} and
 * the player UUID. Callers read the type off the source rather than hard-coding
 * it, so {@code /balance}, {@code /pay} and the AI tools take one path.
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
     * The binding every server uses: a player's money belongs to the player,
     * keyed by the player UUID under {@link AccountTypes#PLAYER}. Always ready,
     * always resolves.
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
}
