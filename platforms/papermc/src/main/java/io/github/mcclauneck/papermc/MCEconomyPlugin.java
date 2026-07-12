package io.github.mcclauneck.papermc;

import io.github.mcclauneck.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.bukkit.scheduler.BukkitPlatformScheduler;
import io.github.mcclauneck.bukkit.scheduler.PlatformScheduler;

/**
 * PaperMC entry point. Installs the classic Bukkit scheduler and inherits the
 * shared bootstrap from {@link AbstractMCEconomyPlugin}.
 */
public class MCEconomyPlugin extends AbstractMCEconomyPlugin {

    @Override
    protected PlatformScheduler createScheduler() {
        return new BukkitPlatformScheduler(this);
    }
}
