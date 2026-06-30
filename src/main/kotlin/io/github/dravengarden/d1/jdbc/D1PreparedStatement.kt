package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.Wrangler
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.SQLWarning

/**
 * A [java.sql.PreparedStatement] whose positional `?` parameters are bound
 * client-side and spliced into the SQL before it reaches wrangler (see [Sql]).
 * Only the setters a browsing client needs are implemented; the rest fall through
 * to [AbstractPreparedStatement] (throwing).
 */
public class D1PreparedStatement(
    private val connection: D1Connection,
    private val wrangler: Wrangler,
    private val sql: String,
) : AbstractPreparedStatement() {
    private val params = mutableMapOf<Int, String>()
    private var current: D1ResultSet? = null
    private var updateCount = -1
    private var closed = false

    private fun bind(index: Int, literal: String) {
        if (index < 1) throw SQLException("parameter index out of range: $index")
        params[index] = literal
    }

    private fun bound(): String {
        if (closed) throw SQLException("statement is closed")
        return substituteParams(sql, params)
    }

    override fun executeQuery(): ResultSet {
        val rs = D1ResultSet(wrangler.execute(bound()), this)
        current = rs
        updateCount = -1
        return rs
    }

    override fun executeUpdate(): Int {
        val result = wrangler.execute(bound())
        connection.invalidateIntrospection()
        current = null
        updateCount = result.changes.toInt()
        return updateCount
    }

    override fun execute(): Boolean {
        val text = bound()
        return if (looksLikeQuery(text)) {
            current = D1ResultSet(wrangler.execute(text), this)
            updateCount = -1
            true
        } else {
            val result = wrangler.execute(text)
            connection.invalidateIntrospection()
            current = null
            updateCount = result.changes.toInt()
            false
        }
    }

    override fun clearParameters() {
        params.clear()
    }

    override fun setNull(parameterIndex: Int, sqlType: Int): Unit = bind(parameterIndex, "NULL")

    override fun setNull(parameterIndex: Int, sqlType: Int, typeName: String?): Unit = bind(parameterIndex, "NULL")

    override fun setBoolean(parameterIndex: Int, x: Boolean): Unit = bind(parameterIndex, if (x) "1" else "0")

    override fun setByte(parameterIndex: Int, x: Byte): Unit = bind(parameterIndex, x.toString())

    override fun setShort(parameterIndex: Int, x: Short): Unit = bind(parameterIndex, x.toString())

    override fun setInt(parameterIndex: Int, x: Int): Unit = bind(parameterIndex, x.toString())

    override fun setLong(parameterIndex: Int, x: Long): Unit = bind(parameterIndex, x.toString())

    override fun setFloat(parameterIndex: Int, x: Float): Unit = bind(parameterIndex, x.toString())

    override fun setDouble(parameterIndex: Int, x: Double): Unit = bind(parameterIndex, x.toString())

    override fun setBigDecimal(parameterIndex: Int, x: BigDecimal?): Unit =
        bind(parameterIndex, x?.toPlainString() ?: "NULL")

    override fun setString(parameterIndex: Int, x: String?): Unit =
        bind(parameterIndex, if (x == null) "NULL" else quoteSqlString(x))

    override fun setNString(parameterIndex: Int, value: String?): Unit = setString(parameterIndex, value)

    override fun setObject(parameterIndex: Int, x: Any?): Unit = bind(parameterIndex, sqlLiteralOf(x))

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: Int): Unit = setObject(parameterIndex, x)

    override fun setObject(parameterIndex: Int, x: Any?, targetSqlType: Int, scaleOrLength: Int): Unit =
        setObject(parameterIndex, x)

    override fun getResultSet(): ResultSet? = current

    override fun getUpdateCount(): Int = updateCount

    override fun getMoreResults(): Boolean {
        current = null
        updateCount = -1
        return false
    }

    override fun getMoreResults(current: Int): Boolean = getMoreResults()

    override fun getMetaData(): ResultSetMetaData? = current?.metaData

    override fun close() {
        closed = true
        current?.close()
    }

    override fun isClosed(): Boolean = closed

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
        // No streaming cursor; nothing to size.
    }

    override fun getMaxRows(): Int = 0

    override fun setMaxRows(max: Int) {
        // Unbounded.
    }

    override fun getMaxFieldSize(): Int = 0

    override fun setMaxFieldSize(max: Int) {
        // No per-field cap.
    }

    override fun getQueryTimeout(): Int = 0

    override fun setQueryTimeout(seconds: Int) {
        // The transport owns its own timeout.
    }

    override fun setEscapeProcessing(enable: Boolean) {
        // SQL is passed through verbatim.
    }

    override fun getWarnings(): SQLWarning? = null

    override fun clearWarnings() {
        // No warnings are accumulated.
    }

    override fun isPoolable(): Boolean = false

    override fun setPoolable(poolable: Boolean) {
        // Not pooled.
    }

    override fun isCloseOnCompletion(): Boolean = false

    override fun closeOnCompletion() {
        // No-op so clients that call it still work.
    }
}
