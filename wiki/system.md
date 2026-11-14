# System Architecture

MCEconomy is a consolidated monorepo that merges six formerly separate repositories into one
Gradle multi module build. One shared implementation backs SpigotMC, PaperMC, and Folia, and
each platform ships a thin entry point that shades the shared modules into a single plugin jar.

## Modules

| Module | Gradle path | Published | Responsibility |
| --- | --- | --- | --- |
| `api` | `:api` | Yes (`io.github.mcclauneck:api`) | Pure Java data contracts and immutable models, including the `AccountTypes` constants. No platform or database dependencies. |
| `common` | `:common` | Yes (`io.github.mcclauneck:common`) | The central `MCEconomyProvider` and the SQLite, MySQL, PostgreSQL, MongoDB, and central database backends. |
| `bukkit` | `:bukkit` | Yes (`io.github.mcclauneck:bukkit`) | Shared Bukkit layer: the `AbstractMCEconomyPlugin` bootstrap, the `/balance` and `/pay` commands and GUI, the scheduler abstraction, and the player account listener. |
| `tools` | `:tools` | No | Pure Java, read-only MCAgents AI tools and the reflective `AITool` proxy adapter; see [MCAgents AI Tools](agent-tools.md). Shaded by the engine. |
| `platforms/spigotmc` | `:platforms:spigotmc` | No | SpigotMC entry point; bundled into the engine jar. |
| `platforms/papermc` | `:platforms:papermc` | No | PaperMC entry point; bundled into the engine jar. |
| `platforms/foliamc` | `:platforms:foliamc` | No | Folia entry point and region aware scheduler; bundled into the engine jar. |
| `platforms/engine` | `:platforms:engine` | No | Universal central loader; the single distributed jar that detects SpigotMC, PaperMC, or Folia at enable time and installs the matching scheduler. |

Dependency direction: `api` ← `common` ← `bukkit` ← { `spigotmc`, `papermc`, `foliamc` } ← `engine`.
Modules reference each other with Gradle `project(':...')` references, so one build compiles the
whole project with no intermediate publishing step. The `engine` module is the single place that
shades `api`, `common`, and `bukkit` into the one universal distributable jar; the per-platform
modules reference the shared modules `compileOnly` and ship only their own entry point classes,
which the engine bundles.

## Platform abstraction

The `bukkit` module compiles against the plain Spigot API, the common denominator across all
three platforms, and hides scheduling differences behind a scheduler abstraction. Spigot and
Paper use the classic Bukkit scheduler; Folia uses a region aware scheduler. Inventory and
player operations are always dispatched to the thread that owns the player, so the same GUI and
listener code is safe on every platform.

The universal `engine` jar chooses the scheduler at runtime. At enable time it probes for classes
unique to each server — Folia's `RegionizedServer`, then Paper's `PaperConfig` — and falls back to
Spigot, installing the region aware Folia scheduler on Folia and the classic Bukkit scheduler on
Spigot and Paper. Detection is purely reflective, so the engine compiles against the Folia API
alone, the superset of the Bukkit, Paper, and Folia types the schedulers use.

## Accounts

Every wallet belongs to the player: account type `player` (`AccountTypes.PLAYER`) keyed by the
player UUID. `AccountBinding` binds `AccountSource.players()` at enable time and registers
`PlayerAccountListener`, which creates the account on join. Since MCIdentity 5.0.0 a player has
one character whatever profession they wear, so MCEconomy reads nothing from MCIdentity and
compiles against no MCIdentity artifact.

## Storage

Every SQL backend stores account rows in the shared `accounts` table, which MCMultiverse,
MCRealm and the other account-keyed plugins create with the same statement. Whichever plugin
starts first creates it with `CREATE TABLE IF NOT EXISTS`, and the others find it there.
MCEconomy reads the statement byte for byte from `common/src/main/resources/schema/accounts/`,
and `SharedAccountsTest` pins the SHA-256 of each dialect's file. MCEconomy never creates
`account_property`, which belongs to MCMultiverse and MCRealm.

| Table | Key | Account foreign key |
| --- | --- | --- |
| `accounts` | `(account_type, account_uuid)` | Shared table. |
| `economy_currency` | `id`, unique `system_code` | None. |
| `economy_wallet` | `(account_type, account_uuid, currency_id)` | `ON DELETE CASCADE` |
| `economy_transactions` | `transaction_uuid` | `ON DELETE CASCADE` |
| `economy_logs` | `id` | `ON DELETE RESTRICT` |
| `archive_economy_wallet`, `archive_economy_logs`, `archive_economy_transactions` | As the live table (PostgreSQL only) | `ON DELETE RESTRICT` |
| `archive_economy_currency` | `id` (PostgreSQL only) | None. |

The account columns have the shared table's types in every account-keyed table: `TEXT` on
SQLite, `VARCHAR(50)` and `VARCHAR(36)` on MySQL, and `VARCHAR(50)` and `UUID` on PostgreSQL.
Before the first wallet, transaction or log write for an account, the backend inserts its
`accounts` row unless it exists. MongoDB has no foreign keys, so it upserts the account's
document in the `accounts` collection (`_id` `{type}_{uuid}`, with `account_type`,
`account_uuid`, `created_at` and `updated_at`) before every account-keyed write. The central
backend leaves its schema to the server.

## Accounts, wallets, and logging

The `MCEconomyProvider` is the central entry point for balance operations: adding, removing,
and setting balances, and transferring between accounts. It is a facade: the currency cache and
the configured currencies live in `common/currency/CurrencyRegistry`, and every logged balance
change in `common/balance/BalanceLedger`. Amounts are validated (no negatives,
no overdrafts) and every change writes log rows. A transfer debits the sender with a `SEND`
entry and credits the receiver with a `RECEIVE` entry, plus a transaction receipt, and is
rejected when the currency is not transferable. Currencies are configured per platform
`config.yml`, keyed by system code, each with a display name, a transferable flag, and an
optional head texture.

## Balance GUI

The `/balance` command builds a paged inventory, one custom head per currency. The inventory
height is derived from the currencies on the current page: one content row for every nine
currencies, plus a navigation row only when the currencies span more than one page. Leftover
content slots and the navigation row are padded with a black glass pane whose tooltip is hidden,
so the filler shows no hover text. Because the navigation row moves with the inventory height,
the previous and next arrow slots are stored on the inventory holder and read back by the click
listener, which cancels every interaction and repaginates on an arrow click.
