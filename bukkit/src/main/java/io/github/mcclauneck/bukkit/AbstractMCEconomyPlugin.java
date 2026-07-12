package io.github.mcclauneck.bukkit;

import io.github.mcclauneck.api.database.IEconomyDatabase;
import io.github.mcclauneck.api.models.CurrencyDefinition;
import io.github.mcclauneck.bukkit.listener.IdentityAccountListener;
import io.github.mcclauneck.common.MCEconomyProvider;
import io.github.mcclauneck.common.database.api.MCEconomyAPI;
import io.github.mcclauneck.common.database.mongodb.MCEconomyMongoDB;
import io.github.mcclauneck.common.database.mysql.MCEconomyMySQL;
import io.github.mcclauneck.common.database.postgresql.MCEconomyPostgreSQL;
import io.github.mcclauneck.common.database.sqlite.MCEconomySQLite;
import io.github.mcclauneck.common.http.MCEconomyHttpClient;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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

    @Override
    public void onEnable() {
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

        // Read the configurable currency list ("code,Display Name,transferable") from config.yml
        Map<String, CurrencyDefinition> currencies = MCEconomyProvider.parseCurrencyList(getConfig().getStringList("currencies"));

        // Initialize Central Economy Provider with the configured currencies
        this.provider = new MCEconomyProvider(database, currencies);

        // Pre-load all currencies into cache
        logger.info("Loading currencies into cache...");
        this.provider.loadCurrencies().join();

        // Generate the configured currencies on first run (creates any that are missing)
        logger.info("Ensuring configured currencies exist...");
        this.provider.ensureDefaultCurrencies().join();

        // Give every equipped MCIdentity identity its own "identity" economy account
        getServer().getPluginManager().registerEvents(new IdentityAccountListener(this.provider, logger), this);

        logger.info("MCEconomy Plugin has been successfully enabled!");
    }

    /**
     * Builds the storage backend selected by {@code database.type} in config.yml —
     * SQLite (the default), MySQL, PostgreSQL, MongoDB, or the remote central API.
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
            case "api":
                logger.info("Initializing Remote API Database...");
                return new MCEconomyAPI(
                    getConfig().getString("database.api.url"),
                    getConfig().getString("database.api.token")
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
