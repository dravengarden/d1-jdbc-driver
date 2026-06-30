# Tasks

Remaining work. Slice 1 (project scaffold + `Transport` (normal/ssh) + the
`wrangler` core + `D1Driver` registration + CLI + unit tests) is **done and
green** — the CLI already queries a real D1. See `AGENTS.md` for build/run and the
architecture.

Order: Task 1 is what makes the driver usable in DataGrip; do it first.

---

## Task 1 — JDBC connection layer (`java.sql.*`)

Make the driver usable from a JDBC client. Implement, all delegating to the
existing `core.Wrangler`:

- `D1Connection : java.sql.Connection`
- `D1Statement` / `D1PreparedStatement`
- `D1ResultSet` + `D1ResultSetMetaData` — map each `JsonElement` cell to JDBC
  types. SQLite/D1 is dynamically typed; a reasonable mapping is
  TEXT → `VARCHAR`, INTEGER → `BIGINT`, REAL → `DOUBLE`, NULL → `NULL`,
  BLOB → `BLOB`. Implement `wasNull`, `getString/getInt/getLong/getDouble/
  getObject`, `next`, `findColumn`, `getMetaData`.
- `D1DatabaseMetaData`:
  - `getTables()` → `SELECT name, type FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%'`
  - `getColumns()` → `PRAGMA table_info(<table>)`
  - `getPrimaryKeys()` → `PRAGMA table_info` (pk column), `getIndexInfo()` →
    `PRAGMA index_list` / `index_info`.

Implementation notes:

- Use **abstract base classes** that implement each interface with
  `throw SQLFeatureNotSupportedException()` defaults, then override only what is
  needed. Reference the structure of `DataGrip/redis-jdbc-driver` and
  `DataGrip/mongo-jdbc-driver` (Kotlin, same approach).
- Wire `D1Driver.connect` to build a `D1Connection` (run a `SELECT 1` connectivity
  check first).
- **Implement `Connection.isValid` correctly** — if it returns `false`, DataGrip
  closes the connection as invalid.

Acceptance: from a JDBC client, the connection opens, the schema tree lists tables
and columns, and `SELECT …` returns rows.

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
