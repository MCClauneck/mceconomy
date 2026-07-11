# Agents Central Index

This is the routing hub for AI agent instructions in the **`mceconomy`** monorepo.
Always read this file first to locate the context of a new session.

## File Index
- `.agents/architecture/structure.md`: Monorepo module layout, publishing rules, and dependency wiring.
- `.agents/credentials/github.md`: Credentials and security environment variables.
- `.agents/git/commits.md`: Conventional commits specification.
- `.agents/git/workflow.md`: Git branching and PR workflow.
- `.agents/gradle/properties.md`: Root `gradle.properties` standard variables.
- `.agents/gradle/versioning.md`: Semantic (Major.Minor.Patch) versioning.
- `.agents/platforms/papermc/concurrency.md`: PaperMC concurrency, async tasks, and thread-safety rules.
- `.agents/platforms/spigotmc/concurrency.md`: SpigotMC concurrency, async tasks, and thread-safety rules.
- `.agents/platforms/foliamc/concurrency.md`: FoliaMC concurrency, async tasks, and thread-safety rules.
- `.agents/rule/core.md`: Core directives and local state management.
- `.agents/rule/style.md`: General code style guidelines.

## Module skills
Platform modules ship a `SKILLS.md` at their module root — read it before writing or modifying
code in that module:
- `bukkit/SKILLS.md`, `spigotmc/SKILLS.md`, `papermc/SKILLS.md`, `foliamc/SKILLS.md`.
