package io.github.mcclauneck.mceconomy.tools;

import io.github.mcclauneck.mceconomy.api.models.Currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * {@code economy_get_my_balances} — reports the calling player's balances, in
 * every currency or in one. Read-only, and only ever the caller's own wallet.
 *
 * <p>Wallets belong to the player, keyed by account type {@code player} and
 * the player UUID, as they do for {@code /balance}.</p>
 */
public final class GetMyBalancesTool implements PluginTool {

    /**
     * The optional argument narrowing the answer to one currency.
     */
    static final String CURRENCY_ARGUMENT = "currency";

    private final EconomyToolPort port;

    /**
     * @param port The economy view the tool reads through.
     */
    public GetMyBalancesTool(EconomyToolPort port) {
        this.port = port;
    }

    @Override
    public String getName() {
        return "economy_get_my_balances";
    }

    @Override
    public String getDescription() {
        return "Gets the calling player's own money balances in this server's economy (MCEconomy), as held by "
                + "the player's own account. Returns every currency, or only one when 'currency' is given. "
                + "It cannot read another player's balance.";
    }

    @Override
    public String getParametersJsonSchema() {
        return "{\"type\": \"object\", \"properties\": {"
                + "\"" + CURRENCY_ARGUMENT + "\": {\"type\": \"string\", \"description\": "
                + "\"Optional currency code (see economy_list_currencies); omit it to get every balance.\"}"
                + "}, \"required\": []}";
    }

    @Override
    public List<String> getCategories() {
        return List.of(EconomyTools.CATEGORY);
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, ToolCaller caller) {
        PlayerGuard.Result guard = PlayerGuard.require(caller, this.port, "your balance");
        if (!guard.allowed()) {
            return CompletableFuture.completedFuture(guard.error());
        }
        if (!this.port.isAccountReady()) {
            return CompletableFuture.completedFuture(
                    "Error: The economy is not ready yet, so balances cannot be read. Try again in a moment.");
        }

        List<Currency> all = this.port.currencies();
        List<Currency> wanted;
        Object requested = arguments == null ? null : arguments.get(CURRENCY_ARGUMENT);
        String code = requested == null ? "" : String.valueOf(requested).trim();
        if (code.isEmpty()) {
            wanted = all;
        } else {
            wanted = all.stream().filter(c -> c.systemCode().equalsIgnoreCase(code)).toList();
            if (wanted.isEmpty()) {
                String known = all.stream().map(Currency::systemCode).collect(Collectors.joining(", "));
                return CompletableFuture.completedFuture("Error: Unknown currency '" + code + "'. Known currencies: "
                        + (known.isEmpty() ? "none" : known) + ".");
            }
        }
        if (wanted.isEmpty()) {
            return CompletableFuture.completedFuture("This server's economy has no currencies.");
        }

        try {
            return this.port.accountUuid(guard.playerUuid())
                    .thenCompose(account -> account
                            .map(accountUuid -> readBalances(accountUuid, wanted))
                            .orElseGet(() -> CompletableFuture.completedFuture(
                                    "You have no economy account yet. Try again in a moment.")))
                    .exceptionally(failure -> "Error: Could not read your balance right now. Try again later.");
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture("Error: Could not read your balance right now. Try again later.");
        }
    }

    private CompletableFuture<String> readBalances(UUID accountUuid, List<Currency> currencies) {
        List<CompletableFuture<Long>> reads = new ArrayList<>(currencies.size());
        for (Currency currency : currencies) {
            reads.add(this.port.balance(accountUuid, currency));
        }
        return CompletableFuture.allOf(reads.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            StringBuilder out = new StringBuilder("Your balances:");
            for (int i = 0; i < currencies.size(); i++) {
                Currency currency = currencies.get(i);
                out.append("\n- ").append(currency.displayName()).append(" (").append(currency.systemCode())
                        .append("): ").append(String.format("%,d", reads.get(i).join()));
            }
            return out.toString();
        });
    }
}
