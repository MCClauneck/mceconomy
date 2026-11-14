package io.github.mcclauneck.mceconomy.bukkit.host;

import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.bukkit.listener.PlayerAccountListener;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Which wallets money belongs to on this server, decided once at enable time.
 * <p>
 * Every wallet belongs to the player: account type
 * {@link io.github.mcclauneck.mceconomy.api.models.AccountTypes#PLAYER} and the
 * player UUID. Since MCIdentity 5.0.0 a player has one character whatever
 * profession they wear, so the economy does not depend on MCIdentity at all.
 * The {@link PlayerAccountListener} keeps every player's account present.
 *
 * @param accounts The account binding, never {@code null}.
 */
public record AccountBinding(AccountSource accounts) {

    /**
     * Binds wallets to players and registers the account listener.
     *
     * @param plugin   The MCEconomy plugin the listener is registered for.
     * @param provider The provider the listener creates accounts through.
     * @param logger   The plugin logger.
     * @return The resolved binding.
     */
    public static AccountBinding bind(Plugin plugin, MCEconomyProvider provider, Logger logger) {
        new PlayerAccountListener(provider, logger).register(plugin);
        return new AccountBinding(AccountSource.players());
    }
}
