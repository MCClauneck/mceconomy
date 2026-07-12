package io.github.mcclauneck.bukkit.scheduler;

import org.bukkit.entity.Entity;

/**
 * Platform-agnostic scheduling abstraction for the shared bukkit module.
 * <p>
 * The shared code targets the plain spigot-api and therefore cannot reference
 * Paper's or Folia's schedulers directly. Each platform module supplies its own
 * implementation:
 * </p>
 * <ul>
 *   <li>Spigot &amp; Paper: {@link BukkitPlatformScheduler} (classic Bukkit scheduler).</li>
 *   <li>Folia: a region-aware implementation living in the foliamc platform module.</li>
 * </ul>
 */
public interface PlatformScheduler {

    /**
     * Runs a task asynchronously, off the main server thread.
     *
     * @param task the task to run
     */
    void runAsync(Runnable task);

    /**
     * Runs a task on the thread that owns the given entity — the main server
     * thread on Spigot/Paper, or the entity's region thread on Folia. Use this
     * before touching thread-confined Bukkit API such as inventories.
     *
     * @param entity the entity whose owning thread must run the task
     * @param task   the task to run
     */
    void runForEntity(Entity entity, Runnable task);
}
