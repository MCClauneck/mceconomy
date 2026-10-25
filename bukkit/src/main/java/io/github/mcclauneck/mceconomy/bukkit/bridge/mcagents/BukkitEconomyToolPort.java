package io.github.mcclauneck.mceconomy.bukkit.bridge.mcagents;

import io.github.mcclauneck.mceconomy.api.host.IdentitySource;
import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.mceconomy.bukkit.commands.BalanceCommand;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.tools.EconomyToolPort;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Serves the AI tools from the live plugin: the provider's currency cache and
 * asynchronous wallet reads, and the MCIdentity binding.
 *
 * <p>Nothing here touches a player entity — the online check is a lookup in
 * the server's player map and every balance read is the provider's own async
 * database call — so the tools are safe on Folia from any thread. The provider
 * and binding are read from the plugin on every call, so a disabled plugin
 * answers "not ready" instead of reading a closed database.</p>
 */
public final class BukkitEconomyToolPort implements EconomyToolPort {

    private final AbstractMCEconomyPlugin plugin;

    /**
     * @param plugin The MCEconomy plugin whose provider and identity binding are read.
     */
    public BukkitEconomyToolPort(AbstractMCEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEconomyReady() {
        return this.plugin.isEnabled() && this.plugin.getProvider() != null;
    }

    @Override
    public boolean isIdentityReady() {
        IdentitySource identities = this.plugin.getIdentities();
        return identities != null && identities.isAvailable();
    }

    @Override
    public boolean isOnlinePlayer(UUID playerUuid) {
        return Bukkit.getPlayer(playerUuid) != null;
    }

    @Override
    public CompletableFuture<Optional<UUID>> activeIdentity(UUID playerUuid) {
        IdentitySource identities = this.plugin.getIdentities();
        if (identities == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return identities.activeIdentity(playerUuid);
    }

    @Override
    public List<Currency> currencies() {
        MCEconomyProvider provider = this.plugin.getProvider();
        return provider == null ? List.of() : BalanceCommand.orderedCurrencies(provider);
    }

    @Override
    public CompletableFuture<Long> balance(UUID identityUuid, Currency currency) {
        MCEconomyProvider provider = this.plugin.getProvider();
        if (provider == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCEconomy is not enabled"));
        }
        return provider.getBalance(AccountTypes.IDENTITY, identityUuid, currency);
    }
}
