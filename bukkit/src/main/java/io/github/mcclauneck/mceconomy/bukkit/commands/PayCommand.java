package io.github.mcclauneck.mceconomy.bukkit.commands;

import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.bukkit.event.EconomyTransactionEvent;
import io.github.mcclauneck.mceconomy.bukkit.event.EconomyWalletUpdateEvent;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.api.host.AccountSource;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /pay <player> <currency> <amount>} — moves money from the sender's
 * account to the target player's account.
 * <p>
 * The transfer is
 * atomic and fully logged: the shared provider writes a {@code send} log row
 * for the paying account, a {@code receive} row for the receiving account,
 * and a transaction receipt
 * row keyed by a fresh transaction UUID. Non-transferable currencies are
 * rejected before any balance is touched, and the
 * {@link EconomyTransactionEvent} veto convention is honored for both sides.
 * <p>
 * Requires {@link CommandPermissions#PAY}, which every player has by default.
 */
public class PayCommand implements CommandExecutor {

    /** Reference identifiers stored with each pay-command log row. */
    private static final String REFERENCE_TYPE = "player_transfer";
    private static final String REFERENCE_ID = "pay_command";

    private final MCEconomyProvider provider;

    /**
     * The account binding used to resolve both sides' accounts.
     */
    private final AccountSource accounts;

    /**
     * Logger used to report unexpected transfer failures.
     */
    private final Logger logger;

    /**
     * Constructs the command.
     * @param provider The central economy provider.
     * @param accounts The account binding, or {@code null} when unavailable.
     * @param logger The plugin logger for error reporting.
     */
    public PayCommand(MCEconomyProvider provider, AccountSource accounts, Logger logger) {
        this.provider = provider;
        this.accounts = accounts;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(CommandPermissions.PAY)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can pay another player.");
            return true;
        }
        if (args.length != 3) {
            player.sendMessage(ChatColor.RED + "Usage: /" + label + " <player> <currency> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player " + args[0] + " is not online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot pay yourself.");
            return true;
        }

        Currency currency = provider.getCurrency(args[1]);
        if (currency == null) {
            player.sendMessage(ChatColor.RED + "Unknown currency: " + args[1]);
            return true;
        }
        if (!currency.transferable()) {
            player.sendMessage(ChatColor.RED + currency.displayName() + " cannot be transferred.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            amount = -1;
        }
        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "The amount must be a positive whole number.");
            return true;
        }

        if (this.accounts == null || !this.accounts.isReady()) {
            player.sendMessage(ChatColor.RED + "The economy is not ready yet. Try again in a moment.");
            return true;
        }

        // Payments move between the two players' own accounts.
        long finalAmount = amount;
        AccountSource source = this.accounts;
        source.accountUuid(player.getUniqueId())
                .thenCombine(source.accountUuid(target.getUniqueId()),
                        (senderAccount, targetAccount) -> {
                            pay(player, target, currency, finalAmount, source.accountType(),
                                    senderAccount, targetAccount);
                            return null;
                        })
                .exceptionally(throwable -> {
                    this.logger.log(Level.SEVERE, "Failed to resolve accounts for /pay", throwable);
                    player.sendMessage(ChatColor.RED + "The payment failed. Try again later.");
                    return null;
                });
        return true;
    }

    /**
     * Validates both accounts, honors the transaction veto events, and runs
     * the logged transfer. An empty side means that player has no account
     * resolved yet.
     */
    private void pay(Player player, Player target, Currency currency, long amount, String accountType,
                     Optional<UUID> senderAccount, Optional<UUID> targetAccount) {
        if (senderAccount.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You have no economy account yet. Try again in a moment.");
            return;
        }
        if (targetAccount.isEmpty()) {
            player.sendMessage(ChatColor.RED + target.getName() + " has no economy account to receive the payment.");
            return;
        }
        UUID from = senderAccount.get();
        UUID to = targetAccount.get();

        // Honor the shared veto convention for both the debit and the credit.
        EconomyTransactionEvent debit = new EconomyTransactionEvent(
                accountType, from, currency.systemCode(), amount, LogScope.SEND);
        Bukkit.getPluginManager().callEvent(debit);
        EconomyTransactionEvent credit = new EconomyTransactionEvent(
                accountType, to, currency.systemCode(), amount, LogScope.RECEIVE);
        Bukkit.getPluginManager().callEvent(credit);
        if (debit.isCancelled() || credit.isCancelled()) {
            player.sendMessage(ChatColor.RED + "The payment was blocked by another plugin.");
            return;
        }

        UUID transactionUuid = UUID.randomUUID();
        provider.transferBalance(
                        accountType, from,
                        accountType, to,
                        currency, amount, transactionUuid, REFERENCE_TYPE, REFERENCE_ID)
                .thenAccept(success -> {
                    announceNewBalance(accountType, from, currency);
                    announceNewBalance(accountType, to, currency);
                    String formatted = String.format("%,d", amount) + " " + currency.displayName();
                    player.sendMessage(ChatColor.GREEN + "You paid " + formatted + " to " + target.getName() + ".");
                    target.sendMessage(ChatColor.GREEN + "You received " + formatted + " from " + player.getName() + ".");
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
                    if (cause instanceof IllegalStateException) {
                        player.sendMessage(ChatColor.RED + "You do not have enough " + currency.displayName() + ".");
                    } else {
                        this.logger.log(Level.SEVERE, "Failed to transfer " + amount + " " + currency.systemCode()
                                + " from " + accountType + " " + from + " to " + accountType + " " + to, throwable);
                        player.sendMessage(ChatColor.RED + "The payment failed. Try again later.");
                    }
                    return null;
                });
    }

    /**
     * Fires the {@link EconomyWalletUpdateEvent} notification with the
     * account's fresh balance, so other plugins can react to the payment.
     */
    private void announceNewBalance(String accountType, UUID accountUuid, Currency currency) {
        provider.getBalance(accountType, accountUuid, currency).thenAccept(balance ->
                Bukkit.getPluginManager().callEvent(new EconomyWalletUpdateEvent(
                        accountType, accountUuid, currency.systemCode(), balance)));
    }
}
