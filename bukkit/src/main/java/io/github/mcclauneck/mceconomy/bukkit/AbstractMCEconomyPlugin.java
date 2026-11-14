package io.github.mcclauneck.mceconomy.bukkit;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.host.AccountSource;
import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;
import io.github.mcclauneck.mceconomy.bukkit.bridge.mcagents.BukkitEconomyToolPort;
import io.github.mcclauneck.mceconomy.bukkit.bridge.mcagents.MCAgentsToolRegistrar;
import io.github.mcclauneck.mceconomy.bukkit.commands.CommandRegistrar;
import io.github.mcclauneck.mceconomy.bukkit.config.AgentsSettings;
import io.github.mcclauneck.mceconomy.bukkit.config.CurrencyConfig;
import io.github.mcclauneck.mceconomy.bukkit.config.MCAgentsGlobal;
import io.github.mcclauneck.mceconomy.bukkit.database.EconomyDatabaseFactory;
import io.github.mcclauneck.mceconomy.bukkit.gui.BalanceGuiListener;
import io.github.mcclauneck.mceconomy.bukkit.host.AccountBinding;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.PlatformScheduler;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.Schedulers;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.common.http.MCEconomyHttpClient;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared bootstrap for every platform entry point.
 * <p>
 * The enable/disable lifecycle, config loading, database wiring, and account
 * binding are identical across SpigotMC, PaperMC, and Folia, so they live
 * here once. Each platform module extends this class with a thin concrete entry
 * point.
 * </p>
 * <p>
 * The bootstrap only sequences the steps; each step lives in its own class:
 * {@link EconomyDatabaseFactory} picks the backend, {@link CurrencyConfig}
 * reads the currencies, {@link AccountBinding} binds every wallet to its
 * player, and {@link CommandRegistrar} wires the commands. The commands and
 * tools read the account through {@link AccountSource}.
 * </p>
 */
public abstract class AbstractMCEconomyPlugin extends JavaPlugin {

    /**
     * Provider serving as the central API and entry point for economy workflows and queries.
     */
    private MCEconomyProvider provider;

    /**
     * Base64 head textures per currency system code, from the {@code currencies}
     * section of {@code config.yml}. Used by the {@code /balance} GUI.
     */
    private final Map<String, String> currencyTextures = new HashMap<>();

    /**
     * The account binding resolved during {@link #onEnable()}: every wallet
     * belongs to the player. Null until the plugin is enabled.
     */
    private AccountSource accounts;

    /**
     * The {@code agents} block of {@code config.yml}: the AI model and
     * OpenRouter API key this plugin's agent work runs on, each {@code global}
     * by default to share MCAgents' own. Read during {@link #onEnable()}.
     */
    private AgentsSettings agentsSettings = new AgentsSettings(AgentsSettings.GLOBAL, null);

    /**
     * Supplies the platform specific scheduler installed during {@link #onEnable()}.
     * @return the scheduler implementation for this platform.
     */
    protected abstract PlatformScheduler createScheduler();

    @Override
    public void onEnable() {
        Schedulers.set(createScheduler());

        Logger logger = getLogger();

        // Load default config
        saveDefaultConfig();
        if (!getConfig().getBoolean("enable", true)) {
            logger.warning("Plugin is set to 'enable: false' in config.yml. Shutting down MCEconomy...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // The AI model and key: 'global' defers to MCAgents' own agents.{modality} / agents.token
        this.agentsSettings = AgentsSettings.read(getConfig());
        logger.info("AI agent model: " + (this.agentsSettings.globalModel(AgentsSettings.Modality.TEXT)
            ? "global (MCAgents)" : this.agentsSettings.model(AgentsSettings.Modality.TEXT)));

        IEconomyDatabase database = EconomyDatabaseFactory.create(getConfig(), getDataFolder(), logger);

        // Ensure database tables are fully created before proceeding
        database.initialize().join();

        // Read the configured currencies (and their GUI head textures) from config.yml
        Map<String, CurrencyDefinition> currencies = CurrencyConfig.parseDefinitions(getConfig());
        this.currencyTextures.putAll(CurrencyConfig.parseTextures(getConfig()));

        // Initialize Central Economy Provider with the configured currencies
        this.provider = new MCEconomyProvider(database, currencies);

        // Pre-load all currencies into cache
        logger.info("Loading currencies into cache...");
        this.provider.loadCurrencies().join();

        // Generate the configured currencies on first run (creates any that are missing)
        logger.info("Ensuring configured currencies exist...");
        this.provider.ensureDefaultCurrencies().join();

        // Every wallet belongs to the player, keyed by the player UUID
        this.accounts = AccountBinding.bind(this, this.provider, logger).accounts();

        // The /balance GUI and /pay transfers, both between player accounts
        getServer().getPluginManager().registerEvents(new BalanceGuiListener(), this);
        CommandRegistrar.register(this, this.provider, this.accounts, this.currencyTextures, logger);

        // Optional: expose read-only balance tools to MCAgents when it is installed
        MCAgentsToolRegistrar.register(new BukkitEconomyToolPort(this), logger);

        logger.info("MCEconomy Plugin has been successfully enabled!");
    }

    @Override
    public void onDisable() {
        // Safely shutdown the database provider
        if (this.provider != null) {
            getLogger().info("Shutting down database connection...");
            this.provider.shutdown().join();
        }

        getLogger().info("MCEconomy Plugin has been disabled!");
    }

    /**
     * Gets the MCEconomyProvider instance which serves as the central API for economy features.
     * @return The active MCEconomyProvider.
     */
    public MCEconomyProvider getProvider() {
        return this.provider;
    }

    /**
     * Gets the account binding money is keyed by: the player.
     * @return The binding, or {@code null} before the plugin is enabled.
     */
    public AccountSource getAccounts() {
        return this.accounts;
    }

    /**
     * Gets the {@code agents} block as configured, before {@code global} is
     * resolved.
     * @return The settings; both values {@code global} until the plugin enables.
     */
    public AgentsSettings getAgentsSettings() {
        return this.agentsSettings;
    }

    /**
     * Gets the AI model this plugin's agent work runs on: {@code agents.text},
     * or MCAgents' own model when it is {@code global}.
     * @return The model id, blank when it is global and MCAgents has none.
     */
    public String getAgentsModel() {
        return this.agentsSettings.resolveModel(AgentsSettings.Modality.TEXT, new MCAgentsGlobal());
    }

    /**
     * Gets the OpenRouter API key this plugin's agent work runs on:
     * {@code agents.token}, or MCAgents' own key when it is {@code global}.
     * Never log or display the result.
     * @return The key, blank when it is global and MCAgents has none.
     */
    public String getAgentsToken() {
        return this.agentsSettings.resolveToken(new MCAgentsGlobal());
    }

    /**
     * Gets the shared {@link java.net.http.HttpClient} used by the plugin for
     * remote API calls.
     * <p>
     * Reusing this shared client instead of opening a new one lets the whole
     * plugin share a single connection pool.
     * </p>
     *
     * @return The shared HTTP client.
     */
    public java.net.http.HttpClient getHttpClient() {
        return MCEconomyHttpClient.shared();
    }
}
