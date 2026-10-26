# Changelog — 1.4.0

**Released:** 2026-10-26

*MCIdentity becomes a soft dependency. With it, money belongs to the active identity as before;
without it, money belongs to the player.*

## Added

- **`api/host/AccountSource`** (#29) — the account binding `/balance`, `/pay` and the AI tools
  read: `identities(IdentitySource)` keys wallets to the active identity (`identity`),
  `players()` keys them to the player UUID (`player`).
- **`AccountTypes.PLAYER`** (#29) — `player`, the account type used without MCIdentity.
- **`bukkit/listener/PlayerAccountListener`** (#29) — creates a `player` account on join, and
  for players already online at enable, when MCIdentity is not installed.
- **`AbstractMCEconomyPlugin#getAccounts()`** (#29) — the live account binding.
- **`AccountSourceTest`** (#29) — five tests for both bindings.

## Changed

- **Every platform `plugin.yml`** (#29) — `depend: [MCIdentity]` is gone; the soft
  dependencies are now `softdepend: [MCIdentity, MCAgents]`.
- **`/balance`, `/pay` and `economy_get_my_balances`** (#29) — settle against the account
  binding instead of hardcoding `identity`.
- **README, `wiki/api.md`, `wiki/agent-tools.md`** (#29) — document both modes.

What a server owner does about it: nothing on a server with MCIdentity. A server without
MCIdentity can now run MCEconomy; each player gets one set of wallets. An installed but
incompatible MCIdentity still leaves `/balance` and `/pay` unavailable rather than falling
back to player wallets. No table or column changed.
