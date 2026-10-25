package io.github.mcclauneck.mceconomy.bukkit.bridge.mcagents;

import io.github.mcclauneck.mceconomy.tools.EconomyTools;
import io.github.mcclauneck.mceconomy.tools.EconomyToolPort;
import io.github.mcclauneck.mceconomy.tools.MCAgentsToolAdapter;
import io.github.mcclauneck.mceconomy.tools.PluginTool;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers MCEconomy's AI tools with MCAgents, when MCAgents is installed.
 *
 * <p>MCAgents is optional and is never compiled against: its provider, its
 * {@code AITool} interface, and {@code registerTool} are all resolved through
 * the MCAgents plugin's own classloader at enable time. {@code softdepend:
 * [MCAgents]} in {@code plugin.yml} makes MCAgents enable first and its
 * classes reachable. Without MCAgents one info line is logged; any reflective
 * failure logs one warning; either way MCEconomy carries on.</p>
 */
public final class MCAgentsToolRegistrar {

    /**
     * The MCAgents plugin name, as declared in its {@code plugin.yml}.
     */
    public static final String PLUGIN_NAME = "MCAgents";

    private MCAgentsToolRegistrar() {
    }

    /**
     * Registers every MCEconomy tool with MCAgents.
     *
     * @param port   The economy view the tools read through.
     * @param logger The plugin logger.
     * @return The number of tools registered; {@code 0} when MCAgents is absent or the binding failed.
     */
    public static int register(EconomyToolPort port, Logger logger) {
        Plugin mcagents = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (mcagents == null || !mcagents.isEnabled()) {
            logger.info("MCAgents not found; MCEconomy AI tools are not registered.");
            return 0;
        }

        List<PluginTool> tools = EconomyTools.create(port);
        try {
            Object provider = mcagents.getClass().getMethod("getProvider").invoke(mcagents);
            if (provider == null) {
                throw new IllegalStateException("MCAgents has no provider (it failed to enable)");
            }
            Class<?> aiTool = Class.forName(MCAgentsToolAdapter.AI_TOOL_CLASS, false,
                    mcagents.getClass().getClassLoader());
            Method registerTool = provider.getClass().getMethod("registerTool", aiTool);

            registerCategory(provider);
            for (PluginTool tool : tools) {
                registerTool.invoke(provider, MCAgentsToolAdapter.createProxy(aiTool, tool));
            }
            logger.info("Registered " + tools.size() + " MCEconomy AI tools with MCAgents.");
            return tools.size();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "Could not register MCEconomy AI tools with MCAgents; continuing without them: "
                    + failure);
            return 0;
        }
    }

    /**
     * Files the tools under their own MCAgents category when this MCAgents
     * version can register one; older versions simply skip it.
     */
    private static void registerCategory(Object provider) throws ReflectiveOperationException {
        Method registerCategory;
        try {
            registerCategory = provider.getClass().getMethod("registerCategory", String.class, String.class);
        } catch (NoSuchMethodException unsupported) {
            return;
        }
        registerCategory.invoke(provider, EconomyTools.CATEGORY, EconomyTools.CATEGORY_DESCRIPTION);
    }
}
