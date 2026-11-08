---
name: gradle-plugin-adoption
description: Task record for moving mceconomy's root build.gradle onto the shared io.github.mcengine.build-logic convention plugin, released as 1.7.0.
---

# Gradle plugin adoption

**Status:** done

## Goal

Replace the build logic this repository's root `build.gradle` copies from the other
plugins with `id 'io.github.mcengine.build-logic' version '0.1.0'` from
MCEngine/gradle-plugin, without changing what the build produces.

## Objective

* `settings.gradle` resolves the plugin from
  `https://maven.pkg.github.com/MCEngine/gradle-plugin`.
* The root `build.gradle` is the `plugins { }` block plus an `mcengineBuild { }` block
  for what differs from the plugin's defaults.
* No module names a Shadow version; the plugin supplies Shadow 9.4.1.
* The published artifactIds become `mceconomy-api`, `mceconomy-common`, `mceconomy-bukkit` (were the bare `api`, `common`, `bukkit`).
* `./gradlew build` passes; Publish and Release green on 1.7.0.

## Decisions

The owner was away and asked for the recommended option at every fork.

* Release as **1.7.0**.
* Instruction files under `.agents/rules/` that describe the old root build are left for
  the owner as discovery findings.

## Tasks

| # | Title | Branch | PR |
|---|---|---|---|
| 1 | Plan and task record | `plan/gradle-plugin-adoption` | #37 |
| 2 | Apply the convention plugin | `build/gradle-plugin` | #38 |
| 3 | Release 1.7.0 | `release/1.7.0` | #39 |

## Progress

### Task 1 — plan/gradle-plugin-adoption

Wrote this record.

### Task 2 — build/gradle-plugin

`settings.gradle` gained the `pluginManagement` repository for the plugin; the root
`build.gradle` is now the plugin plus `mcengineBuild { }`; module builds dropped the
Shadow version. api, common and bukkit now declare their own `mavenJava` publication (the root used to); artifactIds become `mceconomy-*`; dropped the root `shadow apply false`; updated `wiki/api.md` and `wiki/environment.md`. Verified locally with `./gradlew build` against the plugin
published to Maven Local.

### Task 3 — release/1.7.0

`project-version` → 1.7.0, `wiki/logs/1/7/0/CHANGELOG.md`, the log index row
where the repository keeps one, and the PR column. Record closed.
