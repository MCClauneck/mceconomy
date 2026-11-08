# Environment

This document describes the environment variables and infrastructure configuration used when
building, publishing, and running MCEconomy. Runtime plugin behavior is configured through each
platform's `config.yml` (see the [`README.md`](../README.md)); the variables below cover the
build and the optional external databases.

## Build and publish variables

GitHub Packages authentication is required to resolve the MCIdentity `compileOnly` artifacts
(building the `bukkit` module) and to publish the `api`, `common`, and `bukkit` libraries. The
token needs the `read:packages` scope to build and `write:packages` to publish.

The `bukkit` module, the [`io.github.mcengine.build-logic`](https://github.com/MCEngine/gradle-plugin)
convention plugin the root build applies, and publishing all resolve the credentials from
`GITHUB_ACTOR` and `GITHUB_TOKEN`:

```bash
export GITHUB_ACTOR=your_github_username
export GITHUB_TOKEN=your_github_token
```

## Optional jar export path

The `engine` module exposes an `exportJar` task that copies the freshly built universal plugin
jar to the repository root `build/` directory, and additionally to a local path — for example a
test server `plugins/` folder — when one is set. The local path is optional; when unset, only
the root `build/` copy is made.

```bash
# Copy the built universal jar to this path after a build.
export ENGINE_PLUGIN_JAR_PATH=your_server_plugins_path
```

## External database examples

SQLite is the default and needs no external service. The examples below start a database only
when MySQL, PostgreSQL, or MongoDB is selected as `database.type`. Replace the placeholder
credentials and keep them out of version control.

```yaml
# docker-compose.yml — optional database services for MCEconomy.
services:
  mysql:
    image: mysql:8
    environment:
      MYSQL_DATABASE: mceconomy
      MYSQL_USER: your_mysql_user
      MYSQL_PASSWORD: your_mysql_password
      MYSQL_ROOT_PASSWORD: your_mysql_root_password
    ports:
      - "3306:3306"

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: mceconomy
      POSTGRES_USER: your_postgres_user
      POSTGRES_PASSWORD: your_postgres_password
    ports:
      - "5432:5432"

  mongodb:
    image: mongo:7
    environment:
      MONGO_INITDB_DATABASE: mceconomy
    ports:
      - "27017:27017"
```

The matching connection details are set in the `database` section of each platform's
`config.yml`, not through environment variables.
