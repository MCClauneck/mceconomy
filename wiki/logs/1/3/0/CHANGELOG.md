# Changelog — 1.3.0

**Released:** 2026-10-25

*MCEconomy gains an `agents` block that picks the AI model and OpenRouter API key its agent
work runs on, defaulting to `global` — whatever MCAgents is configured with.*

## Added

- **`agents` block in every platform `config.yml`** (#27) — `agents.model` and
  `agents.token`, both `global` by default. `global` is read at runtime from MCAgents'
  `agents.model` / `agents.token` through the `getAgentsModel()` / `getAgentsToken()`
  getters MCAgents 6.2.0 added; any other value is used as written.
- **`bukkit/config/AgentsSettings`** and **`MCAgentsGlobal`** (#27) — read the block and
  resolve `global` from the running MCAgents plugin by reflection, falling back to the
  provider's `getDefaultModel()` / `getServerApiKey()` on MCAgents older than 6.2.0, and
  to blank without MCAgents.
- **`AbstractMCEconomyPlugin#getAgentsSettings()`, `getAgentsModel()` and
  `getAgentsToken()`** (#27) — the configured block and the resolved model and key. The
  enable log names the model, or `global (MCAgents)`, and never the key.
- **`AgentsSettingsTest`** (#27) — four tests for global resolution, own values, a missing
  block and an absent MCAgents.

## Changed

- **README** (#27) — the Configuration section documents the `agents` block and what
  `global` means.

What a server owner does about it: nothing. The shipped `global` values keep MCEconomy on
MCAgents' model and key; MCEconomy's read-only AI tools are still called by MCAgents on the
player's behalf.
