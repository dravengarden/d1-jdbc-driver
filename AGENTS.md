# AGENTS.md — d1-jdbc-driver

Working guide for coding agents. See `README.md` for the project overview and
usage.

## What this is

A JVM **JDBC driver** for **Cloudflare D1** whose backend is the **`wrangler`
CLI** (`wrangler d1 execute --json`) — not a database connection and not a SQLite
file. A JDBC client (DataGrip, DBeaver) can then query **both** the local dev D1
(miniflare) and the remote Cloudflare D1 through one driver, and treat it as
**SQLite**.

## Stack

- Kotlin 2.1 — `explicitApi()` + `allWarningsAsErrors = true`
- Gradle (Kotlin DSL) + version catalog (`gradle/libs.versions.toml`)
- JDK 21 toolchain
- kotlinx-serialization-json (parse wrangler output)
- Shadow plugin (one fat JAR to load into DataGrip)
- JUnit 5 / kotlin-test
- Nix flake dev shell (`jdk21` + `gradle`)

## Build / test / run

Everything runs inside the dev shell:

```
nix develop            # provides jdk21 + gradle
gradle build           # compile + test + fat JAR
gradle test
```

Fat JAR: `build/libs/d1-jdbc-driver-<version>.jar` — this is what you load in
DataGrip.

CLI smoke of the core (before the JDBC layer is usable). It spawns `wrangler`, so
node/pnpm must be reachable; `--local` needs `--persist-to`:

```
java -cp build/libs/d1-jdbc-driver-*.jar io.github.dravengarden.d1.cli.MainKt \
  "jdbc:d1:?db=<db>&mode=local&dir=<project>&config=<wrangler.jsonc>&persist=.wrangler/state&wrangler=pnpm exec wrangler" \
  "SELECT ..."
```

## Layout

```
src/main/kotlin/io/github/dravengarden/d1/
  model/      WranglerResult, QueryResult (@Serializable)
  transport/  Transport (where a command runs): LocalTransport / SshTransport
  core/       D1Config (URL parsing) + Engine (how a query runs):
              Wrangler (wrangler d1 execute), SqliteEngine (read the local
              <hash>.sqlite via sqlite3), HttpEngine (remote D1 REST API via
              curl; token piped, never in argv). Engine × Transport are
              orthogonal — any engine runs local or over ssh.
  jdbc/       D1Driver + the java.sql.* layer (Connection/Statement/
              PreparedStatement/ResultSet/ResultSetMetaData/DatabaseMetaData).
              Abstract*… are throwing stub bases; D1*… are the concrete classes,
              all routing SQL through D1Connection.execute → the Engine.
  cli/        Main (smoke runner over the core)
src/main/resources/META-INF/services/java.sql.Driver   (SPI registration)
src/test/kotlin/...                                     (unit tests)
```

## JDBC URL

```
jdbc:d1:?transport=<normal|ssh>&host=<sshHost>&dir=<remoteDir>&db=<name>&mode=<local|remote>&env=<env>&config=<path>&persist=<dir>&wrangler=<command>
```

- **One URL carries everything.** Full param list + defaults live in `README.md`.
  Besides the core params above: `engine` (auto/wrangler/sqlite/http) + its
  `sqlite`/`file`/`account`/`database-id`/`env-file`/`token-var`; `ssh` /
  `ssh-opts`; `timeout`; `probe` (connect-time CLI preflight + `SELECT 1`);
  `access` (`read`/`write`/`ddl` guardrail, **default read**); `cache`. Every
  value may also arrive as a JDBC property; only `db` is required.
- **Access guardrail** (`D1Connection.requireAccess` + `classifyStatement`):
  read-only by default, client-side only — it refuses to *send* over-privileged
  SQL, it is NOT a server-side boundary (use a scoped CF token for that).
- **Preflight** (`Engine.checkAvailable` → `requireTool`): on connect, checks the
  engine's CLI is on the host's PATH and distinguishes "tool missing" from "host
  unreachable" with actionable English messages.
- **No secrets in the URL.** SSH auth is delegated to the OS `ssh` client +
  `~/.ssh/config` (keys, known_hosts, ControlMaster); `ssh-opts` is for non-secret
  flags only (port, jump host, identity *path*).
- DataGrip's **User / Password** arrive as JDBC properties. Intended use:
  `password` = a Cloudflare API token for `--remote` in `normal` mode (DataGrip
  stores it in the OS keychain). Proxy needs no credential in DataGrip.

## Transport: two modes

