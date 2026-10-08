# ktc-sqldelight

SQLDelight code generation for JetBrains Kotlin Toolchain. Extracted from
[Kotgent](https://github.com/Heapy/kotgent), with configurable database names and source directories.
Pinned to Kotlin Toolchain **0.13.0**, Kotlin **2.4.20**, SQLDelight **2.3.2** and SQLite dialect **3.38**.

## Install

Use [ktc-plugins 0.2.0 or newer](https://github.com/Heapy/ktc-plugins/releases/tag/v0.2.0)
from the consuming project:

```sh
./ktc-plugins add Heapy/ktc-sqldelight --branch main --enable-in .
```

Use your module path instead of `.` for a non-root module. The installer vendors the plugin sources,
registers the module, imports explicitly exported library aliases, and pins the producer commit and
catalog in its lockfile. Commit the installed sources, manifests, catalog and project configuration.
For a specific revision use `--commit <full-40-character-SHA>` instead of `--branch main`.

Configure the consuming `module.yaml`:

```yaml
plugins:
  sqldelight:
    enabled: true
    packageName: com.example.db
    className: AppDatabase
    sourceDirectory: sqldelight

dependencies:
  - $libs.ktc.sqldelight.runtime
  # Choose a driver for your target; this example is for Kotlin/Native.
  - $libs.ktc.sqldelight.native.driver
```

`packageName` is required; `className` defaults to `Database`, and `sourceDirectory` defaults to
`sqldelight`, relative to the consuming module. Place `.sq` files under package directories, for example
`sqldelight/com/example/db/Person.sq`. The Toolchain module name is supplied automatically.
Generation participates in the normal build and removes stale outputs when queries are deleted.

## Versions and catalogs

The producer's `gradle/libs.versions.toml` is the single source of the SQLDelight version for both
compiler dependencies and exported runtime libraries. The installer resolves the plugin's private
compiler aliases to literal coordinates and exports these consumer aliases:

| Consumer alias | Library |
|---|---|
| `$libs.ktc.sqldelight.runtime` | `app.cash.sqldelight:runtime` |
| `$libs.ktc.sqldelight.native.driver` | `app.cash.sqldelight:native-driver` |
| `$libs.ktc.sqldelight.sqlite.driver` | `app.cash.sqldelight:sqlite-driver` (JVM/JDBC) |
| `$libs.ktc.sqldelight.coroutines.extensions` | `app.cash.sqldelight:coroutines-extensions` |

Exports only add catalog entries. Applications explicitly select module dependencies and platform
drivers. Use the exported aliases to keep declared versions aligned; separate literal dependencies or
dependency-resolution overrides can still select a different runtime. Plugin integration releases and
SQLDelight versions are independent. Do not edit installed plugin sources or managed catalog blocks;
update through `ktc-plugins update sqldelight`, review its diff, then run your application tests.

## Scope

- One synchronous database per consuming module, generated from `.sq` schema/query files.
- SQLite 3.38 dialect; no configurable dialect or asynchronous generation in this version.
- `.sqm` migrations are not generated or verified. Applications own database upgrades; a generated
  schema for a fresh database does not establish upgrade correctness.
- No multi-database dependencies or platform-specific source-set configuration.
- The Gradle-free compiler environment is adapted from SQLDelight 2.3.2. Its optimistic-lock annotator
  is unavailable in the selected compiler artifacts and is omitted. See `NOTICE` and its source header.

This is an independent integration, not an official JetBrains or SQLDelight plugin. Distribution is
through source installation; it is not a Maven-published Toolchain plugin.

## Develop and verify

```sh
./kotlin build
./kotlin test
ktc-plugins validate
```

The example uses a custom schema directory and database class, compiles the generated Kotlin, creates
an actual in-memory SQLite database with the matching JDBC driver, and executes typed queries.
Generator tests cover invalid SQL diagnostics and removal of stale generated sources. CI runs the
build and tests on Linux and macOS, and validates packaging using ktc-plugins 0.2.0.
It also runs `scripts/test-install.sh /path/to/ktc-plugins`: this installs the exact producer commit
into a fresh consumer and repeats the SQLite test using the exported library aliases.

Compiler upgrades must update the producer catalog, review the adapted environment against upstream,
and pass generation/compilation/runtime tests. Kotgent additionally exercises the Native driver.
