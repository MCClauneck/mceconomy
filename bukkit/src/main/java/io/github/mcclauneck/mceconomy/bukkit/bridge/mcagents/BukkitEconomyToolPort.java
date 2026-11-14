package io.github.mcclauneck.mceconomy.bukkit.bridge.mcagents;

import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.tools.EconomyToolPort;

import org.bukkit.Bukkit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Serves the AI tools from the live plugin: the provider's currency cache and
 * asynchronous wallet reads, and the account binding (the player's own
 * account).
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
     * @param plugin The MCEconomy plugin whose provider and account binding are read.
     */
    public BukkitEconomyToolPort(AbstractMCEconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isEconomyReady() {
        return this.plugin.isEnabled() && this.plugin.getProvider() != null;
    }

    @Override
    public boolean isAccountReady() {
        AccountSource accounts = this.plugin.getAccounts();
        return accounts != null && accounts.isReady();
    }

    @Override
    public boolean isOnlinePlayer(UUID playerUuid) {
        return Bukkit.getPlayer(playerUuid) != null;
    }

    @Override
    public CompletableFuture<Optional<UUID>> accountUuid(UUID playerUuid) {
        AccountSource accounts = this.plugin.getAccounts();
        if (accounts == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return accounts.accountUuid(playerUuid);
    }

    @Override
    public List<Currency> currencies() {
        MCEconomyProvider provider = this.plugin.getProvider();
        return provider == null ? List.of() : provider.getCurrenciesInDisplayOrder();
    }

    @Override
    public CompletableFuture<Long> balance(UUID accountUuid, Currency currency) {
        MCEconomyProvider provider = this.plugin.getProvider();
        AccountSource accounts = this.plugin.getAccounts();
        if (provider == null || accounts == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCEconomy is not enabled"));
        }
        return provider.getBalance(accounts.accountType(), accountUuid, currency);
    }
}
