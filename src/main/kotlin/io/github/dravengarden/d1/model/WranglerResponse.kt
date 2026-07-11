package io.github.dravengarden.d1.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One element of `wrangler d1 execute --json` output. wrangler prints a JSON
 * array, one [WranglerResult] per executed statement:
 *
 * ```json
 * [ { "results": [ { "id": "…", "email": "…" } ], "success": true, "meta": {…} } ]
 * ```
 */
@Serializable
public data class WranglerResult(
    val results: List<JsonObject> = emptyList(),
    val success: Boolean = false,
    val meta: JsonObject? = null,
    val error: String? = null,
)

/**
 * A query result flattened for the JDBC layer: ordered [columns] and [rows],
 * each cell a raw [JsonElement] (string / number / boolean / null) to be mapped
 * to JDBC types by the ResultSet. [changes] and [lastRowId] come from D1's `meta`
 * block and drive `Statement.executeUpdate` / generated keys for writes.
 */
public data class QueryResult(
    val columns: List<String>,
    val rows: List<List<JsonElement>>,
    val changes: Long = 0,
    val lastRowId: Long? = null,
)
