package io.github.mcclauneck.mceconomy.tools;

import java.util.Optional;
import java.util.UUID;

/**
 * The account that asked MCAgents to run a tool, read off MCAgents'
 * {@code AIAccount} record by {@link MCAgentsToolAdapter}.
 *
 * <p>Only the two identifying fields are carried; the stored token and
 * timestamps are never copied out of MCAgents.</p>
 *
 * @param accountType The MCAgents account type, for example {@code player} or
 *                    {@code server}; may be {@code null} when it could not be read.
 * @param accountUuid The account's identifier within that type; may be {@code null}.
 */
public record ToolCaller(String accountType, String accountUuid) {

    /**
     * The MCAgents account type of an in-game player.
     */
    public static final String PLAYER_TYPE = "player";

    /**
     * Checks whether the caller claims to be a player with a well-formed UUID.
     * Whether that player is actually online is the port's question, not this one.
     *
     * @return {@code true} when the type is {@code player} and the UUID parses.
     */
    public boolean isPlayer() {
        return PLAYER_TYPE.equalsIgnoreCase(this.accountType) && playerUuid().isPresent();
    }

    /**
     * Parses the caller's UUID when the caller is a player.
     *
     * @return The player UUID, or empty for a non-player or an unparsable UUID.
     */
    public Optional<UUID> playerUuid() {
        if (!PLAYER_TYPE.equalsIgnoreCase(this.accountType) || this.accountUuid == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(this.accountUuid.trim()));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
