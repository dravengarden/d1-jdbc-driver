package io.github.dravengarden.d1.jdbc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Types

/**
 * D1/SQLite is dynamically typed and `wrangler … --json` hands every cell back as
 * a raw JSON value (string / number / boolean / null). This maps a [JsonElement]
 * cell to the JDBC type, name, and Java class a [java.sql.ResultSet] /
 * [java.sql.ResultSetMetaData] must report, and provides the scalar coercions the
 * getters need. SQLite's affinities collapse to four shapes:
 *
 * - string  → `VARCHAR`  / `TEXT`    / `java.lang.String`
 * - integer → `BIGINT`   / `INTEGER` / `java.lang.Long`
 * - real    → `DOUBLE`   / `REAL`    / `java.lang.Double`
 * - null    → `NULL`     / `NULL`    / `null`
 */
internal data class ColumnType(
    val jdbcType: Int,
    val typeName: String,
    val className: String,
) {
    public companion object {
        val TEXT: ColumnType = ColumnType(Types.VARCHAR, "TEXT", "java.lang.String")
        val INTEGER: ColumnType = ColumnType(Types.BIGINT, "INTEGER", "java.lang.Long")
        val REAL: ColumnType = ColumnType(Types.DOUBLE, "REAL", "java.lang.Double")
        val NULL: ColumnType = ColumnType(Types.NULL, "NULL", "java.lang.Object")
    }
}

/** Classify a single JSON cell into the JDBC column shape it represents. */
internal fun classifyCell(cell: JsonElement): ColumnType =
    when {
        cell is JsonNull -> ColumnType.NULL
        cell is JsonPrimitive && cell.isString -> ColumnType.TEXT
        cell is JsonPrimitive && cell.content.toLongOrNull() != null -> ColumnType.INTEGER
        cell is JsonPrimitive && cell.content.toDoubleOrNull() != null -> ColumnType.REAL
        else -> ColumnType.TEXT
    }

internal fun JsonElement.isSqlNull(): Boolean = this is JsonNull

/**
 * Raw text of a cell, or `null` for SQL NULL. Numbers keep their literal form.
 * Note: kotlinx `JsonNull` *is* a `JsonPrimitive` whose `content` is the string
 * "null", so it must be filtered out explicitly before reading `content`.
 */
internal fun JsonElement.cellString(): String? =
    when {
        this is JsonNull -> null
        this is JsonPrimitive -> content
        else -> null
    }

/** Map a cell to the boxed Java object a generic `getObject` should return. */
internal fun JsonElement.cellObject(): Any? =
    when (val ct = classifyCell(this)) {
        ColumnType.NULL -> null
        ColumnType.TEXT -> cellString()
        ColumnType.INTEGER -> cellString()?.toLong()
        ColumnType.REAL -> cellString()?.toDouble()
        else -> error("unreachable column type $ct")
    }
