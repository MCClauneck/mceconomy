package io.github.mcclauneck.mceconomy.bukkit.commands;

import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.bukkit.gui.BalanceGui;
import io.github.mcclauneck.mceconomy.bukkit.gui.CurrencyBalanceEntry;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.api.host.AccountSource;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /balance} — opens the paged balance GUI for the sender's own account.
 * Requires
 * {@link CommandPermissions#BALANCE}, which every player has by default.
 * <p>
 * The command first resolves the account through the {@link AccountSource} and
 * then reads one wallet per cached currency asynchronously before opening the
 * GUI on the player's thread.
 */
public class BalanceCommand implements CommandExecutor {

    private final MCEconomyProvider provider;

    /**
     * Base64 head textures per currency system code, from {@code config.yml}.
     */
    private final Map<String, String> currencyTextures;

    /**
     * The account binding used to resolve the sender's account.
     */
    private final AccountSource accounts;

    /**
     * Logger used to report failures while assembling the GUI data.
     */
    private final Logger logger;

    /**
     * Constructs the command.
     * @param provider The central economy provider.
     * @param accounts The account binding, or {@code null} when unavailable.
     * @param currencyTextures Base64 head textures keyed by currency system code.
     * @param logger The plugin logger for error reporting.
     */
    public BalanceCommand(MCEconomyProvider provider, AccountSource accounts,
                          Map<String, String> currencyTextures, Logger logger) {
        this.provider = provider;
        this.accounts = accounts;
        this.currencyTextures = currencyTextures;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CommandPermissions.BALANCE)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can open the balance GUI.");
            return true;
        }
        if (this.accounts == null || !this.accounts.isReady()) {
            player.sendMessage(ChatColor.RED + "The economy is not ready yet. Try again in a moment.");
            return true;
        }

        AccountSource source = this.accounts;
        source.accountUuid(player.getUniqueId())
                .thenAccept(optAccount -> optAccount.ifPresentOrElse(
                        accountUuid -> openFor(player, source.accountType(), accountUuid),
                        () -> player.sendMessage(ChatColor.RED
                                + "You have no economy account yet. Try again in a moment.")))
                .exceptionally(throwable -> {
                    this.logger.log(Level.SEVERE, "Failed to resolve the account for /balance", throwable);
                    player.sendMessage(ChatColor.RED + "Could not load your balance. Try again later.");
                    return null;
                });
        return true;
    }

    /**
     * Reads every currency balance of the account and opens the GUI.
     */
    private void openFor(Player player, String accountType, UUID accountUuid) {
        List<Currency> currencies = this.provider.getCurrenciesInDisplayOrder();
        List<CompletableFuture<CurrencyBalanceEntry>> rows = new ArrayList<>(currencies.size());
        for (Currency currency : currencies) {
            rows.add(provider.getBalance(accountType, accountUuid, currency)
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
                    this.logger.log(Level.SEVERE, "Failed to load balances for " + accountType + " " + accountUuid, throwable);
                    player.sendMessage(ChatColor.RED + "Could not load your balance. Try again later.");
                    return null;
                });
    }

    /**
     * Returns the cached currencies in a stable display order: the configured
     * (managed) currencies first, in configuration order, then any remaining
     * database currencies sorted by system code. Kept for callers that used it
     * before {@link MCEconomyProvider#getCurrenciesInDisplayOrder()} existed.
     *
     * @param provider The economy provider whose cache is read.
     * @return The currencies in display order.
     */
    public static List<Currency> orderedCurrencies(MCEconomyProvider provider) {
        return provider.getCurrenciesInDisplayOrder();
    }
}
