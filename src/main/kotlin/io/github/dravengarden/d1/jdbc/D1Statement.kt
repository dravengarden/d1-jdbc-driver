package io.github.dravengarden.d1.jdbc

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.SQLWarning

/**
 * A read-oriented [java.sql.Statement] backed by the wrangler core. Each
 * `execute`/`executeQuery` runs one `wrangler d1 execute` (via
 * [D1Connection.execute], which normalises errors to [SQLException]) and
 * materialises the whole result set into a [D1ResultSet].
 */
public class D1Statement(
    private val connection: D1Connection,
) : AbstractStatement() {
    private var current: D1ResultSet? = null
    private var updateCount = -1
    private var closed = false

    private fun require(sql: String?): String {
        if (closed) throw SQLException("statement is closed")
        return sql ?: throw SQLException("sql is null")
    }

    override fun executeQuery(sql: String?): ResultSet {
        val rs = D1ResultSet(connection.execute(require(sql)), this)
        current = rs
        updateCount = -1
        return rs
    }

    override fun executeUpdate(sql: String?): Int {
        val text = require(sql)
        val result = connection.execute(text)
        connection.invalidateIntrospection()
        current = null
        updateCount = result.changes.toInt()
        return updateCount
    }

    override fun execute(sql: String?): Boolean {
        val text = require(sql)
        return if (looksLikeQuery(text)) {
            current = D1ResultSet(connection.execute(text), this)
            updateCount = -1
            true
        } else {
            val result = connection.execute(text)
            connection.invalidateIntrospection()
            current = null
            updateCount = result.changes.toInt()
            false
        }
    }

    override fun getResultSet(): ResultSet? = current

    override fun getUpdateCount(): Int = updateCount

    override fun getMoreResults(): Boolean {
        current = null
        updateCount = -1
        return false
    }

    override fun getMoreResults(current: Int): Boolean = getMoreResults()

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
        // Unbounded; wrangler returns the full result set.
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
        // Not supported; left as a no-op so clients that call it still work.
    }
}
