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
- [Engines](#engines) — wrangler / sqlite / http, and the engine × transport matrix
- [Connection URL reference](#connection-url-reference)
- [Access & permissions](#access--permissions) — read / write / ddl
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

Three orthogonal axes, all set in the URL:

- **mode** — *which* D1: `local` (miniflare's on-disk SQLite) or `remote` (the
  cloud database).
- **engine** — *how* a query reaches the data:
  - `wrangler` — shell out to `wrangler d1 execute` (works for local and remote).
  - `sqlite` — read the local miniflare SQLite file directly (no Node; ~ms vs
    ~1.5 s). `mode=local` only.
  - `http` — the Cloudflare D1 REST API directly (no Node). `mode=remote` only.
  - `auto` (default) — local → `sqlite` when a file is resolvable, else `wrangler`.
- **transport** — *where* that engine runs: `normal` (this machine) or
  `ssh`/`proxy` (a remote host over SSH).

`mode=local` must run on the machine that owns the `.wrangler/state` dir;
`mode=remote` can run on either side. See [Engines](#engines) for the full
matrix and the per-engine dependencies.

---

## Prerequisites

**Client** (where the JDBC app / DataGrip runs): the driver JAR; a JVM (the host
app already has one); for `proxy`, an `ssh` client. Nothing else — no Node, no
Cloudflare token locally (unless you run a non-proxy `engine=http`).

**The host that runs the engine** (the same machine for `transport=normal`, the
remote for `transport=ssh`/proxy) needs **only the small CLI the chosen engine
shells out to** — plus `sshd` for proxy:

| engine | the host needs | for |
|---|---|---|
| `wrangler` | **`wrangler`** on PATH (or point `wrangler=` at it) — a Node CLI | local + remote |
| `sqlite` | **`sqlite3`** on PATH (or point `sqlite=` at it) | local only |
| `http` | **`curl`** on PATH | remote only |

So for the common **proxy + fast local** setup, the remote host needs just
**`sqlite3` + `sshd`**. None of these tools is bundled in the JAR — they're
external on purpose (a Node CLI / SQLite build must match the environment, and
the JAR stays tiny and dependency-free). Everything else travels in the URL;
nothing project-specific is installed on the host.

On connect the driver **preflights** the engine's CLI on its host and tells you
exactly what's missing (e.g. *"engine needs 'sqlite3' on SSH host 'hawk' …"*)
rather than failing opaquely later. Disable with `probe=false`.

---

## Quick start

1. **Build the JAR** (or grab it from a release):

   ```bash
   nix develop -c gradle build      # → build/libs/d1-jdbc-driver-<version>.jar
   ```

2. **Smoke-test the core** without a JDBC client — one query straight through the
   resolved engine (`auto` → `sqlite` here, since `mode=local` + `persist`):

   ```bash
   java -cp build/libs/d1-jdbc-driver-*.jar io.github.dravengarden.d1.cli.MainKt \
     "jdbc:d1:?db=mydb-local&mode=local&persist=/path/to/project/.wrangler/state" \
     "SELECT name FROM sqlite_master WHERE type='table'"
   ```

3. **Wire it into a JDBC client** — see [DataGrip](#use-in-datagrip) below.

---

## Engines

An **engine** decides *how* a query reaches the data; a **transport** decides
*where* the engine runs. They're independent, so any engine can run locally or on
a remote host over SSH:

| engine ＼ transport | `normal` (this machine) | `ssh` (remote host) |
|---|---|---|
| **wrangler** | wrangler here | wrangler on the remote |
| **sqlite** (local only) | read the file here | read the file on the remote |
| **http** (remote only) | this machine → Cloudflare | curl on the remote → Cloudflare |

- **`wrangler`** — the portable default. Works for `local` and `remote`, matches
  the project's wrangler config/state exactly. Cost: each query is a ~1.5 s Node
  + miniflare cold-start.
- **`sqlite`** (`mode=local`) — the local D1 *is* a SQLite file on disk; this
  reads it directly with `sqlite3` (~ms, no Node). Opened **read-only** with a
  busy timeout, so it runs safely alongside a live `wrangler dev` and sees its
  committed writes. Writes aren't supported on this engine — use
  `engine=wrangler` for those. The file is auto-resolved under
  `<persist>/v3/d1/…`, or set `file=`.
- **`http`** (`mode=remote`) — call the Cloudflare D1 REST API directly via
  `curl`, no Node (~2× faster than `wrangler --remote`, and it returns real
  row-change counts). Needs `account=` + `database-id=`. With `transport=ssh` the
  request egresses from the remote host (useful when its path to Cloudflare beats
  the client's). The token is read fresh from the host's env file (so rotation is
  picked up) and piped to curl — never on the command line — or supplied via the
  JDBC `password`.
- **`auto`** (default) — `mode=local` with a resolvable file → `sqlite`;
  `mode=remote` with `account=` + `database-id=` → `http`; otherwise `wrangler`.

**Pick by editing the URL**, e.g. `&engine=sqlite` or `&engine=wrangler`. The
matrix is just `engine=…` × `transport=…`; the [recipes](#recipes) below show the
common combinations.

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
| `engine` | `auto` | `auto` / `wrangler` / `sqlite` / `http` — see [Engines](#engines) |
| `transport` | `normal` | `normal` (run the engine here) or `ssh` / `proxy` (run it on `host`) |
| `host` | — | SSH target for proxy mode (`user@ip` or a `~/.ssh/config` alias) |
| `dir` | — | working directory where the engine's command runs (the project root) |
| `env` | — | wrangler named environment (e.g. `preview`, `production`) |
| `config` | — | path to `wrangler.jsonc` (wrangler engine; resolved on the machine running it) |
| `persist` | — | `--persist-to` dir for `mode=local`; also where the `sqlite` engine finds the file |
| `wrangler` | `wrangler` | the wrangler command, token-split (e.g. `pnpm exec wrangler`) |
| `sqlite` | `sqlite3` | the sqlite3 command for `engine=sqlite`, token-split |
| `file` | — | explicit `.sqlite` path for `engine=sqlite` (else auto-resolved from `persist`) |
| `account` | — | Cloudflare account id for `engine=http` |
| `database-id` | — | D1 database **id** (UUID) for `engine=http` — not the `db` name |
| `env-file` | `.env` | file (under `dir`) the http engine reads the token from |
| `token-var` | `CLOUDFLARE_API_TOKEN` | env-var name holding the token in `env-file` |
| `ssh` | `ssh` | the ssh command for `transport=ssh`, token-split |
| `ssh-opts` | — | extra non-secret ssh args, token-split (e.g. `-p 2222 -o ProxyJump=bastion`) |
| `timeout` | `120` | per-command timeout, in seconds |
| `probe` | `true` | on connect, check the engine's CLI is present + run `SELECT 1` |
| `access` | `read` | write guardrail: `read` / `write` / `ddl` — see [Access](#access--permissions) |
| `cache` | `true` | cache schema introspection per connection |

Notes:

- **Booleans** accept `true/false/1/0/yes/no/on/off`.
- **URL-encode** spaces in multi-token values, or rely on your client's field:
  `wrangler=pnpm%20exec%20wrangler`. The driver also accepts them verbatim when
  the value comes from a JDBC property.
- **Paths** (`dir`, `config`, `persist`, `file`) are resolved on whichever
  machine runs the engine — the **remote** for `proxy`.
- **No secrets in the URL.** SSH keys/passphrase stay in `~/.ssh/config` + agent;
  the Cloudflare token stays in the project `.env` or the `password` property
  (see [Authentication](#authentication)).

---

## Access & permissions

A connection's `access` level is a **write guardrail** — three increasing tiers,
**read by default** (writes must be opted into):

| `access` | allows | aliases |
|---|---|---|
| `read` *(default)* | `SELECT` / `PRAGMA` / `EXPLAIN` | `ro`, `readonly` |
| `write` | + `INSERT` / `UPDATE` / `DELETE` (DML) | `rw` |
| `ddl` | + `CREATE` / `ALTER` / `DROP` … (schema) | `full` |

```
…                       # read-only — safe for browsing prod
…&access=write          # edit rows
…&access=ddl            # change schema / run migrations
```

Statements are classified by leading keyword (a `WITH …` CTE is scanned for a
write verb); anything ambiguous is treated as a write. A blocked statement gives
a clear message, e.g. *"This connection is read-only (access=read). Add
'access=write' …"*.

> **It's a guardrail, not a security boundary.** The driver refuses to *send*
> over-privileged SQL, but your Cloudflare token may still have write access. For
> hard enforcement, use a **read-only Cloudflare API token** (D1 Read scope) —
> that's enforced server-side. The two compose: the guardrail stops slips, the
> scoped token stops everything.
>
> Note: `engine=sqlite` is always read-only at the engine level regardless of
> `access`; local writes need `engine=wrangler`.

---

## Recipes

### Local D1 (miniflare) on this machine

```
jdbc:d1:?db=mydb-local&mode=local&persist=.wrangler/state
```

`engine=auto` reads the file directly with `sqlite3` (fast). `persist` must point
at the same `--persist-to` your `wrangler dev` uses, or you'll resolve a
different / empty DB. Force the legacy wrangler path with `&engine=wrangler`
(then also pass `dir`/`config`).

### Fast local browsing over proxy (recommended for a remote dev box)

```
jdbc:d1:?transport=ssh&host=devbox&db=mydb-local&mode=local&persist=/srv/project/.wrangler/state
```

`engine=auto` → `sqlite` runs `ssh devbox sqlite3 -readonly …` against the file —
~ms per query instead of ~1.5 s. The remote box needs **`sqlite3` + `sshd`**
(no Node). Read-only; for writes add `&engine=wrangler`.

### Remote (cloud) D1 — via wrangler

```
jdbc:d1:?db=mydb&mode=remote&engine=wrangler&env=production&dir=/path/to/project&config=wrangler.jsonc
```

Needs a Cloudflare API token (see [Authentication](#authentication)). Drop
`env=` if the database is declared at the top level of `wrangler.jsonc`.

### Remote (cloud) D1 — via the D1 HTTP API (faster, no Node)

```
jdbc:d1:?db=mydb&mode=remote&account=<account-id>&database-id=<db-uuid>&dir=/path/to/project
```

`engine=auto` → `http`: a direct `curl` to the D1 REST API. `account` and
`database-id` come from `wrangler.jsonc` / the Cloudflare dashboard. The token is
read from `dir/.env` (`CLOUDFLARE_API_TOKEN`), or pass it as the JDBC `password`.
Add `transport=ssh&host=devbox` to make the request **egress from `devbox`**
instead of this machine (then `dir`/`.env` are on `devbox`); that host needs only
`curl` + `sshd`.

### Proxy — D1 lives on a remote dev server

wrangler runs on the server; this machine needs only `ssh`.

```
# the server's LOCAL miniflare DB
jdbc:d1:?transport=ssh&host=devbox&db=mydb-local&mode=local&dir=/srv/project&config=wrangler.jsonc&persist=.wrangler/state

# the cloud DB — request egresses from the server (engine=http via curl on devbox)
jdbc:d1:?transport=ssh&host=devbox&db=mydb&mode=remote&account=<acct>&database-id=<uuid>&dir=/srv/project
```

`host` is whatever `ssh <host>` resolves to (an alias from `~/.ssh/config` is
ideal — see the [multiplexing tip](#use-in-datagrip)).

### Browsing production (read-only is the default)

```
jdbc:d1:?db=mydb&mode=remote&account=<acct>&database-id=<uuid>&dir=/srv/project
```

No `access=` needed — connections are **read-only by default**. Writes are
rejected before reaching the backend; opt in per connection with `access=write`
(data) or `access=ddl` (schema). See [Access](#access--permissions).

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

1. **Register the driver.** Driver management is **not** in *Settings* — open the
   **Data Sources and Drivers** dialog (**⌘;** / *File → Data Sources…*, or the
   Database Explorer toolbar **+ → Driver**). In its left pane select the
   **Drivers** group, click **+**, and name it `Cloudflare D1`.
   - **Driver Files**: **+** → add `build/libs/d1-jdbc-driver-<version>.jar`.
   - **Class**: pick `io.github.dravengarden.d1.jdbc.D1Driver` (listed once the
     JAR is added, via the JAR's SPI registration).
   - **Dialect**: **SQLite** — D1 *is* SQLite, and the introspector relies on it.
   - **URL template** (optional): `jdbc:d1:?db=[{db}]&mode=[{mode}]`.

2. **Create a data source.** In the same dialog's **Data Sources** group, click
   **+** → pick your **Cloudflare D1** driver, and paste a `jdbc:d1:?…` URL from
   [Recipes](#recipes) into the URL field. No server-side wrapper or config file
   is needed.

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
        "jdbc:d1:?db=mydb-local&mode=local&persist=/path/to/project/.wrangler/state")) {
    try (Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT id, email FROM accounts")) {
        while (rs.next()) System.out.println(rs.getString("id") + " " + rs.getString("email"));
    }
}
```

---

## Authentication

- **`mode=local`** — no credentials. It's an on-disk SQLite file via miniflare.
- **`mode=remote`** — needs a **Cloudflare API token** with D1 access. Either:
  - **An env file on the host** — `CLOUDFLARE_API_TOKEN` in `dir/.env` (override
    with `env-file=` / `token-var=`). `engine=http` reads it **fresh per query**
    (so a rotated token is picked up) and pipes it to curl — never on the command
    line; `engine=wrangler` has wrangler auto-load it. This keeps the token on the
    host, which is the way for **`proxy`**.
  - **The JDBC `password` property** (DataGrip's *Password* field) — kept in the
    client's secret store, never in the URL. Used by `engine=http` directly, and
    injected as `CLOUDFLARE_API_TOKEN` into a local `engine=wrangler` process.
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

- **Use `engine=sqlite` for local** (it's the `auto` default). A `wrangler d1
  execute` spawn is **~1.5 s** — almost all Node + miniflare cold-start, not the
  query. Reading the SQLite file directly with `sqlite3` is **~ms**. Over proxy,
  add SSH `ControlMaster` (below) so the ssh hop is reused too.
- **Schema introspection is cached per connection** (the repeated
  `sqlite_master` / `PRAGMA` reads a client issues while building its tree), and
  the cache is cleared on any write. Disable with `cache=false`.
- Tune the per-command ceiling with `timeout=<seconds>`; skip the connect-time
  probe with `probe=false`.
- **SSH multiplexing** for proxy — each query is a fresh `ssh`; reuse one
  connection so you don't re-handshake. In the client's `~/.ssh/config`:

  ```
  Host devbox
      ControlMaster auto
      ControlPath ~/.ssh/cm-%r@%h:%p
      ControlPersist 5m
  ```

- `mode=remote` pays the network round-trip to Cloudflare per query.
  `engine=http` is ~2× faster than `engine=wrangler` there (no Node spawn on top
  of the round-trip) and returns real row-change counts.

---

## Limitations

- **One connection = one D1 database.** Use a separate data source per database /
  environment (see the [multi-DB recipe](#multiple-databases--environments)).
- **`engine=sqlite` is read-only.** It's for fast browsing; INSERT/UPDATE/DELETE
  fail with `readonly database`. Use `engine=wrangler` to write to a local D1.
- **`engine=wrangler` + `mode=local` reports no row-change count.** A local write
  succeeds but `executeUpdate` returns `0` (wrangler's local `meta` omits
  `changes`). Remote reports it correctly.
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
| `no such table: X` (local) | `persist=` missing or wrong; a different default DB was opened | point `persist=` at the dir holding `.wrangler/state` that your `wrangler dev` uses |
| `…engine needs 'X' on SSH host '…', but it is not on PATH there` | the engine's CLI (sqlite3 / curl / wrangler) is missing on the host | install it on that host, or point the matching param (`sqlite=` / `wrangler=`) at an absolute path |
| `SSH host '…' is unreachable or refused the connection` | ssh can't reach/auth the host | check the alias, key-based auth (`ssh <host> true`), and `known_hosts` |
| `no local D1 SQLite file under …` | `wrangler dev`/migrations haven't created the local DB yet, or `persist=` is wrong | run the app once locally; verify `persist=` |
| `readonly database` | writing through `engine=sqlite` (always read-only) | add `&engine=wrangler` for local writes |
| `command failed (exit 127)` / `wrangler: not found` | wrangler not on the PATH of the machine running it | set `wrangler=` (e.g. `pnpm exec wrangler` or an absolute path), or install wrangler globally; for `proxy`, ensure it's on the server's non-interactive ssh PATH |
| remote: `Authentication error` / 403 | no/invalid Cloudflare API token | put `CLOUDFLARE_API_TOKEN` in the project `.env`, or pass it as the `password` property |
| `This connection is read-only (access=read)` | writes need an explicit access level | add `access=write` (data) or `access=ddl` (schema) to the URL |
| `Schema changes (DDL) are not allowed (access=write)` | `DROP`/`CREATE`/`ALTER` need the top tier | add `access=ddl` to the URL |
| proxy: `Host key verification failed` / `Permission denied (publickey)` | ssh can't verify the host or auth non-interactively | fix `~/.ssh/config` + `known_hosts` + key/agent; confirm with `ssh <host> true` |
| `command timed out after 120s` | slow query or cold start | raise `timeout=<seconds>` |
| `executeUpdate` returns `0` on a local write (`engine=wrangler`) | expected — local wrangler omits the change count | the write **did** apply; use `mode=remote` if you need the count |

For deeper digging, run the same query through the [CLI](#quick-start) to see the
engine's raw behavior outside JDBC.

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
