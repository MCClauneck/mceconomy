# Changelog — 1.2.0

**Released:** 2026-10-25

*MCEconomy exposes a player's own balances to MCAgents as two read-only AI tools, builds
against the Minecraft 26.3 server APIs, and publishes only when a `release/{version}` pull
request merges.*

## Added

- **`tools` module** (`:tools`, depends on `api` only, shaded by the engine, not published)
  — two read-only MCAgents AI tools in the `economy` category:
  `economy_get_my_balances` (the balances of the caller's active MCIdentity identity, all
  currencies or one) and `economy_list_currencies` (every currency, its display name, and
  whether `/pay` can send it). Both fail closed for anything but an online player and take
  no player argument.
- **`tools/MCAgentsToolAdapter`** — exposes each tool as a `java.lang.reflect.Proxy` of
  MCAgents' `AITool`, loaded from the MCAgents classloader against a local `PluginTool`
  mirror, so the build needs no MCAgents artifact.
- **`bukkit/bridge/mcagents/MCAgentsToolRegistrar`** and **`BukkitEconomyToolPort`** —
  register the tools on enable and serve them from the provider's asynchronous wallet reads
  and the MCIdentity binding; one info line when MCAgents is absent, one warning when it
  cannot be bound.
- **`MCEconomyToolsTest`** — covers the proxy mapping, the non-player refusal and the JSON
  schemas.
- **`wiki/agent-tools.md`** — the human page for the tools, listed in `INDEX.md`.
- **`wiki/logs/`** — this changelog, the first under the version directory shape.

## Changed

- **Minecraft 26.3 server APIs.** `bukkit` and every `platforms/*` module compile against
  `spigot-api 26.3-R0.1-SNAPSHOT`, `paper-api 26.3.build.+` and `folia-api 26.3.build.+`,
  up from the 26.1.2 line.
- **Every `plugin.yml`** adds `softdepend: [MCAgents]` beside `depend: [MCIdentity]`.
- **`BalanceCommand.orderedCurrencies`** is now a public static shared with the tools, so
  the AI reports balances in the order the `/balance` GUI shows them.
- **`.github/workflows/publish.yml`** runs only when a pull request from a
  `release/{version}` branch merges into `master`, instead of on every push to `master`;
  `workflow_dispatch` stays as the retry. **`release.yml`** follows Publish runs on
  `release/**` and `master`. The logic lives in `MCEngine/actions` (MCEngine/actions#3).
  What a maintainer does about it: cut a release by merging a `release/{version}` branch —
  an ordinary merge to `master` no longer publishes.

## Fixed

- **`.github/workflows/release.yml`** grants `packages: read`, which the shared Release
  build requests to resolve plugin libraries from GitHub Packages.
