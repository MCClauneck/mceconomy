package io.github.mcclauneck.mceconomy.bukkit.database;

import io.github.mcclauneck.mceconomy.api.database.IEconomyDatabase;
import io.github.mcclauneck.mceconomy.common.central.MCEconomyAPI;
import io.github.mcclauneck.mceconomy.common.database.mongodb.MCEconomyMongoDB;
import io.github.mcclauneck.mceconomy.common.database.mysql.MCEconomyMySQL;
import io.github.mcclauneck.mceconomy.common.database.postgresql.MCEconomyPostgreSQL;
import io.github.mcclauneck.mceconomy.common.database.sqlite.MCEconomySQLite;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * Builds the storage backend selected by {@code database.type} in
 * {@code config.yml} — SQLite (the default), MySQL, PostgreSQL, MongoDB, or
 * the central server.
 */
public final class EconomyDatabaseFactory {

    private EconomyDatabaseFactory() {
    }

    /**
     * @param config     The plugin's {@code config.yml}.
     * @param dataFolder The plugin's data folder, where the SQLite file lives.
     * @param logger     The plugin logger used to report the chosen backend.
     * @return The configured database, not yet initialized by the caller.
     */
    public static IEconomyDatabase create(FileConfiguration config, File dataFolder, Logger logger) {
        String dbType = config.getString("database.type", "sqlite").toLowerCase();

        switch (dbType) {
            case "postgresql":
                logger.info("Initializing PostgreSQL Database...");
                return new MCEconomyPostgreSQL(
                    config.getString("database.postgresql.host"),
                    config.getInt("database.postgresql.port"),
                    config.getString("database.postgresql.database"),
                    config.getString("database.postgresql.username"),
                    config.getString("database.postgresql.password")
                );
            case "mysql":
                logger.info("Initializing MySQL Database...");
                return new MCEconomyMySQL(
                    config.getString("database.mysql.host"),
                    config.getInt("database.mysql.port"),
                    config.getString("database.mysql.database"),
                    config.getString("database.mysql.username"),
                    config.getString("database.mysql.password")
                );
            case "central":
                logger.info("Initializing Central Server Database...");
                return new MCEconomyAPI(
                    config.getString("central_server.url"),
                    config.getString("central_server.token"),
                    config.getInt("central_server.timeout_seconds")
                );
            case "mongodb":
                logger.info("Initializing MongoDB Database...");
                return new MCEconomyMongoDB(
                    config.getString("database.mongodb.uri"),
                    config.getString("database.mongodb.database")
                );
            default:
            case "sqlite":
                logger.info("Initializing SQLite Database...");
                return new MCEconomySQLite(
                    new File(dataFolder, config.getString("database.sqlite.file", "economy.db"))
                );
        }
    }
}
