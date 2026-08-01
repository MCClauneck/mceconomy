package io.github.mcclauneck.mceconomy.bukkit.commands;

import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.bukkit.gui.BalanceGui;
import io.github.mcclauneck.mceconomy.bukkit.gui.CurrencyBalanceEntry;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.api.host.IdentitySource;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /balance} — opens the paged balance GUI for the sender's <em>active
 * identity</em>. No permission is required.
 * <p>
 * Balances belong to identities, never to the raw player, so the command first
 * resolves the active MCIdentity identity and then reads one wallet per cached
 * currency asynchronously before opening the GUI on the player's thread.
 */
public class BalanceCommand implements CommandExecutor {

    private final MCEconomyProvider provider;

    /**
     * Base64 head textures per currency system code, from {@code config.yml}.
     */
    private final Map<String, String> currencyTextures;

    /**
     * The reflective MCIdentity binding used to resolve the active identity.
     */
    private final IdentitySource identities;

    /**
     * Logger used to report failures while assembling the GUI data.
     */
    private final Logger logger;

    /**
     * Constructs the command.
     * @param provider The central economy provider.
     * @param identities The MCIdentity binding, or {@code null} when unavailable.
     * @param currencyTextures Base64 head textures keyed by currency system code.
     * @param logger The plugin logger for error reporting.
     */
    public BalanceCommand(MCEconomyProvider provider, IdentitySource identities,
                          Map<String, String> currencyTextures, Logger logger) {
        this.provider = provider;
        this.identities = identities;
        this.currencyTextures = currencyTextures;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the balance GUI.");
            return true;
        }
        if (this.identities == null || !this.identities.isAvailable()) {
            player.sendMessage(ChatColor.RED + "The identity system is not ready yet. Try again in a moment.");
            return true;
        }

        this.identities.activeIdentity(player.getUniqueId())
                .thenAccept(optIdentity -> optIdentity.ifPresentOrElse(
                        identityUuid -> openFor(player, identityUuid),
                        () -> player.sendMessage(ChatColor.RED
                                + "You have no active identity. Balances belong to your active identity, so equip one first.")))
                .exceptionally(throwable -> {
                    this.logger.log(Level.SEVERE, "Failed to resolve the active identity for /balance", throwable);
                    player.sendMessage(ChatColor.RED + "Could not load your balance. Try again later.");
                    return null;
                });
        return true;
    }

    /**
     * Reads every currency balance of the identity and opens the GUI.
     */
    private void openFor(Player player, UUID identityUuid) {
        List<Currency> currencies = orderedCurrencies();
        List<CompletableFuture<CurrencyBalanceEntry>> rows = new ArrayList<>(currencies.size());
        for (Currency currency : currencies) {
            rows.add(provider.getBalance(AccountTypes.IDENTITY, identityUuid, currency)
                    .thenApply(balance -> new CurrencyBalanceEntry(
                            currency.systemCode(),
                            currency.displayName(),
                            currency.transferable(),
                            currencyTextures.get(currency.systemCode().toLowerCase()),
                            balance)));
        }

        CompletableFuture.allOf(rows.toArray(new CompletableFuture[0]))
                .thenAccept(ignored -> {
                    List<CurrencyBalanceEntry> entries = rows.stream().map(CompletableFuture::join).toList();
                    BalanceGui.open(player, entries, 0);
                })
                .exceptionally(throwable -> {
                    this.logger.log(Level.SEVERE, "Failed to load balances for identity " + identityUuid, throwable);
                    player.sendMessage(ChatColor.RED + "Could not load your balance. Try again later.");
                    return null;
                });
    }

    /**
     * Returns the cached currencies in a stable display order: the configured
     * (managed) currencies first, in configuration order, then any remaining
     * database currencies sorted by system code.
     */
    private List<Currency> orderedCurrencies() {
        Map<String, Currency> remaining = new LinkedHashMap<>();
        provider.getCurrencies().stream()
                .sorted((a, b) -> a.systemCode().compareToIgnoreCase(b.systemCode()))
                .forEach(currency -> remaining.put(currency.systemCode().toLowerCase(), currency));

        List<Currency> ordered = new ArrayList<>(remaining.size());
        for (String code : provider.getManagedCurrencies().keySet()) {
            Currency currency = remaining.remove(code.toLowerCase());
            if (currency != null) {
                ordered.add(currency);
            }
        }
        ordered.addAll(remaining.values());
        return ordered;
    }
}
