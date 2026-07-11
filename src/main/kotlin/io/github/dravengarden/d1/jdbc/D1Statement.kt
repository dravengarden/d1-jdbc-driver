package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.model.QueryResult
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLWarning

/** A forward-only JDBC statement backed by one materialised D1 command result. */
public class D1Statement(
    private val connection: D1Connection,
) : AbstractStatement() {
    private var current: D1ResultSet? = null
    private var updateCount = -1L
    private var closed = false
    private var maxRows = 0L
    private var closeOnCompletion = false

    private fun begin(sql: String?): String {
        if (isClosed) throw SQLException("statement is closed")
        connection.ensureOpen()
        val text = sql ?: throw SQLException("sql is null")
        current?.close()
        current = null
        if (isClosed) throw SQLException("statement was closed when its previous result set closed")
        updateCount = -1
        return text
    }

    private fun resultSet(result: QueryResult): D1ResultSet {
        val limited = if (maxRows > 0 && result.rows.size.toLong() > maxRows) result.copy(rows = result.rows.take(maxRows.toInt())) else result
        return D1ResultSet(limited, this).also { current = it }
    }

    private fun runUpdate(text: String): Long {
        val result = connection.execute(text)
        if (classifyStatement(text) != StatementClass.READ) connection.invalidateIntrospection()
        updateCount = result.changes
        return updateCount
    }

    override fun executeQuery(sql: String?): ResultSet {
        val text = begin(sql)
        if (!looksLikeQuery(text)) throw SQLException("executeQuery requires a row-returning statement")
        return resultSet(connection.execute(text))
    }

    override fun executeUpdate(sql: String?): Int = narrowUpdateCount(executeLargeUpdate(sql))

    override fun executeLargeUpdate(sql: String?): Long {
        val text = begin(sql)
        if (looksLikeQuery(text)) throw SQLException("executeUpdate cannot execute a row-returning statement")
        return runUpdate(text)
    }

    override fun execute(sql: String?): Boolean {
        val text = begin(sql)
        return if (looksLikeQuery(text)) {
            resultSet(connection.execute(text))
            true
        } else {
            runUpdate(text)
            false
        }
    }

    override fun getResultSet(): ResultSet? = current

    override fun getUpdateCount(): Int = if (updateCount < 0) -1 else narrowUpdateCount(updateCount)

    override fun getLargeUpdateCount(): Long = updateCount

    override fun getMoreResults(): Boolean = getMoreResults(java.sql.Statement.CLOSE_CURRENT_RESULT)

    override fun getMoreResults(current: Int): Boolean {
        when (current) {
            java.sql.Statement.CLOSE_CURRENT_RESULT, java.sql.Statement.CLOSE_ALL_RESULTS -> this.current?.close()
            java.sql.Statement.KEEP_CURRENT_RESULT -> throw SQLFeatureNotSupportedException("multiple open results are not supported")
            else -> throw SQLException("invalid getMoreResults option: $current")
        }
        this.current = null
        updateCount = -1
        return false
    }

    internal fun onResultSetClosed(resultSet: D1ResultSet) {
        if (current === resultSet) current = null
        if (closeOnCompletion && !closed) close()
    }

    override fun close() {
        if (closed) return
        closed = true
        current?.close()
        current = null
    }

    override fun isClosed(): Boolean = closed || connection.isClosed

    override fun getConnection(): Connection = connection

    override fun getResultSetType(): Int = ResultSet.TYPE_FORWARD_ONLY

    override fun getResultSetConcurrency(): Int = ResultSet.CONCUR_READ_ONLY

    override fun getResultSetHoldability(): Int = ResultSet.CLOSE_CURSORS_AT_COMMIT

    override fun getFetchDirection(): Int = ResultSet.FETCH_FORWARD

    override fun setFetchDirection(direction: Int) {
        if (direction != ResultSet.FETCH_FORWARD) throw SQLException("d1 is forward-only")
    }

    override fun getFetchSize(): Int = 0

    override fun setFetchSize(rows: Int) {
        if (rows < 0) throw SQLException("fetch size must be non-negative")
    }

    override fun getMaxRows(): Int = if (maxRows > Int.MAX_VALUE) Int.MAX_VALUE else maxRows.toInt()

    override fun setMaxRows(max: Int) {
        if (max < 0) throw SQLException("max rows must be non-negative")
        maxRows = max.toLong()
    }

    override fun getLargeMaxRows(): Long = maxRows

    override fun setLargeMaxRows(max: Long) {
        if (max < 0 || max > Int.MAX_VALUE) throw SQLException("max rows must be between 0 and ${Int.MAX_VALUE}")
        maxRows = max
    }

    override fun getMaxFieldSize(): Int = 0

    override fun setMaxFieldSize(max: Int) {
        if (max < 0) throw SQLException("max field size must be non-negative")
        if (max != 0) throw SQLFeatureNotSupportedException("field-size truncation is not supported")
    }

    override fun getQueryTimeout(): Int = 0

    override fun setQueryTimeout(seconds: Int) {
        if (seconds < 0) throw SQLException("query timeout must be non-negative")
        if (seconds != 0) throw SQLFeatureNotSupportedException("use the connection URL timeout= setting")
    }

    override fun setEscapeProcessing(enable: Boolean) {
        if (enable) throw SQLFeatureNotSupportedException("JDBC escape processing is not supported")
    }

    override fun getWarnings(): SQLWarning? = null

    override fun clearWarnings(): Unit = Unit

    override fun isPoolable(): Boolean = false

    override fun setPoolable(poolable: Boolean): Unit = Unit

    override fun isCloseOnCompletion(): Boolean = closeOnCompletion

    override fun closeOnCompletion() {
        closeOnCompletion = true
    }
}

internal fun narrowUpdateCount(count: Long): Int {
    if (count > Int.MAX_VALUE) throw SQLException("update count $count exceeds Int.MAX_VALUE; use executeLargeUpdate")
    return count.toInt()
}
