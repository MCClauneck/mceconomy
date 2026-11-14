package io.github.mcclauneck.mceconomy.bukkit.commands;

import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Wires the {@code /balance} and {@code /pay} executors declared in
 * {@code plugin.yml}.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    /**
     * @param plugin           The plugin whose {@code plugin.yml} declares the commands.
     * @param provider         The central economy provider.
     * @param accounts         The account binding.
     * @param currencyTextures Base64 head textures keyed by currency system code.
     * @param logger           The plugin logger for error reporting.
     */
    public static void register(JavaPlugin plugin, MCEconomyProvider provider, AccountSource accounts,
                                Map<String, String> currencyTextures, Logger logger) {
        PluginCommand balance = plugin.getCommand("balance");
        if (balance != null) {
            balance.setExecutor(new BalanceCommand(provider, accounts, currencyTextures, logger));
        }
        PluginCommand pay = plugin.getCommand("pay");
        if (pay != null) {
            pay.setExecutor(new PayCommand(provider, accounts, logger));
            pay.setTabCompleter(new PayTabCompleter(provider));
        }
    }
}
