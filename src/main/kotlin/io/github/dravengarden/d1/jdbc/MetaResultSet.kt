package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.model.QueryResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Build a read-only [D1ResultSet] from plain Kotlin values. [DatabaseMetaData]
 * methods must return result sets with a fixed, JDBC-specified column layout
 * (e.g. `getTables` → `TABLE_CAT, TABLE_SCHEM, …`); this packs hand-built rows
 * into the same [QueryResult]/[D1ResultSet] machinery the query path uses, so the
 * type inference and getters behave identically.
 */
internal fun metaResultSet(columns: List<String>, rows: List<List<Any?>>): D1ResultSet {
    val jsonRows = rows.map { row -> row.map(::toJsonCell) }
    return D1ResultSet(QueryResult(columns, jsonRows), null)
}

private fun toJsonCell(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(if (value) 1 else 0)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Short -> JsonPrimitive(value.toInt())
        is Double -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
