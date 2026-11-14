# Changelog — 2.1.0

**Released:** 2026-11-15

A minor release. Account rows move to the shared `accounts` table that MCMultiverse, MCRealm
and the other account-keyed plugins create with the same statement, and MCEconomy loads after
MCMultiverse and MCRealm when they are installed.

## Added

- The shared `accounts` table, created on every SQL backend with the canonical statement of
  its dialect, read from `schema/accounts/{dialect}.sql` by `SharedAccounts` (#49).
- `SharedAccountsTest`, pinning the SHA-256 of each dialect's statement (#49).
- `MCMultiverse` and `MCRealm` in every `softdepend`, after `MCAgents` (#50).

## Changed

- `economy_wallet`, `economy_transactions`, `economy_logs` and the PostgreSQL archive tables
  reference `accounts (account_type, account_uuid)` (#49).
- The account row is inserted if missing before the first wallet, transaction or log write (#49).
- MongoDB keeps account documents in the `accounts` collection (#49).

## Removed

- The `economy_account` table and collection, and `archive_economy_account` (#49).

## Upgrading

There is no migration: the plugin was not in production. The central backend is unchanged.
