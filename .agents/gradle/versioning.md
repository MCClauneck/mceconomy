# Versioning Specification

## Format

The project version MUST use Semantic Versioning — `Major.Minor.Patch`:

```text
{major}.{minor}.{patch}
```

An optional pre-release type suffix is allowed:

```text
{major}.{minor}.{patch}-{type}   # e.g. 1.0.0-beta
```

`project-version` in the root `gradle.properties` is the single source of truth and applies to
every module in the monorepo.

## Semantics

- **Major** — incompatible / breaking API changes.
- **Minor** — new, backwards-compatible functionality.
- **Patch** — backwards-compatible bug fixes.

## Release Check

Before merging, verify `project-version` was updated when the change warrants it. If it has not
changed, ask the maintainer whether to bump it and, if so, apply the correct Major/Minor/Patch
increment.
