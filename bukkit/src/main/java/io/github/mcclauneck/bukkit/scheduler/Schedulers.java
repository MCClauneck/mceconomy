package io.github.mcclauneck.bukkit.scheduler;

import org.bukkit.entity.Entity;

/**
 * Static access point to the active {@link PlatformScheduler}.
 * <p>
 * Each platform plugin installs its scheduler during {@code onEnable} via
 * {@link #set(PlatformScheduler)}; shared bukkit code then schedules work
 * through the convenience methods here without knowing the platform.
 * </p>
 */
public final class Schedulers {

    private static PlatformScheduler scheduler;

    private Schedulers() {
    }

    /**
     * Installs the platform scheduler implementation. Call once during plugin enable.
     *
     * @param impl the platform-specific scheduler
     */
    public static void set(PlatformScheduler impl) {
        scheduler = impl;
    }

    /**
     * @return the active platform scheduler, or {@code null} if none was installed
     */
    public static PlatformScheduler get() {
        return scheduler;
    }

    /**
     * Runs a task asynchronously, off the main server thread.
     *
     * @param task the task to run
     */
    public static void runAsync(Runnable task) {
        scheduler.runAsync(task);
    }

    /**
     * Runs a task on the thread that owns the given entity — the main server
     * thread on Spigot/Paper, or the entity's region thread on Folia.
     *
     * @param entity the entity whose owning thread must run the task
     * @param task   the task to run
     */
    public static void runForEntity(Entity entity, Runnable task) {
        scheduler.runForEntity(entity, task);
    }
}
