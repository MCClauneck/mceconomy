package io.github.mcclauneck.mceconomy.bukkit.listener;

import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import io.github.mcoriax.mcidentity.bukkit.event.MCIdentityEquipEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Listener that binds MCEconomy accounts to MCIdentity identities.
 * <p>
 * Each MCIdentity identity is a fully separate character, so its money must be
 * kept apart from every other identity the same player owns. Whenever a player
 * equips an identity this listener ensures an economy account of type
 * {@link AccountTypes#IDENTITY} exists for that <strong>identity UUID</strong>
 * (never the base player UUID). All wallets and logs for the identity then hang
 * off that account, keeping balances per character.
 * <p>
 * Account creation is idempotent, so re-equipping an identity is harmless. The
 * work runs asynchronously through the provider's database layer and never
 * blocks the server thread.
 */
public class IdentityAccountListener implements Listener {

    /**
     * The central economy provider used to create identity accounts.
     */
    private final MCEconomyProvider provider;

    /**
     * Logger used to report failures while ensuring identity accounts.
     */
    private final Logger logger;

    /**
     * Constructs the listener.
     * @param provider The central economy provider.
     * @param logger The plugin logger for error reporting.
     */
    public IdentityAccountListener(MCEconomyProvider provider, Logger logger) {
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * Ensures the equipped identity has an economy account of type
     * {@link AccountTypes#IDENTITY}, keyed by the identity UUID.
     * @param event The MCIdentity equip event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIdentityEquip(MCIdentityEquipEvent event) {
        UUID identityUuid = event.getIdentityUuid();
        this.provider.createAccount(AccountTypes.IDENTITY, identityUuid).exceptionally(throwable -> {
            this.logger.log(Level.SEVERE,
                    "Failed to ensure the identity economy account exists for identity " + identityUuid, throwable);
            return null;
        });
    }
}
