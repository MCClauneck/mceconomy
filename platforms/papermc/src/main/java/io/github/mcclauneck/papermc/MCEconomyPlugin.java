package io.github.mcclauneck.papermc;

import io.github.mcclauneck.api.database.IEconomyDatabase;
import io.github.mcclauneck.bukkit.listener.IdentityAccountListener;
import io.github.mcclauneck.bukkit.listener.PlayerJoinCurrencyListener;
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
 * Main plugin class for MCEconomy on Bukkit/PaperMC.
 * It integrates the central MCEconomyProvider.
 */
public class MCEconomyPlugin extends JavaPlugin {

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

        IEconomyDatabase database;
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();

        switch (dbType) {
            case "postgresql":
                logger.info("Initializing PostgreSQL Database...");
                database = new MCEconomyPostgreSQL(
                    getConfig().getString("database.postgresql.host"),
                    getConfig().getInt("database.postgresql.port"),
                    getConfig().getString("database.postgresql.database"),
                    getConfig().getString("database.postgresql.username"),
                    getConfig().getString("database.postgresql.password")
                );
                break;
            case "mysql":
                logger.info("Initializing MySQL Database...");
                database = new MCEconomyMySQL(
                    getConfig().getString("database.mysql.host"),
                    getConfig().getInt("database.mysql.port"),
                    getConfig().getString("database.mysql.database"),
                    getConfig().getString("database.mysql.username"),
                    getConfig().getString("database.mysql.password")
                );
                break;
            case "api":
                logger.info("Initializing Remote API Database...");
                database = new MCEconomyAPI(
                    getConfig().getString("database.api.url"),
                    getConfig().getString("database.api.token")
                );
                break;
            case "mongodb":
                logger.info("Initializing MongoDB Database...");
                database = new MCEconomyMongoDB(
                    getConfig().getString("database.mongodb.uri"),
                    getConfig().getString("database.mongodb.database")
                );
                break;
            default:
            case "sqlite":
                logger.info("Initializing SQLite Database...");
                database = new MCEconomySQLite(
                    new File(getDataFolder(), getConfig().getString("database.sqlite.file", "economy.db"))
                );
                break;
        }

        // Ensure database tables are fully created before proceeding
        database.initialize().join();

        // Read the configurable currency list ("code,Display Name") from config.yml
        Map<String, String> currencies = MCEconomyProvider.parseCurrencyList(getConfig().getStringList("currencies"));

        // Initialize Central Economy Provider with the configured currencies
        this.provider = new MCEconomyProvider(database, currencies);

        // Pre-load all currencies into cache
        logger.info("Loading currencies into cache...");
        this.provider.loadCurrencies().join();

        // Generate the default currencies on first run (creates any that are missing)
        logger.info("Ensuring default currencies exist...");
        this.provider.ensureDefaultCurrencies().join();

        // Keep the default currencies present by re-checking whenever a player joins
        getServer().getPluginManager().registerEvents(new PlayerJoinCurrencyListener(this.provider, logger), this);

        // Give every equipped MCIdentity identity its own "identity" economy account
        getServer().getPluginManager().registerEvents(new IdentityAccountListener(this.provider, logger), this);

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
