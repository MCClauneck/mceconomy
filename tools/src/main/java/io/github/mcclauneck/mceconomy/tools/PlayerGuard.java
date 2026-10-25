package io.github.mcclauneck.mceconomy.tools;

import java.util.Optional;
import java.util.UUID;

/**
 * The fail-closed caller check every MCEconomy tool runs first.
 *
 * <p>The tools only report the caller's own data, so no OP or permission check
 * is needed — but a caller that is not an online player has no "own" wallet,
 * and is refused rather than guessed at.</p>
 */
final class PlayerGuard {

    private PlayerGuard() {
    }

    /**
     * Resolves the calling player, or explains why the call is refused.
     *
     * @param caller The caller handed over by MCAgents.
     * @param port   The economy view, used to confirm the player is online.
     * @param what   What was asked for, for the error message.
     * @return The online player's UUID on the right, or the refusal text on the left.
     */
    static Result require(ToolCaller caller, EconomyToolPort port, String what) {
        if (!port.isEconomyReady()) {
            return Result.denied("Error: MCEconomy is not running right now, so " + what + " cannot be read.");
        }
        Optional<UUID> player = caller == null ? Optional.empty() : caller.playerUuid();
        if (player.isEmpty() || !port.isOnlinePlayer(player.get())) {
            return Result.denied("Error: " + capitalise(what) + " is only available to an in-game player.");
        }
        return new Result(player.get(), null);
    }

    private static String capitalise(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Either the verified player or the refusal message.
     *
     * @param playerUuid The verified online player, or {@code null} when denied.
     * @param error      The refusal message, or {@code null} when allowed.
     */
    record Result(UUID playerUuid, String error) {

        static Result denied(String error) {
            return new Result(null, error);
        }

        boolean allowed() {
            return this.error == null;
        }
    }
}
