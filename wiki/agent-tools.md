# MCAgents AI Tools

MCEconomy ships two read-only AI tools for the **MCAgents** plugin. When MCAgents is installed,
a player can ask the in-game AI about their own money ("how many coins do I have?") and the AI
answers by calling these tools.

## Who can use them

Any normal player — no permission node and no OP is needed, because the tools are read-only and
only ever report the calling player's own data. They fail closed: when the caller is not an online,
in-game player (for example the console or a server-side call), the tool answers
`Error: ... only available to an in-game player.` and reads nothing. No tool takes a player name,
so a player can never read another player's wallet, and no tool changes any balance.

## The tools

All tools are filed under the MCAgents category `economy`.

| Tool | Arguments | Returns |
| --- | --- | --- |
| `economy_get_my_balances` | `currency` (optional): a currency code such as `coin` | The balances held by the player's account — the **active MCIdentity identity**, or the player when MCIdentity is not installed —, one line per currency (`Coin (coin): 1,234`), or only the requested currency. Same wallets and order as `/balance`. |
| `economy_list_currencies` | none | Every currency on the server: its code, display name, and whether it can be sent with `/pay`. |

Wallets belong to identities, not to the raw player, exactly as for `/balance`. A player with no
equipped identity is told to equip one first; an unknown currency code is answered with the list
of known codes.

## MCAgents is optional

MCEconomy runs the same with or without MCAgents. `plugin.yml` lists MCAgents under
`softdepend`, so when it is installed it enables first and MCEconomy registers the tools during
its own enable; when it is absent MCEconomy logs one info line and carries on. If the MCAgents
version installed is incompatible, MCEconomy logs one warning and carries on without the tools.

## How it is built

The tools live in the pure Java `tools` module (no Bukkit types), which the engine jar shades.
They are not compiled against MCAgents: `MCAgentsToolAdapter` exposes each tool as a
`java.lang.reflect.Proxy` of MCAgents' `AITool` interface, loaded from the MCAgents plugin's
classloader, and `bukkit/bridge/mcagents/MCAgentsToolRegistrar` registers those proxies through
the MCAgents provider. The tools read through `EconomyToolPort`, implemented in `bukkit` over the
provider's asynchronous wallet reads and the account binding, so no tool touches a player
entity and every database call stays asynchronous.
