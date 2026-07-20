package io.github.mcclauneck.mceconomy.foliamc;

import io.github.mcclauneck.mceconomy.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.mceconomy.bukkit.scheduler.PlatformScheduler;
import io.github.mcclauneck.mceconomy.foliamc.scheduler.FoliaPlatformScheduler;

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
