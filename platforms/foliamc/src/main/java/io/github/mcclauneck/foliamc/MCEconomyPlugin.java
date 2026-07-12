package io.github.mcclauneck.foliamc;

import io.github.mcclauneck.bukkit.AbstractMCEconomyPlugin;

/**
 * Folia entry point. Inherits the shared bootstrap from
 * {@link AbstractMCEconomyPlugin}. All database work is asynchronous, so the
 * shared bootstrap complies with Folia's strict threading requirements.
 */
public class MCEconomyPlugin extends AbstractMCEconomyPlugin {
}
