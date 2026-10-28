package io.github.mcclauneck.mceconomy.bukkit.host;

import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.api.host.IdentitySource;
import io.github.mcclauneck.mceconomy.bukkit.listener.IdentityAccountListener;
import io.github.mcclauneck.mceconomy.bukkit.listener.PlayerAccountListener;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Which wallets money belongs to on this server, decided once at enable time.
 * <p>
 * MCIdentity is optional. Installed and bound, accounts belong to identities
 * and the {@link IdentityAccountListener} keeps every equipped identity's
 * account present. Not installed, accounts belong to players and the
 * {@link PlayerAccountListener} does the same per player. Installed but
 * incompatible leaves both {@code null}, so the commands stay inert rather
 * than credit the wrong wallet.
 *
 * @param identities The MCIdentity binding, or {@code null} when it is absent or unbound.
 * @param accounts   The account binding, or {@code null} when MCIdentity is installed but unbound.
 */
public record AccountBinding(IdentitySource identities, AccountSource accounts) {

    /**
     * Resolves the binding and registers the matching account listener.
     *
     * @param plugin   The MCEconomy plugin the listener is registered for.
     * @param provider The provider the listener creates accounts through.
     * @param logger   The plugin logger.
     * @return The resolved binding.
     */
    public static AccountBinding bind(Plugin plugin, MCEconomyProvider provider, Logger logger) {
        Plugin identityHost = plugin.getServer().getPluginManager().getPlugin(MCIdentityBridge.PLUGIN_NAME);
        if (identityHost == null) {
            new PlayerAccountListener(provider, logger).register(plugin);
            logger.info(MCIdentityBridge.PLUGIN_NAME + " is not installed; wallets belong to players.");
            return new AccountBinding(null, AccountSource.players());
        }

        MCIdentityBridge identityBridge = MCIdentityBridge.resolve(plugin).orElse(null);
        if (identityBridge == null) {
            return new AccountBinding(null, null);
        }
        // Give every equipped MCIdentity identity its own "identity" economy account
        new IdentityAccountListener(provider, logger).register(plugin, identityBridge);
        logger.info("Found " + MCIdentityBridge.PLUGIN_NAME + "; wallets belong to active identities.");
        return new AccountBinding(identityBridge, AccountSource.identities(identityBridge));
    }
}
