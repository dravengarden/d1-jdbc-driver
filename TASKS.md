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

## Task 2 — wrangler wrapper + proxy/SSH verification

- Add a `d1q` wrapper on hawk: PATH-resolved, resolves node + wrangler without
  entering the project dev shell (avoid the banner that pollutes stdout), sources
  only the CF token for `--remote`, keeps stdout clean. The driver then calls
  `ssh hawk d1q d1 execute …`.
- Verify the `proxy` (SSH) transport end-to-end against the hawk-local D1.
- Verify `mode=remote` against the real Cloudflare D1.
- Document the recommended Mac `~/.ssh/config` Host entry with `ControlMaster` +
  `ControlPersist`.

---

## Task 3 — DataGrip integration

- Load the fat JAR as a user driver; set the data source SQL **dialect to
  SQLite**.
- Confirm introspection + browsing on both local (`proxy`) and `remote`.
- Document the exact DataGrip setup steps in `README.md`.

---

## Task 4 — performance & polish

- Driver-side **introspection caching** to cut the many-query latency.
- Optional: a **persistent hawk-side query helper** (one long-lived process
  holding the D1 handle, answering over the SSH-multiplexed channel) to remove the
  per-query node startup.
- Write support: `Statement.executeUpdate` (INSERT/UPDATE/DELETE).
- CI: GitHub Actions running `gradle build`; publish the fat JAR as a release
  artifact.
- Optional optimization: for `mode=remote`, call the **D1 HTTP API** directly from
  the JVM (skip wrangler/Node) — faster, at the cost of a second code path.
