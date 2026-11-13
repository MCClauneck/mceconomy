# Currencies

[← Back to README](../README.md)

MCEconomy's default `config.yml` gives a server four **base coins**, which are the server's
money, and **credit**, the top-up currency. Every other currency is a **custom currency**: the
default config also ships two of those, `point` and `token`, as ready-made examples. The
defaults live in each platform's `config.yml`
(`platforms/{spigotmc,papermc,foliamc}/src/main/resources/config.yml`).

**Contents**

1. [The default currencies](#1-the-default-currencies)
2. [Base coins are separate currencies](#2-base-coins-are-separate-currencies)
3. [Credit](#3-credit)
4. [Reserved codes](#4-reserved-codes)
5. [Adding a custom currency](#5-adding-a-custom-currency)
6. [Before you add a currency](#6-before-you-add-a-currency)

---

## 1. The default currencies

| Code | Display name | Transferable | Kind |
| --- | --- | --- | --- |
| `coin` | Coin | yes | Base coin |
| `copper` | Copper | yes | Base coin |
| `silver` | Silver | yes | Base coin |
| `gold` | Gold | yes | Base coin |
| `credit` | Credit | no | Top-up currency |
| `point` | Point | no | Custom currency (example) |
| `token` | Token | no | Custom currency (example) |

*Transferable* means the currency can move between accounts, for example with `/pay`. A
currency that is not transferable stays with the account that received it.

Remove `point` or `token` if your server has no use for them. Currencies already stored in the
database are never deleted.

---

## 2. Base coins are separate currencies

Each base coin is a currency of its own, with its own wallet and its own purpose. There is
**no exchange rate** between them: 100 coin is not 1 copper, and MCEconomy never converts one
into another. Decide what each coin is for (what earns it and what it buys) before players
start earning it.

---

## 3. Credit

Credit is the **top-up currency**: players buy it, usually with real money, rather than earn
it in game. It is not transferable, so credit a player paid for cannot be passed to another
account. Give it out through your store or an admin command, and spend it on whatever your
server sells for credit.

---

## 4. Reserved codes

The four base coins and credit (`coin`, `copper`, `silver`, `gold`, `credit`) belong to the
server's own economy. An add-on that needs a currency for a feature of its own uses a custom
currency instead, so that feature never drains or inflates the server's money. MCPass, for
example, refuses these five codes as the currency that buys pass levels.

---

## 5. Adding a custom currency

Add an entry under `currencies:` in `plugins/MCEconomy/config.yml` and restart. A missing
currency is created on enable, and a changed display name or transferable flag is applied on
the next enable:

```yaml
currencies:
  spirit:
    display_name: Spirit
    transferable: false
    texture: <base64>        # head texture shown in the /balance GUI (optional)
```

---

## 6. Before you add a currency

> **Warning.** Don't create too many custom base coins, or planning the server's future gets
> hard. When you create a coin, decide its purpose at the same time: for example `point` for
> events, `cyber` for a cyber world (if you run several worlds), or `spirit` for enchanting.
>
> Inflation is the other critical issue. When too much of a currency piles up, it becomes
> worthless, so plan a way to take it back out of the economy as well: a sink such as taxes,
> purchases that return nothing, a tax on transfers, or a tax on auctions.

A short check before adding one:

- **Purpose.** What earns it, and what does it buy that no other currency already does?
- **Sink.** Where does it leave the economy, and does that keep pace with what comes in?
- **Transferable?** A currency players can trade is easier to farm and move; lock it when it
  rewards something personal.
