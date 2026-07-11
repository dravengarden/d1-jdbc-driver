package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.model.QueryResult
import io.github.dravengarden.d1.transport.Transport
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Runs one SQL statement against a D1 database and returns its rows. The driver
 * doesn't care *how* — the implementations differ only in where the data lives:
 *
 * - [Wrangler]      — shell out to `wrangler d1 execute` (local miniflare or remote).
 * - [SqliteEngine]  — read the local miniflare SQLite file directly (no Node).
 * - (HTTP engine)   — the Cloudflare D1 REST API directly (remote, no Node).
 *
 * Each runs through a [io.github.dravengarden.d1.transport.Transport], so any
 * engine can execute locally or on a remote host over SSH.
 */
public interface Engine {
    public fun query(sql: String): QueryResult

    /**
     * Verify this engine's CLI is available on the host that runs it, before the
     * first query, so a missing dependency yields a clear message instead of an
     * opaque `command not found`. Throws [IllegalStateException] with a
     * user-facing message; callers wrap it as a plain `SQLException`.
     */
    public fun checkAvailable()
}

/**
 * Check that [tool] is on PATH of the [transport]'s host. The `command -v … ||`
 * always exits 0, so a non-FOUND result means the tool is missing while a thrown
 * [io.github.dravengarden.d1.transport.TransportException] means the host itself
 * is unreachable — the two get distinct, actionable messages.
 */
internal fun requireTool(transport: Transport, workingDir: String?, tool: String, hintParam: String?) {
    val probe = "command -v ${shq(tool)} >/dev/null 2>&1 && printf FOUND || printf MISSING"
    val out =
        try {
            transport.run(listOf("sh", "-c", probe), workingDir)
        } catch (e: Exception) {
            val advice =
                if (transport.description == "this machine") {
                    "Check the configured working directory and local executable permissions."
                } else {
                    "Check the host, key-based SSH auth, and known_hosts."
                }
            throw IllegalStateException(
                "Could not run a dependency probe on ${transport.description}: ${e.message}. $advice",
            )
        }
    if (out.trim() != "FOUND") {
        val hint = hintParam?.let { " or set '$it=<absolute-path>' in the URL" } ?: ""
        throw IllegalStateException(
            "This connection's engine needs '$tool' on ${transport.description}, but it is not on PATH there. " +
                "Install it$hint.",
        )
    }
}

private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

/** Flatten a list of JSON row-objects into the column/row shape the JDBC layer wants. */
internal fun tabulate(rows: List<JsonObject>, changes: Long = 0, lastRowId: Long? = null): QueryResult {
    if (rows.isEmpty()) return QueryResult(emptyList(), emptyList(), changes, lastRowId)
    val columns = buildList { rows.forEach { row -> row.keys.forEach { if (it !in this) add(it) } } }
    val tabular = rows.map { row -> columns.map { col -> row[col] ?: JsonNull } }
    return QueryResult(columns, tabular, changes, lastRowId)
}
