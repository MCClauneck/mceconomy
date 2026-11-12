# Changelog — 1.8.0

**Released:** 2026-11-12

A minor release. Every MCEconomy AI tool names a permission node, so MCAgents 6.9.0 and
later only offer a player the tools they may use and no tokens go on the rest.

## Added

- `PluginTool#getPermission()`, answered to MCAgents through the tool adapter (#41).
- `mcclauneck.mceconomy.tool.economy_list_currencies` (`default: true`) for `economy_list_currencies` (#41).
- `mcclauneck.mceconomy.tool.economy_get_my_balances` (`default: true`) for `economy_get_my_balances` (#41).

## Upgrading

Drop in the new jar. Players keep the tools they have today; deny a node to hide one.
Older MCAgents versions ignore the nodes.
