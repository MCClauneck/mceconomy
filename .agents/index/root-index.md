---
name: root-index
description: Index of every index in this repository — the router an agent reads first to pick the one branch that matches the task.
---

# Root Index

**Scope:** `.agents/` and `wiki/`
**Parent:** none — this is the root.

## Child Indexes

| Index | Scope | Load when |
|---|---|---|
| [`memory-index.md`](memory-index.md) | `.agents/memory/` | Every session, to continue earlier work instead of restarting it. |
| [`project-wiki-index.md`](project-wiki-index.md) | `wiki/` | You need the human documentation: requirements, API, system, currencies, AI tools, environment. |
| [`project-wiki-logs-index.md`](project-wiki-logs-index.md) | `wiki/logs/` | You need a past release's changelog, or are recording a new release. |
