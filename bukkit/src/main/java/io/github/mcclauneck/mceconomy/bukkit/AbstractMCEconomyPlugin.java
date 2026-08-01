package io.github.mcclauneck.mceconomy.bukkit;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.api.host.IdentitySource;
import io.github.mcclauneck.mceconomy.api.models.CurrencyDefinition;
import io.github.mcclauneck.mceconomy.bukkit.commands.BalanceCommand;
import io.github.mcclauneck.mceconomy.bukkit.commands.PayCommand;
import io.github.mcclauneck.mceconomy.bukkit.commands.PayTabCompleter;
import io.github.mcclauneck.mceconomy.bukkit.config.CurrencyConfig;
import io.github.mcclauneck.mceconomy.bukkit.gui.BalanceGuiListener;
import io.github.mcclauneck.mceconomy.bukkit.host.MCIdentityBridge;
import io.github.mcclauneck.mceconomy.bukkit.listener.IdentityAccountListener;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.PlatformScheduler;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.Schedulers;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import io.github.mcclauneck.mceconomy.common.central.MCEconomyAPI;
import io.github.mcclauneck.mceconomy.common.database.mongodb.MCEconomyMongoDB;
import io.github.mcclauneck.mceconomy.common.database.mysql.MCEconomyMySQL;
import io.github.mcclauneck.mceconomy.common.database.postgresql.MCEconomyPostgreSQL;
import io.github.mcclauneck.mceconomy.common.database.sqlite.MCEconomySQLite;
import io.github.mcclauneck.mceconomy.common.http.MCEconomyHttpClient;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared bootstrap for every platform entry point.
 * <p>
 * The enable/disable lifecycle, config loading, database wiring, and MCIdentity
 * integration are identical across SpigotMC, PaperMC, and Folia, so they live
 * here once. Each platform module extends this class with a thin concrete entry
 * point.
 * </p>
 * <p>
 * The economy binds accounts to MCIdentity identities only: the
 * {@link IdentityAccountListener} reacts to identity equip events and never to
 * raw player events, keeping every wallet keyed by the identity UUID.
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
     * Reflective binding to the MCIdentity plugin, resolved during
     * {@link #onEnable()}. Null when MCIdentity is absent or incompatible, in
     * which case identity-bound commands report that and do nothing.
     */
    private IdentitySource identities;

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

        IEconomyDatabase database = createDatabase(logger);

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

        // Bind to MCIdentity reflectively. The economy keys every wallet by identity
        // UUID, so a missing or incompatible identity plugin is fatal to trading —
        // but it is reported and degraded, not thrown, so the server still starts.
        MCIdentityBridge identityBridge = MCIdentityBridge.resolve(this).orElse(null);
        this.identities = identityBridge;
        if (identityBridge != null) {
            // Give every equipped MCIdentity identity its own "identity" economy account
            new IdentityAccountListener(this.provider, logger).register(this, identityBridge);
        }

        // The /balance GUI and /pay transfers, both bound to active identities
        getServer().getPluginManager().registerEvents(new BalanceGuiListener(), this);
        registerCommands(logger);

        logger.info("MCEconomy Plugin has been successfully enabled!");
    }

    /**
     * Wires the {@code /balance} and {@code /pay} executors declared in
     * {@code plugin.yml}.
     */
    private void registerCommands(Logger logger) {
        PluginCommand balance = getCommand("balance");
        if (balance != null) {
            balance.setExecutor(new BalanceCommand(this.provider, this.identities, this.currencyTextures, logger));
        }
        PluginCommand pay = getCommand("pay");
        if (pay != null) {
            pay.setExecutor(new PayCommand(this.provider, this.identities, logger));
            pay.setTabCompleter(new PayTabCompleter(this.provider));
        }
    }

    /**
     * Builds the storage backend selected by {@code database.type} in config.yml —
     * SQLite (the default), MySQL, PostgreSQL, MongoDB, or the central server.
     *
     * @param logger The plugin logger used to report the chosen backend.
     * @return The configured database.
     */
    private IEconomyDatabase createDatabase(Logger logger) {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();

        switch (dbType) {
            case "postgresql":
                logger.info("Initializing PostgreSQL Database...");
                return new MCEconomyPostgreSQL(
                    getConfig().getString("database.postgresql.host"),
                    getConfig().getInt("database.postgresql.port"),
                    getConfig().getString("database.postgresql.database"),
                    getConfig().getString("database.postgresql.username"),
                    getConfig().getString("database.postgresql.password")
                );
            case "mysql":
                logger.info("Initializing MySQL Database...");
                return new MCEconomyMySQL(
                    getConfig().getString("database.mysql.host"),
                    getConfig().getInt("database.mysql.port"),
                    getConfig().getString("database.mysql.database"),
                    getConfig().getString("database.mysql.username"),
                    getConfig().getString("database.mysql.password")
                );
            case "central":
                logger.info("Initializing Central Server Database...");
                return new MCEconomyAPI(
                    getConfig().getString("central_server.url"),
                    getConfig().getString("central_server.token"),
                    getConfig().getInt("central_server.timeout_seconds")
                );
            case "mongodb":
                logger.info("Initializing MongoDB Database...");
                return new MCEconomyMongoDB(
                    getConfig().getString("database.mongodb.uri"),
                    getConfig().getString("database.mongodb.database")
                );
            default:
            case "sqlite":
                logger.info("Initializing SQLite Database...");
                return new MCEconomySQLite(
                    new File(getDataFolder(), getConfig().getString("database.sqlite.file", "economy.db"))
                );
        }
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
     * Gets the active MCIdentity binding, which resolves the identity a player
     * currently has equipped.
     * @return The binding, or {@code null} when MCIdentity is unavailable.
     */
    public IdentitySource getIdentities() {
        return this.identities;
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
