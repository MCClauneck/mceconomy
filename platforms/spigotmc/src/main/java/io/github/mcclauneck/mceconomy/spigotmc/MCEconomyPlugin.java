package io.github.mcclauneck.mceconomy.spigotmc;

import io.github.mcclauneck.mceconomy.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.BukkitPlatformScheduler;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.PlatformScheduler;

/**
 * SpigotMC entry point. Installs the classic Bukkit scheduler and inherits the
 * shared bootstrap from {@link AbstractMCEconomyPlugin}.
 */
public class MCEconomyPlugin extends AbstractMCEconomyPlugin {

    @Override
    protected PlatformScheduler createScheduler() {
        return new BukkitPlatformScheduler(this);
    }
}
