package io.github.mcclauneck.mceconomy.bukkit.commands;

import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Tab completion for {@code /pay <player> <currency> <amount>}: online player
 * names, then transferable currency codes, then a few example amounts.
 * Senders without {@link CommandPermissions#PAY} are offered nothing.
 */
public class PayTabCompleter implements TabCompleter {

    private final MCEconomyProvider provider;

    /**
     * Constructs the completer.
     * @param provider The central economy provider, used to list currencies.
     */
    public PayTabCompleter(MCEconomyProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(CommandPermissions.PAY)) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return provider.getCurrencies().stream()
                    .filter(Currency::transferable)
                    .map(currency -> currency.systemCode().toLowerCase(Locale.ROOT))
                    .filter(code -> code.startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if (args.length == 3) {
            return List.of("1", "10", "100");
        }
        return List.of();
    }
}
