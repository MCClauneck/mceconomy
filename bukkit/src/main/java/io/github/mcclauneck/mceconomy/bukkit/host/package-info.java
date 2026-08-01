package io.github.mcclauneck.mceconomy.bukkit.host;

/**
 * Reflective bindings to the host plugins MCEconomy integrates with. Each bridge
 * resolves its target through that plugin's own classloader at enable time and
 * degrades to "unavailable" rather than throwing when the host is absent.
 */
