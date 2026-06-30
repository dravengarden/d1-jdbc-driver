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

---

## Contents

- [How it works](#how-it-works)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Connection URL reference](#connection-url-reference)
- [Recipes](#recipes) — local / remote / proxy / read-only / multi-DB / SSH options
- [Use in DataGrip](#use-in-datagrip)
- [Use in DBeaver / other JDBC clients](#use-in-dbeaver--other-jdbc-clients)
- [Authentication](#authentication)
- [Performance](#performance)
- [Limitations](#limitations)
- [Troubleshooting](#troubleshooting)
- [Build from source](#build-from-source)

---

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

Two orthogonal axes, both set in the URL:

- **transport** — *where* wrangler runs: `normal` (this machine) or `ssh`/`proxy`
  (a remote host over SSH).
- **mode** — *which* D1: `local` (miniflare's on-disk SQLite) or `remote` (the
  cloud database).

`mode=local` must run on the machine that owns the `.wrangler/state` dir;
`mode=remote` can run on either side.

---

## Prerequisites

| Side | Needs |
|---|---|
| **Client** (where the JDBC app / DataGrip runs) | the driver JAR; a JVM (the host app already has one); for `proxy`, an `ssh` client |
| **Server** (where wrangler runs — same machine for `normal`, the remote for `proxy`) | **`wrangler`** reachable (global on PATH, or point `wrangler=` at it) and, for `proxy`, **`sshd`** |

That's the whole server-side contract: **`wrangler` + `sshd`**. Nothing
project-specific is installed there — every parameter travels in the URL.

`wrangler` is **not** bundled: it's a Node CLI that must match the project's
version + `wrangler.jsonc` + `.wrangler/state`, so a bundled copy would drift.
The JAR carries no Node.

---

## Quick start

1. **Build the JAR** (or grab it from a release):

   ```bash
   nix develop -c gradle build      # → build/libs/d1-jdbc-driver-<version>.jar
   ```

2. **Smoke-test the core** without a JDBC client — one query straight through
   wrangler:

   ```bash
   java -cp build/libs/d1-jdbc-driver-*.jar io.github.dravengarden.d1.cli.MainKt \
     "jdbc:d1:?db=mydb-local&mode=local&dir=/path/to/project&config=wrangler.jsonc&persist=.wrangler/state" \
     "SELECT name FROM sqlite_master WHERE type='table'"
   ```

3. **Wire it into a JDBC client** — see [DataGrip](#use-in-datagrip) below.

---

## Connection URL reference

Everything is configured in one URL (or as JDBC properties — e.g. DataGrip's
*Advanced* tab). `db` is the only required parameter.

```
jdbc:d1:?db=<name>&mode=<local|remote>&transport=<normal|ssh>&...
```

| param | default | meaning |
|---|---|---|
| `db` | — (required) | D1 database name (as in `wrangler.jsonc`) |
| `mode` | `local` | `local` (miniflare) or `remote` (cloud) |
| `transport` | `normal` | `normal` (run wrangler here) or `ssh` / `proxy` (run it on `host`) |
| `host` | — | SSH target for proxy mode (`user@ip` or a `~/.ssh/config` alias) |
| `dir` | — | working directory where wrangler runs (the project root) |
| `env` | — | wrangler named environment (e.g. `preview`, `production`) |
| `config` | — | path to `wrangler.jsonc` (resolved on the machine running wrangler) |
| `persist` | — | `--persist-to` dir for `mode=local` (e.g. `.wrangler/state`) |
| `wrangler` | `wrangler` | the wrangler command, token-split (e.g. `pnpm exec wrangler`) |
| `ssh` | `ssh` | the ssh command for `transport=ssh`, token-split |
| `ssh-opts` | — | extra non-secret ssh args, token-split (e.g. `-p 2222 -o ProxyJump=bastion`) |
| `timeout` | `120` | per-command wrangler timeout, in seconds |
| `probe` | `true` | run a `SELECT 1` connectivity check on connect |
| `readonly` | `false` | reject all writes on this connection |
| `cache` | `true` | cache schema introspection per connection |

Notes:

- **Booleans** accept `true/false/1/0/yes/no/on/off`.
- **URL-encode** spaces in multi-token values, or rely on your client's field:
  `wrangler=pnpm%20exec%20wrangler`. The driver also accepts them verbatim when
  the value comes from a JDBC property.
- **Paths** (`dir`, `config`, `persist`) are resolved on whichever machine runs
  wrangler — the **remote** for `proxy`.
- **No secrets in the URL.** SSH keys/passphrase stay in `~/.ssh/config` + agent;
  the Cloudflare token stays in the project `.env` or the `password` property
  (see [Authentication](#authentication)).

---

## Recipes

### Local D1 (miniflare) on this machine

```
jdbc:d1:?db=mydb-local&mode=local&dir=/path/to/project&config=wrangler.jsonc&persist=.wrangler/state
```

`persist` must point at the same `--persist-to` your `wrangler dev` uses, or
wrangler opens a different empty DB and you get `no such table`.

### Remote (cloud) D1

```
jdbc:d1:?db=mydb&mode=remote&env=production&dir=/path/to/project&config=wrangler.jsonc
```

Needs a Cloudflare API token (see [Authentication](#authentication)). Drop
`env=` if the database is declared at the top level of `wrangler.jsonc`.

### Proxy — D1 lives on a remote dev server

wrangler runs on the server; this machine needs only `ssh`.

```
# the server's LOCAL miniflare DB
jdbc:d1:?transport=ssh&host=devbox&db=mydb-local&mode=local&dir=/srv/project&config=wrangler.jsonc&persist=.wrangler/state

# the cloud DB, but wrangler (and the token) live on the server
jdbc:d1:?transport=ssh&host=devbox&db=mydb&mode=remote&dir=/srv/project&config=wrangler.jsonc
```

`host` is whatever `ssh <host>` resolves to (an alias from `~/.ssh/config` is
ideal — see the [multiplexing tip](#use-in-datagrip)).

### Read-only browsing of production

```
jdbc:d1:?db=mydb&mode=remote&env=production&dir=/srv/project&config=wrangler.jsonc&readonly=true
```

`readonly=true` makes the connection reject every `executeUpdate` / write
`execute` **before** it reaches wrangler — a safety net when poking at prod.

### Project-local wrangler (no global install)

```
...&wrangler=pnpm%20exec%20wrangler
# or point straight at the binary:
...&wrangler=/path/to/project/node_modules/.bin/wrangler
```

### Multiple databases / environments

The driver maps **one connection = one D1 database** (which is exactly how JDBC
and SQLite think — one file, one database). A wrangler project can bind several
D1s and several environments; expose each as its **own data source** with a
different `db` / `env`:

```
jdbc:d1:?db=mydb-local&mode=local&...                 # dev
jdbc:d1:?db=mydb&mode=remote&env=preview&...          # preview
jdbc:d1:?db=mydb&mode=remote&env=production&...        # prod
```

### SSH on a non-default port / via a jump host

```
...&transport=ssh&host=devbox&ssh-opts=-p%202222%20-o%20ProxyJump%3Dbastion
```

`ssh-opts` is token-split and inserted before the host. Use it for ports, jump
hosts, or an identity *path* (`-i ~/.ssh/id_ed25519`) — never a passphrase.

### Raise the timeout for slow / cold-start queries

```
...&timeout=300
```

---

## Use in DataGrip

1. **Register the driver** — *Settings → Database → Drivers → +* (or *Database
   Explorer → + → Driver*). Name it `Cloudflare D1`.
   - **Driver Files**: add `build/libs/d1-jdbc-driver-<version>.jar`.
   - **Class**: `io.github.dravengarden.d1.jdbc.D1Driver` (usually auto-detected
     from the JAR's SPI registration).
   - **Dialect**: **SQLite** — D1 *is* SQLite, and the introspector relies on it.
   - **URL template** (optional): `jdbc:d1:?db=[{db}]&mode=[{mode}]`.

2. **Create a data source** with that driver and paste a `jdbc:d1:?…` URL from
   [Recipes](#recipes). No server-side wrapper or config file is needed.

3. **Test Connection** — the driver runs `SELECT 1`; a green check means it
   reached the D1. Then expand the schema tree to browse tables/columns and open
   a console to run SQL.

**Tip — SSH multiplexing for `proxy`.** Each query is a fresh `ssh` invocation;
reuse one connection so you're not re-handshaking every time. In `~/.ssh/config`:

```
Host devbox
    HostName 10.0.0.5
    User me
    ControlMaster auto
    ControlPath ~/.ssh/cm-%r@%h:%p
    ControlPersist 5m
```

Then use `host=devbox` in the URL.

---

## Use in DBeaver / other JDBC clients

Any JDBC client works — the driver is a plain JAR with SPI registration.

1. **Driver Manager → New** (DBeaver: *Database → Driver Manager → New*).
2. Add the JAR; set **Class Name** `io.github.dravengarden.d1.jdbc.D1Driver`.
3. Set the **URL template** to `jdbc:d1:{...}` (or just paste a full URL into the
   connection's URL field).
4. Pick a **SQLite**-flavored dialect if the client offers one, so its SQL editor
   and introspection assume SQLite syntax.

Programmatic use is ordinary JDBC:

```java
try (Connection c = DriverManager.getConnection(
        "jdbc:d1:?db=mydb-local&mode=local&dir=/path/to/project&config=wrangler.jsonc&persist=.wrangler/state")) {
    try (Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT id, email FROM accounts")) {
        while (rs.next()) System.out.println(rs.getString("id") + " " + rs.getString("email"));
    }
}
```

---

## Authentication

- **`mode=local`** — no credentials. It's an on-disk SQLite file via miniflare.
- **`mode=remote`** — needs a **Cloudflare API token** with D1 access. Two ways
  to supply it:
  - The JDBC **`password`** property (DataGrip's *Password* field). Your client
    keeps it in its own secret store (DataGrip → OS keychain); the driver injects
    it as `CLOUDFLARE_API_TOKEN` into the local wrangler process — never the URL,
    never the command line. **`transport=normal` only.**
  - `CLOUDFLARE_API_TOKEN` in the environment wrangler runs in — e.g. the
    project's `.env`, which wrangler auto-loads when run from `dir`. This is the
    way for **`proxy`** (the token stays on the server; the driver does not forward
    a secret over ssh).
- **SSH (`proxy`)** — fully delegated to the OS `ssh` client: keys, `known_hosts`,
  agent, and `~/.ssh/config`. The driver puts **no** credential in the URL;
  `ssh-opts` is for non-secret flags only.

  **Passwordless (key-based) SSH is required.** The driver runs `ssh <host> …`
  per query with no TTY, so an interactive password/passphrase prompt would just
  fail. Set it up and verify once before configuring the data source:

  ```bash
  ssh-copy-id <user>@<host>          # if the key isn't authorized yet
  ssh -o BatchMode=yes <host> true   # must succeed with NO prompt
  ```

  First contact also needs the host key accepted — run a plain `ssh <host> true`
  once interactively to store it in `known_hosts`. For speed, reuse one
  connection across queries with `ControlMaster` (see the
  [DataGrip tip](#use-in-datagrip)).

The token is never written to the URL or logged by the driver.

---

## Performance

- A per-query `wrangler` spawn is **~1 s** (Node + miniflare/network startup).
  Fine for browsing; not a high-QPS path.
- **Schema introspection is cached per connection** (the repeated
  `sqlite_master` / `PRAGMA` reads a client issues while building its tree), and
  the cache is cleared on any write. Disable with `cache=false`.
- Tune the per-command ceiling with `timeout=<seconds>`; skip the connect-time
  probe with `probe=false`.
- **Advanced (local only):** the miniflare DB is a real SQLite file at
  `<persist>/v3/d1/miniflare-D1DatabaseObject/<hash>.sqlite`. For read-heavy local
  work you can point a native SQLite driver straight at it (no ~1 s spawn) — but
  the filename is an opaque hash, and you must **not** have `wrangler dev` writing
  to it concurrently (SQLite single-writer lock). The d1-jdbc-driver is the
  portable path that also works for `remote` and `proxy`.

---

## Limitations

- **One connection = one D1 database.** Use a separate data source per database /
  environment (see the [multi-DB recipe](#multiple-databases--environments)).
- **`mode=local` reports no row-change count.** A local write succeeds but
  `executeUpdate` returns `0` (wrangler's local `meta` omits `changes`). Remote
  reports it correctly.
- **Result sets are forward-only, read-only** (`TYPE_FORWARD_ONLY`,
  `CONCUR_READ_ONLY`). The whole result is materialized in memory (wrangler
  returns it all at once).
- **Not full JDBC.** Only the methods a browsing/SQL client needs are
  implemented; anything else throws `SQLFeatureNotSupportedException` rather than
  silently misbehaving.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `no such table: X` (local) | `persist=` missing or wrong; wrangler opened a different default DB | point `persist=` at the dir holding `.wrangler/state` that your `wrangler dev` uses |
| `command failed (exit 127)` / `wrangler: not found` | wrangler not on the PATH of the machine running it | set `wrangler=` (e.g. `pnpm exec wrangler` or an absolute path), or install wrangler globally; for `proxy`, ensure it's on the server's non-interactive ssh PATH |
| remote: `Authentication error` / 403 | no/invalid Cloudflare API token | put `CLOUDFLARE_API_TOKEN` in the project `.env`, or pass it as the `password` property |
| `connection is read-only` | `readonly=true` is set | remove it, or call `setReadOnly(false)` on the connection |
| proxy: `Host key verification failed` / `Permission denied (publickey)` | ssh can't verify the host or auth non-interactively | fix `~/.ssh/config` + `known_hosts` + key/agent; confirm with `ssh <host> true` |
| `command timed out after 120s` | slow query or cold start | raise `timeout=<seconds>` |
| `executeUpdate` returns `0` on a local write | expected — local wrangler omits the change count | the write **did** apply; use `mode=remote` if you need the count |

For deeper digging, run the same query through the [CLI](#quick-start) to see
wrangler's raw behavior outside JDBC.

---

## Build from source

Inside the dev shell (`nix develop` → JDK 21 + Gradle):

```bash
gradle build      # compile + run all unit tests + fat JAR
gradle test       # tests only
```

Output: `build/libs/d1-jdbc-driver-<version>.jar` — the single self-contained JAR
you load into a JDBC client. The unit tests use a fake transport with canned
`wrangler --json` payloads, so they need no wrangler or network.

See [`AGENTS.md`](AGENTS.md) for the internal architecture and conventions.

---

## License

Apache-2.0. See [`LICENSE`](LICENSE).
