package io.github.mcclauneck.mceconomy.tools;

import io.github.mcclauneck.mceconomy.api.models.Currency;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code economy_list_currencies} — lists the currencies this server's economy
 * knows, with whether each can be sent with {@code /pay}. Read-only, and
 * answered only for an in-game player.
 */
public final class ListCurrenciesTool implements PluginTool {

    private final EconomyToolPort port;

    /**
     * @param port The economy view the tool reads through.
     */
    public ListCurrenciesTool(EconomyToolPort port) {
        this.port = port;
    }

    @Override
    public String getName() {
        return "economy_list_currencies";
    }

    @Override
    public String getDescription() {
        return "Lists the currencies of this server's economy (MCEconomy): each currency's code, display name, "
                + "and whether it can be transferred to another player with /pay. Takes no arguments. "
                + "Use the code with economy_get_my_balances to read one balance.";
    }

    @Override
    public String getParametersJsonSchema() {
        return "{\"type\": \"object\", \"properties\": {}, \"required\": []}";
    }

    @Override
    public List<String> getCategories() {
        return List.of(EconomyTools.CATEGORY);
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, ToolCaller caller) {
        PlayerGuard.Result guard = PlayerGuard.require(caller, this.port, "the currency list");
        if (!guard.allowed()) {
            return CompletableFuture.completedFuture(guard.error());
        }

        List<Currency> currencies = this.port.currencies();
        if (currencies.isEmpty()) {
            return CompletableFuture.completedFuture("This server's economy has no currencies.");
        }
        StringBuilder out = new StringBuilder("Currencies on this server (code: name, transferability):");
        for (Currency currency : currencies) {
            out.append("\n- ").append(currency.systemCode()).append(": ").append(currency.displayName())
                    .append(currency.transferable() ? " (transferable with /pay)" : " (not transferable)");
        }
        return CompletableFuture.completedFuture(out.toString());
    }
}
