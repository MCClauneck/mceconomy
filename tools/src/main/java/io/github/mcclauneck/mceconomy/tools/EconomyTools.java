package io.github.mcclauneck.mceconomy.tools;

import java.util.List;

/**
 * The MCEconomy tool set and the MCAgents category it is filed under.
 */
public final class EconomyTools {

    /**
     * The MCAgents category id every MCEconomy tool belongs to.
     */
    public static final String CATEGORY = "economy";

    /**
     * The category description MCAgents shows the AI when it picks categories.
     */
    public static final String CATEGORY_DESCRIPTION =
            "The calling player's own MCEconomy money: their balances and the server's currencies.";

    private EconomyTools() {
    }

    /**
     * Builds every MCEconomy tool over one economy view.
     *
     * @param port The economy view the tools read through.
     * @return The tools, in a stable order.
     */
    public static List<PluginTool> create(EconomyToolPort port) {
        return List.of(new GetMyBalancesTool(port), new ListCurrenciesTool(port));
    }
}
