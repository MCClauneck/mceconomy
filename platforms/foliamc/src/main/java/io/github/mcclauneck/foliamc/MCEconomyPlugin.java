package io.github.mcclauneck.foliamc;

import io.github.mcclauneck.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.bukkit.scheduler.PlatformScheduler;
import io.github.mcclauneck.foliamc.scheduler.FoliaPlatformScheduler;

/**
 * Folia entry point. Installs the region-aware scheduler and inherits the
 * shared bootstrap from {@link AbstractMCEconomyPlugin}.
 */
public class MCEconomyPlugin extends AbstractMCEconomyPlugin {

    @Override
    protected PlatformScheduler createScheduler() {
        return new FoliaPlatformScheduler(this);
    }
}
