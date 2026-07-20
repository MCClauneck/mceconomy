package io.github.mcclauneck.mceconomy.foliamc.scheduler;

import io.github.mcclauneck.mceconomy.bukkit.scheduler.PlatformScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * {@link PlatformScheduler} backed by Folia's region-aware schedulers.
 * <p>
 * Folia disables the classic Bukkit scheduler, so asynchronous work is
 * dispatched through the global async scheduler and entity-confined work
 * through the owning entity's scheduler, keeping the shared bukkit logic
 * correct on a regionised server.
 * </p>
 */
public final class FoliaPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;

    /**
     * @param plugin the owning plugin used to schedule tasks
     */
    public FoliaPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }
}
