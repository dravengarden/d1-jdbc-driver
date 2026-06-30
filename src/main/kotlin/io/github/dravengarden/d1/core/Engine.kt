package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.model.QueryResult
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
}

/** Flatten a list of JSON row-objects into the column/row shape the JDBC layer wants. */
internal fun tabulate(rows: List<JsonObject>, changes: Long = 0, lastRowId: Long? = null): QueryResult {
    if (rows.isEmpty()) return QueryResult(emptyList(), emptyList(), changes, lastRowId)
    val columns = rows.first().keys.toList()
    val tabular = rows.map { row -> columns.map { col -> row[col] ?: JsonNull } }
    return QueryResult(columns, tabular, changes, lastRowId)
}
