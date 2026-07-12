package io.github.mcclauneck.bukkit.scheduler;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * {@link PlatformScheduler} backed by the classic {@code BukkitScheduler}.
 * <p>
 * Suitable for SpigotMC and PaperMC, where all server state lives on a single
 * main thread. Folia, which partitions the world into independently ticking
 * regions, requires a dedicated region-aware implementation instead.
 * </p>
 */
public final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    /**
     * @param plugin the owning plugin used to schedule tasks
     */
    public BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        // A single main thread owns every entity on Spigot/Paper.
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
