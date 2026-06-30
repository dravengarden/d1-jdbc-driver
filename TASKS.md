# Tasks

Remaining work. Slice 1 (project scaffold + `Transport` (normal/ssh) + the
`wrangler` core + `D1Driver` registration + CLI + unit tests) is **done and
green** — the CLI already queries a real D1. See `AGENTS.md` for build/run and the
architecture.

Order: Task 2 is the next step — verify the implemented JDBC layer against a real
local/remote D1 before wiring DataGrip.

---

## Task 1 — JDBC connection layer (`java.sql.*`) — DONE

The full `java.sql.*` layer is implemented in `jdbc/`, all delegating to
`core.Wrangler`, with 27 unit tests and a building fat JAR:

- `D1Connection` (autocommit-only; `isValid` runs `SELECT 1`),
  `D1Statement`, `D1PreparedStatement` (client-side positional-`?`
  substitution), `D1ResultSet` + `D1ResultSetMetaData` (cell→JDBC mapping:
  TEXT → `VARCHAR`, INTEGER → `BIGINT`, REAL → `DOUBLE`, NULL → `NULL`).
- `D1DatabaseMetaData`: `getTables` (sqlite_master), `getColumns` /
  `getPrimaryKeys` (`PRAGMA table_info`), `getIndexInfo`
  (`PRAGMA index_list` / `index_info`), plus SQLite-appropriate capability
  predicates so introspection never hits a thrown stub.
- `Abstract{Connection,Statement,PreparedStatement,ResultSet,DatabaseMetaData}`
  throwing stub bases (the redis/mongo-jdbc-driver approach).
- `D1Driver.connect` builds a `D1Connection` after a `SELECT 1` probe and wraps
  any backend failure in a `SQLException`.

Still UNVERIFIED against a real D1 (no wrangler/credentials in the build
sandbox) — that is Task 2. The mapping/reshaping is covered by unit tests using
a fake transport with canned `wrangler --json` payloads.

---

## Task 2 — wrangler wrapper + proxy/SSH verification — DONE

- **`normal` / `mode=local` — verified live** against the kuaitu local D1
  through the real `java.sql.*` API (DriverManager SPI → `isValid` →
  `DatabaseMetaData` getTables/getColumns/getPrimaryKeys/getIndexInfo →
  `Statement` SELECT → bound `PreparedStatement` → INSERT/UPDATE/DELETE).
- **`normal` / `mode=remote` — verified live** against the real Cloudflare D1
  (`kuaitu-preview`, token from the project `.env`; read-only `sqlite_master`
  listing returned 10 tables).
- **`d1q` wrapper — DONE** at [`scripts/d1q`](scripts/d1q) (shellcheck-clean):
  injects `--config`/`--persist-to`, strips the dotenv banner so stdout is pure
  JSON, resolves wrangler via `pnpm exec` (no dev shell). Verified as a drop-in
  `wrangler=` command through the driver. Project is overridable via
  `D1Q_PROJECT`/`D1Q_CONFIG`/`D1Q_PERSIST`; install on hawk's PATH (or use an
  absolute `wrangler=` path) for `ssh hawk d1q …`.
- **`proxy` (SSH) — verified by construction.** `SshTransport.remoteCommand` is
  unit-tested and `d1q` is a verified drop-in; the only untested link is the
  literal Mac→hawk `ssh` hop (can't loopback from hawk — no ssh-back-to-hawk
  path). Needs a real Mac → hawk run to close out.
- **Mac `~/.ssh/config`** (`ControlMaster`/`ControlPersist`) documented in
  `README.md`.

---

## Task 3 — DataGrip integration

- **Setup steps documented in `README.md`** (register user driver →
  `io.github.dravengarden.d1.jdbc.D1Driver`, dialect SQLite, URL templates for
  local/remote/proxy, ssh ControlMaster).
- Loading the JAR + confirming introspection/browsing in the DataGrip GUI is a
  **manual Mac-side step** (no headless DataGrip here). The same introspection
  path is already verified programmatically via `DatabaseMetaData` (Task 2).

---

## Task 4 — performance & polish

- **Introspection caching — DONE.** `D1Connection.introspect` memoises
  schema-read queries per connection; cleared on any write. Unit-tested
  (cache-hit + write-invalidation).
- **Write support — DONE.** `Statement`/`PreparedStatement` `executeUpdate` +
  `execute` route writes to an update count parsed from D1's `meta.changes`
  (caveat: `mode=local` wrangler reports no count → returns 0; remote is
  correct). Live-verified INSERT/UPDATE/DELETE on local.
- **CI — DONE.** `.github/workflows/ci.yml` runs `./gradlew build` on JDK 21
  (unit tests need no wrangler/network), uploads the fat JAR, and attaches it to
  `v*` tag releases.
- **Deferred (optional):** a persistent hawk-side query helper (long-lived
  process over the SSH-multiplexed channel) to kill the ~1 s per-query node
  startup; and a direct **D1 HTTP API** path for `mode=remote` (skip
  wrangler/Node) — a faster second code path. Both are nice-to-haves, not needed
  for a working DataGrip driver.
