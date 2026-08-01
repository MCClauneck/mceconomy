package io.github.mcclauneck.mceconomy.bukkit.listener;

import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.bukkit.host.MCIdentityBridge;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Binds MCEconomy accounts to MCIdentity identities.
 * <p>
 * Each MCIdentity identity is a fully separate character, so its money must be
 * kept apart from every other identity the same player owns. Whenever a player
 * equips an identity this handler ensures an economy account of type
 * {@link AccountTypes#IDENTITY} exists for that <strong>identity UUID</strong>
 * (never the base player UUID). All wallets and logs for the identity then hang
 * off that account, keeping balances per character.
 * <p>
 * The equip event belongs to MCIdentity, which this plugin does not compile
 * against, so the subscription is installed through
 * {@link MCIdentityBridge#registerEquipListener} rather than an
 * {@code @EventHandler} method — the event type is only known at runtime.
 * <p>
 * Account creation is idempotent, so re-equipping an identity is harmless. The
 * work runs asynchronously through the provider's database layer and never
 * blocks the server thread.
 */
public final class IdentityAccountListener {

    /**
     * The central economy provider used to create identity accounts.
     */
    private final MCEconomyProvider provider;

    /**
     * Logger used to report failures while ensuring identity accounts.
     */
    private final Logger logger;

    /**
     * Constructs the handler.
     * @param provider The central economy provider.
     * @param logger The plugin logger for error reporting.
     */
    public IdentityAccountListener(MCEconomyProvider provider, Logger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * Subscribes to MCIdentity's equip event through the given bridge.
     *
     * @param plugin   The owning plugin, registered as the handler's owner.
     * @param identity The resolved MCIdentity binding.
     * @return {@code true} when the subscription was installed.
     */
    public boolean register(Plugin plugin, MCIdentityBridge identity) {
        return identity.registerEquipListener(plugin, this::onIdentityEquip);
    }

    /**
     * Ensures the equipped identity has an economy account of type
     * {@link AccountTypes#IDENTITY}, keyed by the identity UUID.
     * @param identityUuid The UUID of the identity that was equipped.
     */
    private void onIdentityEquip(UUID identityUuid) {
        this.provider.createAccount(AccountTypes.IDENTITY, identityUuid).exceptionally(throwable -> {
            this.logger.log(Level.SEVERE,
                    "Failed to ensure the identity economy account exists for identity " + identityUuid, throwable);
            return null;
        });
    }
}
