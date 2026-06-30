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

Usable. The **`java.sql.*` layer is implemented and tested** (37 unit tests) and
**live-verified** end-to-end against a real D1 — both the local miniflare DB
(`mode=local`) and the cloud DB (`mode=remote`) — through DriverManager →
`DatabaseMetaData` introspection → `Statement` / `PreparedStatement`, including
INSERT/UPDATE/DELETE writes. The remaining checks are Mac-side only: a live
SSH-proxy run and the DataGrip GUI walkthrough (the introspection path itself is
already verified programmatically).

Two known wrangler-imposed limits: a per-query `wrangler` spawn is ~1 s (cached
for schema introspection); and `mode=local` wrangler does not report a row-change
count, so `executeUpdate` returns 0 there even though the write succeeds (remote
reports it correctly).

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

Everything is configured here; values may also be passed as JDBC properties
(e.g. DataGrip's advanced tab). `db` is the only required one.

| param | default | meaning |
|---|---|---|
| `db` | — (required) | D1 database name |
| `transport` | `normal` | `normal` (run wrangler locally) or `ssh` / `proxy` (run it on `host`) |
| `mode` | `local` | `local` (miniflare) or `remote` (cloud) |
| `host` | — | SSH target for proxy mode (`user@ip` or a `~/.ssh/config` alias) |
| `dir` | — | working directory where wrangler runs (the project root) |
| `env` | — | wrangler named environment (e.g. `production`) |
| `config` | — | path to `wrangler.jsonc` |
| `persist` | — | `--persist-to` dir for `mode=local` (e.g. `.wrangler/state`) |
| `wrangler` | `wrangler` | the wrangler command, token-split (e.g. `pnpm exec wrangler`) |
| `ssh` | `ssh` | the ssh command for `transport=ssh`, token-split |
| `ssh-opts` | — | extra non-secret ssh args, token-split (e.g. `-p 2222 -o ProxyJump=bastion`) |
| `timeout` | `120` | per-command wrangler timeout, seconds |
| `probe` | `true` | run a `SELECT 1` connectivity check on connect |
| `readonly` | `false` | reject all writes on this connection |
| `cache` | `true` | cache schema introspection per connection |

### Two transport modes

- **`normal`** — run `wrangler` on the machine running the JDBC client.
- **`proxy`** (`transport=ssh`) — run `wrangler` on a remote host (e.g. a dev
  server) over SSH; the client side needs only an SSH client, no wrangler and no
  Cloudflare token locally. The **server side needs only `wrangler` + `sshd`** —
  nothing project-specific; every parameter (`dir`/`config`/`persist`/`db`/…)
  travels in the URL.

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

The CLI runs one query straight through the wrangler core — handy for smoke
tests without a JDBC client. It spawns `wrangler`, so wrangler must be reachable,
and `--local` needs `--persist-to`:

```
java -cp build/libs/d1-jdbc-driver-*.jar io.github.dravengarden.d1.cli.MainKt \
  "jdbc:d1:?db=mydb-local&mode=local&dir=/path/to/project&config=wrangler.jsonc&persist=.wrangler/state" \
  "SELECT * FROM accounts"
```

(Add `&wrangler=pnpm exec wrangler` if wrangler is a project-local install
rather than on PATH.)

## Use in DataGrip

1. **Register the driver.** *Settings → Database → Drivers → +* (or *Database
   Explorer → + → Driver*). Name it `Cloudflare D1`.
   - **Driver Files**: add `build/libs/d1-jdbc-driver-<version>.jar`.
   - **Class**: `io.github.dravengarden.d1.jdbc.D1Driver` (DataGrip should
     auto-detect it from the JAR's SPI registration).
   - **Dialect**: **SQLite** — D1 *is* SQLite, and the introspector relies on it.
   - **URL template** (optional): `jdbc:d1:?db=[{db}]&mode=[{mode}]`.
2. **Create a data source** using that driver and paste a `jdbc:d1:?…` URL.
   Everything is in the URL — no server-side wrapper or config file:

   ```
   # local miniflare — wrangler runs on this machine
   jdbc:d1:?db=mydb-local&mode=local&dir=/path/to/project&config=wrangler.jsonc&persist=.wrangler/state

   # remote cloud DB — wrangler runs on this machine
   jdbc:d1:?db=mydb&mode=remote&env=production&dir=/path/to/project&config=wrangler.jsonc

   # proxy — wrangler runs on a remote server over SSH; this machine needs only ssh
   jdbc:d1:?transport=ssh&host=myserver&db=mydb-local&mode=local&dir=/path/on/server&config=wrangler.jsonc&persist=.wrangler/state
   ```
3. **Test Connection** — the driver runs `SELECT 1`; a green check means it
   reached the D1. Then expand the schema tree to browse tables/columns and run
   SQL in a console.

Notes:
- **`wrangler` defaults to the `wrangler` on PATH.** If your project pins a
  local wrangler instead of a global one, add `wrangler=` — e.g.
  `wrangler=pnpm exec wrangler`, or the path to `node_modules/.bin/wrangler`.
  All paths (`dir`/`config`/`persist`) are resolved on whichever machine runs
  wrangler (the remote, for `proxy`).
- The **Password** field can hold a Cloudflare API token; pass it as the
  `password` JDBC property for `--remote` if you don't keep the token in the
  project's `.env`.
- For the `proxy` transport, a Mac `~/.ssh/config` with connection multiplexing
  avoids re-handshaking on every query:

  ```
  Host myserver
      HostName 10.0.0.5
      User me
      ControlMaster auto
      ControlPath ~/.ssh/cm-%r@%h:%p
      ControlPersist 5m
  ```

## License

Apache-2.0. See [`LICENSE`](LICENSE).
