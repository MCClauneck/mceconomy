package io.github.mcclauneck.engine;

import io.github.mcclauneck.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.bukkit.scheduler.BukkitPlatformScheduler;
import io.github.mcclauneck.bukkit.scheduler.PlatformScheduler;
import io.github.mcclauneck.foliamc.scheduler.FoliaPlatformScheduler;

/**
 * Universal entry point — the "central loader".
 * <p>
 * This single, distributable jar runs on SpigotMC, PaperMC, and Folia. Instead of
 * shipping one jar per platform, the engine detects the running server at enable
 * time and installs the matching {@link PlatformScheduler}, then inherits the whole
 * enable/disable lifecycle from {@link AbstractMCEconomyPlugin}.
 * </p>
 * <p>
 * Detection is purely reflective ({@link #detectPlatform()}), so the engine only
 * needs to compile against the Folia API — the superset of the Bukkit, Paper, and
 * Folia types the bundled schedulers reference.
 * </p>
 */
public class MCEconomyEnginePlugin extends AbstractMCEconomyPlugin {

    /**
     * The server flavours the universal jar can run on, in detection order.
     */
    public enum ServerPlatform { FOLIA, PAPER, SPIGOT }

    /**
     * Detects the running server by probing for classes unique to each flavour.
     * Folia is checked first because a Folia server also satisfies the Paper probe.
     *
     * @return the detected {@link ServerPlatform}.
     */
    private ServerPlatform detectPlatform() {
        if (classExists("io.papermc.paper.threadedregions.RegionizedServer")) return ServerPlatform.FOLIA;
        if (classExists("com.destroystokyo.paper.PaperConfig")) return ServerPlatform.PAPER;
        return ServerPlatform.SPIGOT;
    }

    /**
     * @param name the fully qualified class name to probe.
     * @return {@code true} when the class is present on the runtime classpath.
     */
    private boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    protected PlatformScheduler createScheduler() {
        switch (detectPlatform()) {
            case FOLIA:
                getLogger().info("Detected Folia server. Installing the region-aware Folia scheduler.");
                return new FoliaPlatformScheduler(this);
            case PAPER:
                getLogger().info("Detected Paper server. Installing the Bukkit scheduler.");
                return new BukkitPlatformScheduler(this);
            case SPIGOT:
            default:
                getLogger().info("Detected Spigot server. Installing the Bukkit scheduler.");
                return new BukkitPlatformScheduler(this);
        }
    }
}
