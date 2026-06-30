# AGENTS.md — d1-jdbc-driver

Working guide for coding agents. See `README.md` for the project overview and
`TASKS.md` for the remaining work.

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
  transport/  Transport (interface) + LocalTransport (normal) + SshTransport (proxy)
  core/       D1Config (URL parsing), Wrangler (command build + JSON parse)
  jdbc/       D1Driver (java.sql.Driver) — the only JDBC class implemented so far
  cli/        Main (smoke runner over the core)
src/main/resources/META-INF/services/java.sql.Driver   (SPI registration)
src/test/kotlin/...                                     (unit tests)
```

## JDBC URL

```
jdbc:d1:?transport=<normal|ssh>&host=<sshHost>&dir=<remoteDir>&db=<name>&mode=<local|remote>&env=<env>&config=<path>&persist=<dir>&wrangler=<command>
```

- **One URL carries everything.** SSH auth is **not** in the URL — it is
  delegated to the OS `ssh` client + `~/.ssh/config` (keys, known_hosts,
  ControlMaster).
- DataGrip's **User / Password** arrive as JDBC properties. Intended use:
  `password` = a Cloudflare API token for `--remote` in `normal` mode (DataGrip
  stores it in the OS keychain). Proxy-to-hawk needs no credential in DataGrip.

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
- **The dev shell / wrangler prints a banner to stdout** (e.g. `kuaitu: loaded
  .env`); `Wrangler.parse` slices from the first `[` so a banner does not break
  JSON parsing. Prefer a wrapper (`d1q`) that keeps stdout clean.
- **`Transport` is a plain interface, not sealed** — a sealed interface cannot be
  implemented from the test module.
- A per-query `wrangler` spawn is ~1.1 s (node + miniflare startup), plus network
  for `--remote`. Fine for v1; cache introspection, and consider a persistent
  hawk-side helper later.

## Status

Slice 1 — scaffold + transport (normal/ssh) + wrangler core + `D1Driver`
registration + CLI + unit tests — is **done and green**; the CLI queries a real
D1. The `java.sql.*` connection layer is **not** implemented yet
(`D1Driver.connect` throws). Remaining work is in **`TASKS.md`**.
