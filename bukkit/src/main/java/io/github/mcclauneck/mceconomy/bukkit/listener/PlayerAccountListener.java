package io.github.mcclauneck.mceconomy.bukkit.listener;

import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds MCEconomy accounts to players.
 * <p>
 * A player's money belongs to the player, whichever MCIdentity profession they
 * wear, so on
 * every join this handler ensures an economy account of type
 * {@link AccountTypes#PLAYER} exists for the <strong>player UUID</strong>.
 * Players already online when the plugin enables (a reload) are covered by
 * {@link #register(Plugin)}.
 * <p>
 * Account creation is idempotent and runs asynchronously through the
 * provider's database layer, so it never blocks the server or region thread.
 */
public final class PlayerAccountListener implements Listener {

    /**
     * The central economy provider used to create player accounts.
     */
    private final MCEconomyProvider provider;

    /**
     * Logger used to report failures while ensuring player accounts.
     */
    private final Logger logger;

    /**
     * Constructs the handler.
     * @param provider The central economy provider.
     * @param logger The plugin logger for error reporting.
     */
    public PlayerAccountListener(MCEconomyProvider provider, Logger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * Registers the join handler and ensures an account for every player who is
     * already online.
     *
     * @param plugin The owning plugin, registered as the handler's owner.
     */
    public void register(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ensureAccount(player.getUniqueId());
        }
    }

    /**
     * Ensures the joining player has an economy account.
     * @param event The join event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        ensureAccount(event.getPlayer().getUniqueId());
    }

    private void ensureAccount(UUID playerUuid) {
        this.provider.createAccount(AccountTypes.PLAYER, playerUuid).exceptionally(throwable -> {
            this.logger.log(Level.SEVERE,
                    "Failed to ensure the player economy account exists for player " + playerUuid, throwable);
            return null;
        });
    }
}
