# gradle.properties Standard

The single root `gradle.properties` drives identity and versioning for every module in the
monorepo. It MUST ALWAYS provide these variables:

```properties
git-org-name=
git-repository=
git-repository-id=
project-group=
project-version=
```

- `git-org-name` — the GitHub organisation (`MCClauneck`).
- `git-repository` — the repository name (`mceconomy`); also the GitHub Packages target.
- `git-repository-id` — optional numeric repository id (may be empty).
- `project-group` — the Maven group (`io.github.mcclauneck`), shared by every module.
- `project-version` — the Major.Minor.Patch version, shared by every module. See
  [`versioning.md`](versioning.md).

`group` and `version` are applied once for all subprojects from the root `build.gradle`
`allprojects` block, so individual module build scripts do not redeclare them.
