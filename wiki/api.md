# Developer API Usage

[← Back to README](../README.md)

This guide explains how to integrate a **new custom plugin** (for example a shop,
job, or quest-reward system) with **MCEconomy**. The economy system is exposed
through the pure-Java `api`/`common` modules and the Bukkit event layer in
`bukkit`, all published to GitHub Packages.

MCEconomy is designed to sit on top of **MCIdentity**: with it installed, every
economy account is bound to an *identity*, so a single player's money is kept
separate per character. MCIdentity is a soft dependency: without it, accounts are
bound to the player (`AccountTypes.PLAYER`, keyed by the player UUID).
See [§4](#4-architecture--bind-money-to-the-identity-not-the-player).

**Contents**

1. [Dependency Import](#1-dependency-import)
2. [API Usage](#2-api-usage)
3. [Bukkit Integration](#3-bukkit-integration)
4. [Architecture — bind money to the identity, not the player](#4-architecture--bind-money-to-the-identity-not-the-player)
5. [Custom account types — clan, guild, and beyond](#5-custom-account-types--clan-guild-and-beyond)

---

## 1. Dependency Import

MCEconomy's library modules are published to the **GitHub Packages** Maven
registry for the repository `MCClauneck/mceconomy`. GitHub Packages **always
requires authentication**, even to read public packages, so consumers must supply
a GitHub username and a Personal Access Token (classic) that has the
`read:packages` scope.

### 1.1 Configure the repository and credentials

Never hard-code credentials in `build.gradle`. Read them from environment
variables or from your **`~/.gradle/gradle.properties`** (outside the project):

```properties
# ~/.gradle/gradle.properties
gpr.user=your-github-username
gpr.key=ghp_your_personal_access_token_with_read_packages
```

```groovy
// build.gradle
repositories {
    mavenCentral()

    maven {
        name = "MCEconomyGitHubPackages"
        url = uri("https://maven.pkg.github.com/MCClauneck/mceconomy")
        credentials {
            // Falls back to the GITHUB_ACTOR / GITHUB_TOKEN env vars in CI.
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key")  ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### 1.2 Declare the three dependencies (`compileOnly`, version `1.0.0`)

MCEconomy is present on the server at runtime, so you compile against it but do
**not** bundle/shade it — use the `compileOnly` configuration for all three
artifacts. The published coordinates are group `io.github.mcclauneck`, version
`1.0.0`:

```groovy
// build.gradle
dependencies {
    // MCEconomy — provided by the server at runtime, so compileOnly only.
    compileOnly 'io.github.mcclauneck:api:1.0.0'      // interfaces & models
    compileOnly 'io.github.mcclauneck:common:1.0.0'   // MCEconomyProvider + data layer
    compileOnly 'io.github.mcclauneck:bukkit:1.0.0'   // Bukkit events & helpers

    // Your own server platform API (example)
    compileOnly 'org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT'
}
```

> The jar file names are `MCEconomyApi-1.0.0.jar`, `MCEconomyCommon-1.0.0.jar`,
> and `MCEconomyBukkit-1.0.0.jar` (set via `archivesName`), but the resolvable
> **Maven artifactIds** are `api`, `common`, and `bukkit` as shown above.

### 1.3 Load order

So MCEconomy initializes before your plugin, declare it as a dependency in your
own `plugin.yml`:

```yaml
name: MyShopPlugin
main: com.example.shop.ShopPlugin
version: 1.0.0
api-version: '1.20'
depend: [MCEconomy]        # use softdepend if MCEconomy is optional
```

---

## 2. API Usage

All economy data flows through
`io.github.mcclauneck.mceconomy.common.MCEconomyProvider`, the central class that
orchestrates accounts, wallets, currencies, transfers, and transaction logging
over a pluggable `IEconomyDatabase` backend (SQLite, MySQL, PostgreSQL, MongoDB,
or the remote central API).

> **Threading.** Every database operation returns a `CompletableFuture<…>` and
> completes on an asynchronous thread. Do the economy work inside the future's
> callbacks, but hop back to the server thread (Bukkit scheduler, or the
> region/global scheduler on Folia) before calling any thread-confined Bukkit API
> such as inventories or entities.

### 2.1 Obtain the provider

MCEconomy holds one `MCEconomyProvider` per server, exposed by the plugin's main
class through `getProvider()`. Every platform entry point (SpigotMC, PaperMC,
and Folia) extends the shared
`io.github.mcclauneck.mceconomy.bukkit.AbstractMCEconomyPlugin` base from the published
`bukkit` module, so the portable way to reach the provider is a simple cast:

```java
import io.github.mcclauneck.mceconomy.bukkit.AbstractMCEconomyPlugin;
import io.github.mcclauneck.mceconomy.common.MCEconomyProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

MCEconomyProvider economy() {
    Plugin plugin = Bukkit.getPluginManager().getPlugin("MCEconomy");
    if (!(plugin instanceof AbstractMCEconomyPlugin economyPlugin) || !plugin.isEnabled()) {
        return null;                       // MCEconomy is not ready yet
    }
    return economyPlugin.getProvider();
}
```

Resolve it once your plugin has enabled (after MCEconomy, thanks to the
`depend`) and reuse the instance.

### 2.2 Currencies

Currencies are configured by the server owner and cached in memory. Fetch a
`Currency` by its system code before moving money:

```java
import io.github.mcclauneck.mceconomy.api.models.Currency;

Currency coin = economy().getCurrency("coin");   // null if no such currency

boolean canBePaid = coin.transferable();         // mirrors the is_transferable column
```

`getCurrencies()` returns every cached currency, and `getManagedCurrencies()`
returns the configured system-code → `CurrencyDefinition` map (display name plus
transferability). A currency with `transferable() == false` (for example a
premium or reward currency) can never move between accounts —
`transferBalance` rejects it.

### 2.3 Accounts

An account is identified by an **account type** plus a **UUID**. For
MCIdentity-bound money the type is `AccountTypes.IDENTITY` and the UUID is the
**identity UUID** (see [§4](#4-architecture--bind-money-to-the-identity-not-the-player)).
On a server without MCIdentity the type is `AccountTypes.PLAYER` and the UUID is
the **player UUID**. `AbstractMCEconomyPlugin#getAccounts()` returns the
`AccountSource` MCEconomy itself uses, so a consumer can follow the same rule
instead of hardcoding a type.
Wallets are created lazily, so you rarely need `createAccount` — but it is
idempotent and safe to call to guarantee the account row exists:

```java
import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import java.util.UUID;

UUID identityUuid = /* from MCIdentity */ ;

economy().createAccount(AccountTypes.IDENTITY, identityUuid);      // idempotent
economy().hasAccount(AccountTypes.IDENTITY, identityUuid)
         .thenAccept(exists -> { /* ... */ });
```

### 2.4 Read a balance

```java
economy().getBalance(AccountTypes.IDENTITY, identityUuid, coin)
         .thenAccept(balance -> { /* current balance for this identity */ });

// Convenience check:
economy().hasBalance(AccountTypes.IDENTITY, identityUuid, coin, 500)
         .thenAccept(enough -> { /* true if balance >= 500 */ });
```

### 2.5 Give, take, and transfer money

All mutating calls take a `LogScope` (audit category: `SEND`, `RECEIVE`,
`SPEND`, `DONATE`), a **transaction UUID** for idempotency, and a
`referenceType`/`referenceId` pair describing what caused the change. Amounts are
non-negative; the method name decides the direction.

```java
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import java.util.UUID;

UUID txn = UUID.randomUUID();

// Give money (credit). The future completes with the new balance.
economy().addBalance(AccountTypes.IDENTITY, identityUuid, coin, 250,
        LogScope.RECEIVE, txn, "quest", "daily_reward");

// Take money (debit). The future completes exceptionally on insufficient funds.
economy().removeBalance(AccountTypes.IDENTITY, identityUuid, coin, 100,
        LogScope.SPEND, UUID.randomUUID(), "shop", "buy_sword")
        .exceptionally(err -> {              // IllegalStateException: "Insufficient funds"
            // tell the player they cannot afford it
            return null;
        });

// Set an exact balance, logging the difference.
economy().setBalance(AccountTypes.IDENTITY, identityUuid, coin, 1000,
        LogScope.RECEIVE, UUID.randomUUID(), "admin", "set_balance");

// Move money between two accounts atomically (debits sender, credits receiver).
// Fails exceptionally when the currency is not transferable.
economy().transferBalance(
        AccountTypes.IDENTITY, senderIdentityUuid,
        AccountTypes.IDENTITY, receiverIdentityUuid,
        coin, 500, UUID.randomUUID(), "player_transfer", "pay_command");
```

<details>
<summary><strong>Full <code>MCEconomyProvider</code> method reference</strong></summary>

<details>
<summary><code>getCurrency(String systemCode)</code></summary>

- **Description**: Gets a cached currency by its system code (case-insensitive).
- **Return Type**: `Currency` (or `null` if not loaded).
</details>

<details>
<summary><code>getCurrencies()</code></summary>

- **Description**: Returns all cached currencies.
- **Return Type**: `List<Currency>`
</details>

<details>
<summary><code>getManagedCurrencies()</code></summary>

- **Description**: Returns the configured currencies as an unmodifiable, ordered system-code → `CurrencyDefinition` map (display name plus transferability).
- **Return Type**: `Map<String, CurrencyDefinition>`
</details>

<details>
<summary><code>createAccount(String accountType, UUID accountUuid)</code></summary>

- **Description**: Creates an economy account if it does not already exist (idempotent).
- **Return Type**: `CompletableFuture<Account>`
</details>

<details>
<summary><code>hasAccount(String accountType, UUID accountUuid)</code></summary>

- **Description**: Checks whether an account exists.
- **Return Type**: `CompletableFuture<Boolean>`
</details>

<details>
<summary><code>getBalance(String accountType, UUID accountUuid, Currency currency)</code></summary>

- **Description**: Reads the current balance of one wallet. Completes with `0` when the currency is `null`.
- **Return Type**: `CompletableFuture<Long>`
</details>

<details>
<summary><code>hasBalance(String accountType, UUID accountUuid, Currency currency, long amount)</code></summary>

- **Description**: Returns whether the wallet holds at least `amount`.
- **Return Type**: `CompletableFuture<Boolean>`
</details>

<details>
<summary><code>addBalance(String accountType, UUID accountUuid, Currency currency, long amount, LogScope scope, UUID transactionUuid, String referenceType, String referenceId)</code></summary>

- **Description**: Adds currency to a wallet and logs the transaction. Throws `IllegalArgumentException` if `amount` is negative.
- **Return Type**: `CompletableFuture<Long>` (the new balance)
</details>

<details>
<summary><code>removeBalance(String accountType, UUID accountUuid, Currency currency, long amount, LogScope scope, UUID transactionUuid, String referenceType, String referenceId)</code></summary>

- **Description**: Removes currency from a wallet and logs the transaction. Completes exceptionally with `IllegalStateException` ("Insufficient funds") when the balance is too low; throws `IllegalArgumentException` if `amount` is negative.
- **Return Type**: `CompletableFuture<Long>` (the new balance)
</details>

<details>
<summary><code>setBalance(String accountType, UUID accountUuid, Currency currency, long newBalance, LogScope scope, UUID transactionUuid, String referenceType, String referenceId)</code></summary>

- **Description**: Sets an absolute balance and logs the difference. Throws `IllegalArgumentException` if `newBalance` is negative.
- **Return Type**: `CompletableFuture<Long>`
</details>

<details>
<summary><code>transferBalance(String fromAccountType, UUID fromAccountUuid, String toAccountType, UUID toAccountUuid, Currency currency, long amount, UUID transactionUuid, String referenceType, String referenceId)</code></summary>

- **Description**: Moves money between two accounts (debit sender with `SEND`, credit receiver with `RECEIVE`). Throws `IllegalArgumentException` if `amount` is not positive; completes exceptionally if the sender has insufficient funds or the currency is not transferable (`IllegalStateException`).
- **Return Type**: `CompletableFuture<Boolean>`
</details>

<details>
<summary><code>insertLog(String accountType, UUID accountUuid, LogScope scope, Currency currency, long amount, UUID transactionUuid, String referenceType, String referenceId)</code></summary>

- **Description**: Writes a standalone audit log entry without changing a balance.
- **Return Type**: `CompletableFuture<Void>`
</details>

<details>
<summary><code>loadCurrencies()</code> / <code>ensureDefaultCurrencies()</code></summary>

- **Description**: Refresh the in-memory currency cache from the database, and create any configured currencies that are missing (also syncing a changed display name or transferable flag). Normally called by the plugin on enable.
- **Return Type**: `CompletableFuture<Void>`
</details>

</details>

---

## 3. Bukkit Integration

MCEconomy exposes custom Bukkit events from the
`io.github.mcclauneck.mceconomy.bukkit.event` package so other plugins can veto or react to
money movements. Both events work across **SpigotMC**, **PaperMC**, and **Folia**
and mark themselves asynchronous when fired off the primary thread.

| Event | Cancellable | When |
| --- | --- | --- |
| `EconomyTransactionEvent` | **Yes** | Fired *before* a balance change is applied. Cancel to veto it. |
| `EconomyWalletUpdateEvent` | No | Fired *after* a wallet balance changed. Notification only. |

The convention is: **a plugin that changes a balance fires
`EconomyTransactionEvent` first and honors cancellation**, then performs the
change and (optionally) announces it with `EconomyWalletUpdateEvent`. This gives
the whole ecosystem one agreed veto point.

### 3.1 Cancel giving money to a player

Fire the event before crediting, and skip the credit if a listener cancelled it:

```java
import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.bukkit.event.EconomyTransactionEvent;
import org.bukkit.Bukkit;

import java.util.UUID;

void giveMoney(UUID identityUuid, Currency currency, long amount) {
    EconomyTransactionEvent event = new EconomyTransactionEvent(
            AccountTypes.IDENTITY, identityUuid, currency.systemCode(), amount, LogScope.RECEIVE);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) {
        return;                                   // a listener vetoed the payout
    }
    economy().addBalance(AccountTypes.IDENTITY, identityUuid, currency, amount,
            LogScope.RECEIVE, UUID.randomUUID(), "reward", "give_money");
}
```

And a listener in another plugin that refuses payouts — for example while an
identity is "frozen":

```java
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import io.github.mcclauneck.mceconomy.bukkit.event.EconomyTransactionEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class FreezePayoutListener implements Listener {

    private final FreezeService freeze;

    public FreezePayoutListener(FreezeService freeze) {
        this.freeze = freeze;
    }

    // Cancel *giving* money (a credit) to a frozen identity.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEconomyTransaction(EconomyTransactionEvent event) {
        boolean isCredit = event.getScope() == LogScope.RECEIVE
                || event.getScope() == LogScope.DONATE;
        if (isCredit && freeze.isFrozen(event.getAccountUuid())) {
            event.setCancelled(true);             // veto the payout
        }
    }
}
```

Register it in your plugin's `onEnable`:

```java
getServer().getPluginManager().registerEvents(
        new FreezePayoutListener(freezeService), this);
```

**Folia / async safety.** `EconomyTransactionEvent` marks itself asynchronous
whenever it is fired off the primary thread (`super(!Bukkit.isPrimaryThread())`).
Keep handlers thread-safe and, when you must call main-thread-only Bukkit API,
schedule back onto the appropriate scheduler instead of calling it directly.

### 3.2 React to a completed balance change

```java
import io.github.mcclauneck.mceconomy.bukkit.event.EconomyWalletUpdateEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class BalanceBoardListener implements Listener {

    @EventHandler
    public void onWalletUpdate(EconomyWalletUpdateEvent event) {
        // event.getAccountType(), event.getAccountUuid(),
        // event.getCurrencySystemCode(), event.getNewBalance()
        // e.g. refresh a scoreboard or hologram for this account.
    }
}
```

### 3.3 Event reference

<details>
<summary><code>EconomyTransactionEvent</code></summary>

- **Package**: `io.github.mcclauneck.mceconomy.bukkit.event`
- **Description**: Fired before a balance change is applied; the veto point for economy operations.
- **Cancellable**: Yes (`setCancelled(true)` blocks the change).
- **Constructor**: `EconomyTransactionEvent(String accountType, UUID accountUuid, String currencySystemCode, long amount, LogScope scope)`
- **Accessors**:
    - `getAccountType()` → `String` — the account type (e.g. `"identity"`).
    - `getAccountUuid()` → `UUID` — the account UUID (the identity UUID for identity money).
    - `getCurrencySystemCode()` → `String` — the currency being changed.
    - `getAmount()` → `long` — the non-negative magnitude of the change.
    - `getScope()` → `LogScope` — distinguishes a credit (`RECEIVE`/`DONATE`) from a debit (`SPEND`/`SEND`).
    - `isCancelled()` / `setCancelled(boolean)`
</details>

<details>
<summary><code>EconomyWalletUpdateEvent</code></summary>

- **Package**: `io.github.mcclauneck.mceconomy.bukkit.event`
- **Description**: Fired after a wallet balance changed. Notification only.
- **Cancellable**: No.
- **Constructor**: `EconomyWalletUpdateEvent(String accountType, UUID accountUuid, String currencySystemCode, long newBalance)`
- **Accessors**:
    - `getAccountType()` → `String`
    - `getAccountUuid()` → `UUID`
    - `getCurrencySystemCode()` → `String`
    - `getNewBalance()` → `long`
</details>

---

## 4. Architecture — bind money to the identity, not the player

> [!WARNING]
> **Each MCIdentity identity is an entirely separate character. Bind economy data
> to the Identity UUID, never to the base Player UUID.**

MCEconomy depends on **MCIdentity** (`depend: [MCIdentity]`). A single player owns
**many** identities, each with its own distinct UUID, and only one is active at a
time. Switching identities is a full context switch — as if the player logged into
a completely different account. MCEconomy already keys its own accounts by the
identity UUID through the `identity` account type
(`io.github.mcclauneck.mceconomy.api.models.AccountTypes.IDENTITY`), created automatically
when an identity is equipped.

**Do this — key money by the Identity UUID:**

```java
// CORRECT: the balance belongs to the identity, not the human behind it.
UUID identityUuid = /* MCIdentity: provider.getActiveIdentity(playerUuid) or an equip event */ ;
economy().addBalance(AccountTypes.IDENTITY, identityUuid, coin, 1000,
        LogScope.RECEIVE, UUID.randomUUID(), "reward", "welcome");
```

**Never do this — keying by the Player UUID:**

```java
// WRONG: every identity this player owns would share ONE balance,
// collapsing distinct characters into one wallet.
economy().addBalance(AccountTypes.IDENTITY, player.getUniqueId(), coin, 1000, ...);
```

Resolve the active identity UUID with
`MCIdentityProvider.instance.getActiveIdentity(playerUuid)` (or read
`event.getIdentityUuid()` from the MCIdentity equip/unequip events), and use that
UUID for **all** of your economy calls. For the full MCIdentity integration
guide, see its own [`wiki/api.md`](https://github.com/MCOriax/mcidentity).

---

## 5. Custom account types — clan, guild, and beyond

An economy account is keyed by an *account type* + *account UUID* pair, and the
account type is **free-form**. MCEconomy itself only uses
`AccountTypes.IDENTITY` (`"identity"`), so any other plugin can pick its own
type string and instantly get a fully separate money space for its own entities
— no schema change and no extra plugin required.

Pick one stable, lower-case type per entity kind and always key by the entity's
own UUID:

| Plugin | Account type | Account UUID |
| --- | --- | --- |
| MCEconomy (built-in) | `"identity"` | The MCIdentity identity UUID |
| A clan plugin | `"clan"` | The clan UUID |
| A guild plugin | `"guild"` | The guild UUID |

Because the pair is the key, a clan's wallets never collide with a guild's or an
identity's — **each clan and each guild has its own accounts, wallets, and
transaction logs**, exactly like each identity does.

### 5.1 Example — a clan treasury

A clan plugin can hold a clan-wide treasury on the **clan UUID** under its own
`"clan"` account type. Members deposit from their identity wallet into the clan
wallet with a single `transferBalance` call, which debits and credits atomically
and writes both log rows:

```java
import io.github.mcclauneck.mceconomy.api.models.AccountTypes;
import io.github.mcclauneck.mceconomy.api.models.Currency;
import java.util.UUID;

public class ClanTreasuryService {

    /** Account type owned by the clan plugin. */
    public static final String ACCOUNT_TYPE_CLAN = "clan";

    private final MCEconomyProvider economy;

    public ClanTreasuryService(MCEconomyProvider economy) {
        this.economy = economy;
    }

    /** Create the treasury account when the clan is founded (idempotent). */
    public void onClanCreated(UUID clanUuid) {
        economy.createAccount(ACCOUNT_TYPE_CLAN, clanUuid);
    }

    /** Move money from a member's identity wallet into the clan treasury. */
    public void deposit(UUID identityUuid, UUID clanUuid, Currency coin, long amount) {
        economy.transferBalance(
                AccountTypes.IDENTITY, identityUuid,   // from: the member's identity
                ACCOUNT_TYPE_CLAN, clanUuid,           // to:   the clan treasury
                coin, amount, UUID.randomUUID(), "clan_treasury", "deposit");
    }

    /** Read the treasury balance, e.g. for a clan info screen. */
    public void showBalance(UUID clanUuid, Currency coin) {
        economy.getBalance(ACCOUNT_TYPE_CLAN, clanUuid, coin)
               .thenAccept(balance -> { /* display it */ });
    }
}
```

### 5.2 Example — guild upkeep

A guild plugin works the same way with its own `"guild"` type — for example a
weekly upkeep charge taken straight from the guild's account:

```java
import io.github.mcclauneck.mceconomy.api.models.Currency;
import io.github.mcclauneck.mceconomy.api.models.LogScope;
import java.util.UUID;

public class GuildUpkeepService {

    /** Account type owned by the guild plugin. */
    public static final String ACCOUNT_TYPE_GUILD = "guild";

    private final MCEconomyProvider economy;

    public GuildUpkeepService(MCEconomyProvider economy) {
        this.economy = economy;
    }

    /** Charge the weekly upkeep; disband logic runs when funds are short. */
    public void chargeUpkeep(UUID guildUuid, Currency gold, long upkeep) {
        economy.removeBalance(ACCOUNT_TYPE_GUILD, guildUuid, gold, upkeep,
                LogScope.SPEND, UUID.randomUUID(), "guild_upkeep", "weekly")
               .exceptionally(err -> {
                   // "Insufficient funds" — flag the guild as behind on upkeep.
                   return null;
               });
    }
}
```

**Rules for custom account types:**

1. Choose one stable, lower-case account type per entity kind (`"clan"`,
   `"guild"`, `"town"`, ...) and never reuse another plugin's type for your own
   entities.
2. Key by the entity's own UUID. Do not key clan or guild money by a player or
   identity UUID — members change, the entity stays.
3. Keep using a fresh transaction UUID and a descriptive
   `referenceType`/`referenceId` pair on every mutating call so the shared
   `economy_logs` audit trail stays meaningful.
