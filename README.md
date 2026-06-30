# d1-jdbc-driver

A **JDBC driver for Cloudflare D1**, backed by **`wrangler`** — not a database
connection and not a SQLite file. Any JDBC client (JetBrains DataGrip, DBeaver,
…) can connect to:

- the **local dev D1** (miniflare's SQLite, created by `wrangler dev` / `--local`), and
- the **remote production / preview D1** (the cloud database),

through the **same driver**, and treats it as **SQLite**.

The backend is simply `wrangler d1 execute --json`: the driver builds the command,
runs it (locally or over SSH), and parses the JSON back into a JDBC result set.
No SQLite native library, no file access, no proxy server. Precedent: JetBrains
ships [`redis-jdbc-driver`](https://github.com/DataGrip/redis-jdbc-driver) and
[`mongo-jdbc-driver`](https://github.com/DataGrip/mongo-jdbc-driver) — JDBC
drivers wrapping non-SQL backends — so wrapping a CLI is the same pattern.

Stack: **Kotlin 2.1 + Gradle (Kotlin DSL)**, JDK 21, kotlinx-serialization,
Shadow (fat JAR), JUnit 5. Builds on a Nix flake dev shell.

## Status

Early. The **core is complete and tested** — the included CLI already queries a
real D1. The `java.sql.*` connection layer (so a JDBC client can actually
connect) is **not implemented yet**. See [`TASKS.md`](TASKS.md).

## How it works

```
JDBC client ──▶ d1-jdbc-driver
  executeQuery(sql)
     ─▶ run:  wrangler d1 execute <db> [--local --persist-to <dir> | --remote] --json --command "<sql>"
     ◀─ parse JSON results[] ─▶ JDBC ResultSet
  DatabaseMetaData
     getTables()  ─▶ SELECT name, type FROM sqlite_master WHERE type IN ('table','view')
     getColumns() ─▶ PRAGMA table_info(<table>)
  Dialect: set to SQLite in the client
```

### Connection URL

Everything is in one URL; SSH auth is delegated to the OS `ssh` client.

```
jdbc:d1:?transport=ssh&host=hawk&dir=/path/to/project&db=mydb-local&mode=local&config=wrangler.jsonc&persist=.wrangler/state
jdbc:d1:?db=mydb-production&mode=remote&env=production            # transport defaults to normal
```

| param | meaning |
|---|---|
| `transport` | `normal` (run wrangler locally) or `ssh` / `proxy` (run it on `host`) |
| `host` | SSH target for proxy mode (`user@ip` or a `~/.ssh/config` alias) |
| `dir` | working directory where wrangler runs (the project root) |
| `db` | D1 database name |
| `mode` | `local` (miniflare) or `remote` (cloud) |
| `env` | wrangler named environment (e.g. `production`) |
| `config` | path to `wrangler.jsonc` |
| `persist` | `--persist-to` dir for `mode=local` (e.g. `.wrangler/state`) |
| `wrangler` | the wrangler command, token-split (default `wrangler`) |

### Two transport modes

- **`normal`** — run `wrangler` on the machine running the JDBC client.
- **`proxy`** (`transport=ssh`) — run `wrangler` on a remote host (e.g. a dev
  server) over SSH; the client side needs only SSH access, no wrangler and no
  Cloudflare token locally.

`mode=local` must run where the `.wrangler/state` lives; `mode=remote` can run
either side.

### wrangler is an external dependency — not bundled

The driver shells out to an external `wrangler` (path/command set by `wrangler=`).
wrangler is a Node CLI and must match the project's version + config + state, so a
bundled copy would drift. The JAR carries no Node.

## Build

Inside the dev shell (`nix develop` → JDK 21 + Gradle):

```
gradle build      # compile + test + fat JAR
```

Output: `build/libs/d1-jdbc-driver-<version>.jar`.

## Try the core (CLI)

The CLI exercises the wrangler core before the JDBC layer exists. It spawns
`wrangler`, so node/pnpm must be reachable, and `--local` needs `--persist-to`:

```
java -cp build/libs/d1-jdbc-driver-*.jar io.github.dravengarden.d1.cli.MainKt \
  "jdbc:d1:?db=mydb-local&mode=local&dir=/path/to/project&config=edge/worker/wrangler.jsonc&persist=.wrangler/state&wrangler=pnpm exec wrangler" \
  "SELECT * FROM accounts"
```

## Use in DataGrip

(Once the JDBC layer lands — see `TASKS.md`.) Add a **user driver** pointing at
the fat JAR, set the data source **SQL dialect to SQLite**, and use a
`jdbc:d1:?…` URL. The Password field can hold a Cloudflare API token for
`--remote` in `normal` mode.

## License

Apache-2.0. See [`LICENSE`](LICENSE).
