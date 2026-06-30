package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.model.QueryResult
import kotlinx.serialization.json.JsonElement
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Statement

/**
 * A forward-only, read-only [ResultSet] over an already-materialised
 * [QueryResult] (wrangler returns the whole result set at once, so there is no
 * streaming cursor). Cells are raw JSON; the getters coerce them via [D1Types].
 * Everything not implemented here falls through to [AbstractResultSet] and throws
 * `SQLFeatureNotSupportedException`.
 */
public class D1ResultSet(
    private val result: QueryResult,
    private val statement: Statement?,
) : AbstractResultSet() {
    private var rowIndex = -1
    private var lastWasNull = false
    private var closed = false

    override fun next(): Boolean {
        if (rowIndex < result.rows.size) rowIndex++
        return rowIndex < result.rows.size
    }

    override fun close() {
        closed = true
    }

    override fun isClosed(): Boolean = closed

    override fun wasNull(): Boolean = lastWasNull

    /** 1-based column access with the SQL-NULL bookkeeping every getter shares. */
    private fun cell(columnIndex: Int): JsonElement {
        if (closed) throw SQLException("result set is closed")
        if (rowIndex < 0 || rowIndex >= result.rows.size) {
            throw SQLException("no current row (call next() first)")
        }
        val row = result.rows[rowIndex]
        if (columnIndex < 1 || columnIndex > row.size) {
            throw SQLException("column index out of range: $columnIndex (1..${row.size})")
        }
        val value = row[columnIndex - 1]
        lastWasNull = value.isSqlNull()
        return value
    }

    override fun findColumn(columnLabel: String?): Int {
        val idx = result.columns.indexOfFirst { it.equals(columnLabel, ignoreCase = true) }
        if (idx < 0) throw SQLException("no such column: $columnLabel")
        return idx + 1
    }

    override fun getString(columnIndex: Int): String? = cell(columnIndex).cellString()

    override fun getString(columnLabel: String?): String? = getString(findColumn(columnLabel))

    override fun getBoolean(columnIndex: Int): Boolean {
        val s = cell(columnIndex).cellString() ?: return false
        return s == "1" || s.equals("true", ignoreCase = true) || (s.toDoubleOrNull()?.let { it != 0.0 } ?: false)
    }

    override fun getBoolean(columnLabel: String?): Boolean = getBoolean(findColumn(columnLabel))

    override fun getByte(columnIndex: Int): Byte = getLong(columnIndex).toByte()

    override fun getByte(columnLabel: String?): Byte = getByte(findColumn(columnLabel))

    override fun getShort(columnIndex: Int): Short = getLong(columnIndex).toShort()

    override fun getShort(columnLabel: String?): Short = getShort(findColumn(columnLabel))

    override fun getInt(columnIndex: Int): Int = getLong(columnIndex).toInt()

    override fun getInt(columnLabel: String?): Int = getInt(findColumn(columnLabel))

    override fun getLong(columnIndex: Int): Long {
        val s = cell(columnIndex).cellString() ?: return 0L
        return s.toLongOrNull()
            ?: s.toDoubleOrNull()?.toLong()
            ?: throw SQLException("cannot read '$s' as a long")
    }

    override fun getLong(columnLabel: String?): Long = getLong(findColumn(columnLabel))

    override fun getFloat(columnIndex: Int): Float = getDouble(columnIndex).toFloat()

    override fun getFloat(columnLabel: String?): Float = getFloat(findColumn(columnLabel))

    override fun getDouble(columnIndex: Int): Double {
        val s = cell(columnIndex).cellString() ?: return 0.0
        return s.toDoubleOrNull() ?: throw SQLException("cannot read '$s' as a double")
    }

    override fun getDouble(columnLabel: String?): Double = getDouble(findColumn(columnLabel))

    override fun getBigDecimal(columnIndex: Int): BigDecimal? {
        val s = cell(columnIndex).cellString() ?: return null
        return s.toBigDecimalOrNull() ?: throw SQLException("cannot read '$s' as a decimal")
    }

    override fun getBigDecimal(columnLabel: String?): BigDecimal? = getBigDecimal(findColumn(columnLabel))

    override fun getObject(columnIndex: Int): Any? = cell(columnIndex).cellObject()

    override fun getObject(columnLabel: String?): Any? = getObject(findColumn(columnLabel))

    override fun getMetaData(): ResultSetMetaData = D1ResultSetMetaData(result)

    override fun getStatement(): Statement? = statement

    override fun getRow(): Int = if (rowIndex in 0 until result.rows.size) rowIndex + 1 else 0

    override fun isBeforeFirst(): Boolean = rowIndex < 0 && result.rows.isNotEmpty()

    override fun isAfterLast(): Boolean = rowIndex >= result.rows.size && result.rows.isNotEmpty()

    override fun isFirst(): Boolean = rowIndex == 0 && result.rows.isNotEmpty()

    override fun isLast(): Boolean = rowIndex == result.rows.size - 1 && result.rows.isNotEmpty()

    override fun getType(): Int = ResultSet.TYPE_FORWARD_ONLY

    override fun getConcurrency(): Int = ResultSet.CONCUR_READ_ONLY

    override fun getFetchDirection(): Int = ResultSet.FETCH_FORWARD

    override fun setFetchDirection(direction: Int) {
        if (direction != ResultSet.FETCH_FORWARD) {
            throw SQLException("d1 result sets are forward-only")
        }
    }

    override fun getFetchSize(): Int = result.rows.size

    override fun setFetchSize(rows: Int) {
        // No streaming cursor — the whole result set is already in memory.
    }

    override fun getWarnings(): java.sql.SQLWarning? = null

    override fun clearWarnings() {
        // No warnings are accumulated.
    }
}
