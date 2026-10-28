# Changelog — 1.6.0

**Released:** 2026-10-28

*`/balance` and `/pay` each have a permission node a server owner can revoke, and the provider,
the bootstrap, and the SQL backends are split so each class does one job.*

## Added

- **Permission nodes** (#34) — `mcclauneck.mceconomy.command.balance` and
  `mcclauneck.mceconomy.command.pay`, both default `true`, and the `mcclauneck.mceconomy.*`
  parent (default `op`), declared in every platform `plugin.yml`. Each command checks its node,
  and `/pay` offers no tab completions without it. MCEconomy has no administrative command, so
  no node defaults to `op` on its own.
- **`MCEconomyProvider#getCurrenciesInDisplayOrder()`** (#35) — the currencies in the order
  `/balance` shows them.

## Changed

- **`MCEconomyProvider`** (#35) is a facade over `currency/CurrencyRegistry` (the currency cache
  and configured currencies), `currency/CurrencyListParser`, `currency/DefaultCurrencies`, and
  `balance/BalanceLedger` (logged balance changes). Every public method and constant is kept.
- **SQL backends** (#35) — SQLite, MySQL, and PostgreSQL share their queries through
  `database/sql/AbstractSqlEconomyDatabase`; each keeps only its schema and dialect statements.
  Schemas and statement text are unchanged.
- **`AbstractMCEconomyPlugin`** (#35) sequences the enable steps; backend selection, the
  MCIdentity-or-player account binding, and command wiring live in
  `database/EconomyDatabaseFactory`, `host/AccountBinding`, and `commands/CommandRegistrar`.
- `BalanceCommand.orderedCurrencies` delegates to the provider (#35).
