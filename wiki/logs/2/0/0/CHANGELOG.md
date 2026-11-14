# Changelog — 2.0.0

**Released:** 2026-11-14

A major release. MCIdentity 5.0.0 drops identities: a player has one character, keyed by
their own UUID, whatever profession they wear. Every wallet now belongs to the player on
every server, and MCEconomy no longer integrates with MCIdentity.

## Changed

- Every wallet uses account type `player` and the player UUID; `PlayerAccountListener`
  creates the account on join (#46).
- The AI tool port reads the account with `isAccountReady()` and `accountUuid(UUID)` (#46).
- `softdepend` is `[MCAgents]` only (#46).

## Removed

- `IdentitySource`, `AccountSource.identities(...)` and `AccountTypes.IDENTITY` from the API (#46).
- `MCIdentityBridge`, `IdentityAccountListener` and `AbstractMCEconomyPlugin#getIdentities()` (#46).

## Upgrading

There is no migration: the plugin was not in production. Plugins that used
`AccountTypes.IDENTITY` key money by `AccountTypes.PLAYER` and the player UUID instead.