- **`normal`** — run `wrangler` locally (`ProcessBuilder`). wrangler must be on
  the machine running the driver.
- **`proxy`** (`transport=ssh`) — `ssh <host> 'wrangler …'`; wrangler runs on the
  remote (hawk). The local side needs only SSH.

`mode=local` (`--local`) must run where `.wrangler/state` lives (hawk → `proxy`).
`mode=remote` can run either side.

## wrangler is NOT bundled

The driver shells out to an **external** `wrangler` (configurable via `wrangler=`
in the URL). wrangler is a Node CLI (a JVM JAR cannot embed/run it) and must
match the project's version + `wrangler.jsonc` + `.wrangler/state`; a bundled copy
would drift. Keep the JAR free of Node.

## Conventions

- **English only** — no other language anywhere in the repo (code, comments,
  docs, commit messages).
- `explicitApi()` + `allWarningsAsErrors`: every public declaration needs an
  explicit visibility modifier and return type; keep the build warning-free.
- **No secrets** in code or in JDBC URLs. The CF token lives in wrangler's env
  (e.g. hawk's `.env`) or DataGrip's password store.
- Commit subject: imperative, English.

## Gotchas (already learned)

- **`--persist-to` is required for `mode=local`.** Without it, wrangler opens a
  different default DB and reports `no such table`. The `persist=` URL param maps
  to `--persist-to`.
- **wrangler/dotenv prints a banner to stdout** (e.g. `… loaded .env`);
  `Wrangler.parse` slices from the first `[` so a banner does not break JSON
  parsing. (This is why the driver needs no clean-stdout wrapper — keep config in
  the URL, not in a server-side script.)
- **`Transport` is a plain interface, not sealed** — a sealed interface cannot be
  implemented from the test module.
- A per-query `wrangler` spawn is ~1.1 s (node + miniflare startup), plus network
  for `--remote`. Fine for v1; cache introspection, and consider a persistent
  hawk-side helper later.
- **kotlinx `JsonNull` IS a `JsonPrimitive`** whose `.content` is the string
  `"null"`. Any cell→value coercion must test `is JsonNull` *before* reading
  `content`, or a SQL NULL silently becomes the text `"null"` (see `D1Types`).
- **The `java.sql.*` interfaces have no default methods** — a class must
  implement every member. The `Abstract*` bases throw
  `SQLFeatureNotSupportedException` for all of them; the `D1*` concrete classes
  override only what a browsing/SELECT client needs, so an unimplemented call
  fails loudly rather than misbehaving.
- **`mode=local` wrangler reports no change count.** A local write's `meta` is
  just `{"duration":0}` (no `changes`/`last_row_id`), so `executeUpdate` returns
  0 even though the write succeeds. Remote D1 returns the real `changes`. Don't
  treat a 0 from a local write as "nothing happened".
- **Schema introspection is cached per connection** (`D1Connection.introspect`)
  and **cleared on any write** (`invalidateIntrospection`), because DataGrip
  re-issues the same `sqlite_master`/`PRAGMA` reads dozens of times per sweep and
  each is a ~1 s spawn. User `SELECT`s are never cached.

## Status

Slice 1 — scaffold + transport (normal/ssh) + wrangler core + `D1Driver`
registration + CLI + unit tests — is **done and green**; the CLI queries a real
D1.

Task 1 — the `java.sql.*` connection layer (`D1Connection`, `D1Statement`,
`D1PreparedStatement`, `D1ResultSet`, `D1ResultSetMetaData`,
`D1DatabaseMetaData`, all delegating to the wrangler core) — is **implemented
and green** (37 unit tests, fat JAR builds). `D1Driver.connect` opens a
connection after a `SELECT 1` probe. **Live-verified** end-to-end against the
real kuaitu D1 — both `mode=local` (miniflare) and `mode=remote`
(`kuaitu-preview` on Cloudflare) — through DriverManager → DatabaseMetaData
introspection → Statement/PreparedStatement, including INSERT/UPDATE/DELETE.
Also done: write support (`executeUpdate`), per-connection introspection
caching, and GitHub Actions CI. Everything is URL-driven — for `proxy` the host
side needs only the engine's CLI (`wrangler` / `sqlite3` / `curl`) + `sshd`,
nothing project-specific. Engines: `sqlite` (fast local, read-only) and `http`
(remote D1 REST API, token piped) are both live-verified against the real
kuaitu D1. The only unverified link is the literal client→server `ssh` hop (its
command construction is unit-tested). Optional deferred idea: a persistent
server-side query helper to kill the ~1 s per-query node startup for
`engine=wrangler`.
