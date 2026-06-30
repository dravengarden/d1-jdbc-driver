package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.model.QueryResult
import java.sql.ResultSetMetaData
import java.sql.SQLException

/**
 * Column metadata derived from a [QueryResult]. D1/SQLite sends no schema with a
 * result set, so the type of each column is inferred from the first non-null cell
 * in that column (defaulting to TEXT). Fully implemented — no
 * `SQLFeatureNotSupportedException` fallthrough — because clients call these
 * eagerly while building a schema tree.
 */
public class D1ResultSetMetaData(
    private val result: QueryResult,
) : ResultSetMetaData {
    /** Inferred per-column shape, indexed 0-based (JDBC columns are 1-based). */
    private val types: List<ColumnType> =
        result.columns.indices.map { col ->
            val firstNonNull = result.rows.firstOrNull { !it[col].isSqlNull() }?.get(col)
            if (firstNonNull == null) ColumnType.TEXT else classifyCell(firstNonNull)
        }

    private fun typeOf(column: Int): ColumnType {
        if (column < 1 || column > types.size) {
            throw SQLException("column index out of range: $column (1..${types.size})")
        }
        return types[column - 1]
    }

    override fun getColumnCount(): Int = result.columns.size

    override fun isAutoIncrement(column: Int): Boolean = false

    override fun isCaseSensitive(column: Int): Boolean = true

    override fun isSearchable(column: Int): Boolean = true

    override fun isCurrency(column: Int): Boolean = false

    override fun isNullable(column: Int): Int = ResultSetMetaData.columnNullableUnknown

    override fun isSigned(column: Int): Boolean =
        typeOf(column).jdbcType.let { it == java.sql.Types.BIGINT || it == java.sql.Types.DOUBLE }

    override fun getColumnDisplaySize(column: Int): Int = DISPLAY_SIZE

    override fun getColumnLabel(column: Int): String = getColumnName(column)

    override fun getColumnName(column: Int): String {
        if (column < 1 || column > result.columns.size) {
            throw SQLException("column index out of range: $column (1..${result.columns.size})")
        }
        return result.columns[column - 1]
    }

    override fun getSchemaName(column: Int): String = ""

    override fun getPrecision(column: Int): Int = 0

    override fun getScale(column: Int): Int = 0

    override fun getTableName(column: Int): String = ""

    override fun getCatalogName(column: Int): String = ""

    override fun getColumnType(column: Int): Int = typeOf(column).jdbcType

    override fun getColumnTypeName(column: Int): String = typeOf(column).typeName

    override fun isReadOnly(column: Int): Boolean = true

    override fun isWritable(column: Int): Boolean = false

    override fun isDefinitelyWritable(column: Int): Boolean = false

    override fun getColumnClassName(column: Int): String = typeOf(column).className

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        if (iface != null && iface.isInstance(this)) return this as T
        throw SQLException("not a wrapper for $iface")
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean = iface != null && iface.isInstance(this)

    private companion object {
        const val DISPLAY_SIZE = 40
    }
}
