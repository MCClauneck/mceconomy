package io.github.mcclauneck.mceconomy.bukkit.commands;

/**
 * The permission nodes MCEconomy's commands check, declared with their
 * descriptions and defaults in every platform {@code plugin.yml}.
 * <p>
 * Both commands only ever touch the sender's own account, so both nodes
 * default to {@code true}; they exist so a server owner can revoke a command
 * from a group. {@code mcclauneck.mceconomy.*} (default {@code op}) grants
 * every node at once.
 */
public final class CommandPermissions {

    /** {@code /balance} — open the sender's own balance GUI. Default {@code true}. */
    public static final String BALANCE = "mcclauneck.mceconomy.command.balance";

    /** {@code /pay} — pay another player from the sender's own account. Default {@code true}. */
    public static final String PAY = "mcclauneck.mceconomy.command.pay";

    private CommandPermissions() {
    }
}
