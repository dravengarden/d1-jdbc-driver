package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.Access
import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Engine
import io.github.dravengarden.d1.model.QueryResult
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.sql.SQLWarning
import java.sql.Statement

/**
 * A JDBC [Connection] over a single D1 database reached through the wrangler core.
 * There is no persistent socket — each statement spawns a `wrangler` invocation —
 * so "connecting" is just holding the parsed [D1Config] and confirming the backend
 * answers `SELECT 1`. The connection is read-oriented and always in autocommit:
 * wrangler runs each statement atomically and exposes no transaction control.
 */
public class D1Connection internal constructor(
    internal val config: D1Config,
    internal val engine: Engine,
) : AbstractConnection() {
    public constructor(config: D1Config) : this(config, config.toEngine())

    private var closed = false
    private var access = config.access

    /**
     * Per-connection cache for schema-introspection queries. DataGrip issues the
     * same `sqlite_master` / `PRAGMA` reads dozens of times while building a tree,
     * and each is a ~1 s wrangler spawn; memoising them per connection makes a
     * sweep cheap. Cleared whenever a write runs, since DDL can change the schema.
     */
    private val introspectionCache = HashMap<String, QueryResult>()

    /**
     * The single point where SQL reaches wrangler. Translates ANY failure into a
     * plain [SQLException] — never a custom exception class — because a JDBC client
     * may run the driver out-of-process (DataGrip over RMI) and cannot deserialize
     * a `TransportException` it has never seen, turning the real error into a
     * confusing "class not found". Also short-circuits queries D1 blocks but a
     * client needs (e.g. `PRAGMA database_list`) with a synthetic result.
     */
    internal fun execute(sql: String): QueryResult {
        ensureOpen()
        requireAccess(sql)
        synthetic(sql)?.let { return it }
        return try {
            engine.query(sql)
        } catch (e: SQLException) {
            if (authDeniedPragma(sql, e.message)) QueryResult(emptyList(), emptyList()) else throw e
        } catch (e: Exception) {
            if (authDeniedPragma(sql, e.message)) QueryResult(emptyList(), emptyList()) else throw SQLException(e.message ?: e.toString())
        }
    }

    /**
     * D1's remote authorizer denies `PRAGMA` on its internal tables (`_cf_*`),
     * returning SQLITE_AUTH. A client (DataGrip) introspects every table including
     * those, so without this a single denial aborts the whole schema sweep. Scoped
     * to PRAGMA so a genuine auth failure on a user query still surfaces.
     */
    private fun authDeniedPragma(sql: String, message: String?): Boolean =
        message?.contains("SQLITE_AUTH") == true &&
            sql.trimStart().startsWith("pragma", ignoreCase = true) &&
            sql.contains("_cf_", ignoreCase = true)

    /** The client-side write guardrail: refuse to send SQL above the granted [access]. */
    private fun requireAccess(sql: String) {
        val need = classifyStatement(sql)
        val allowed =
            when (access) {
                Access.READ -> need == StatementClass.READ
                Access.WRITE -> need != StatementClass.DDL
                Access.DDL -> true
            }
        if (!allowed) {
            val message =
                if (need == StatementClass.DDL) {
                    "Schema changes (DDL) are not allowed on this connection (access=${access.name.lowercase()}). " +
                        "Add 'access=ddl' to the JDBC URL to enable them."
                } else {
                    "This connection is read-only (access=read). Add 'access=write' to the JDBC URL to modify " +
                        "data, or 'access=ddl' to also change the schema."
                }
            throw SQLException(message)
        }
    }

    /** Verify the engine's CLI is present (clear error before the first query). */
    internal fun preflight() {
        try {
            engine.checkAvailable()
        } catch (e: SQLException) {
            throw e
        } catch (e: Exception) {
            throw SQLException(e.message ?: e.toString())
        }
    }

    internal fun introspect(sql: String): QueryResult =
        if (config.cacheIntrospection) {
            synchronized(introspectionCache) { introspectionCache.getOrPut(sql) { execute(sql) } }
        } else {
            execute(sql)
        }

    /**
     * Answers for the no-arg informational PRAGMAs a SQLite client (DataGrip)
     * runs while introspecting but D1 rejects (exit 1) — `database_list`,
     * `collation_list`, `function_list`, `module_list`, `compile_options`,
     * `encoding`, `pragma_list`. Returning a sane synthetic result keeps schema
     * introspection from aborting. Arg-taking PRAGMAs (`table_info`, `index_list`,
     * …) work on D1 and fall through to wrangler.
     */
    private fun synthetic(sql: String): QueryResult? {
        val spec = sql.trim().trimEnd(';').trim()
        if (!spec.startsWith("pragma ", ignoreCase = true)) return null
        // Strip "pragma ", any args, and an optional schema qualifier (`main.`).
        val name = spec.substring(7).substringBefore('(').substringAfterLast('.').trim().trim('"').lowercase()
        return when (name) {
            "database_list" -> qr(listOf("seq", "name", "file"), listOf(listOf(0, "main", "")))
            "collation_list" -> qr(listOf("seq", "name"), listOf(listOf(0, "BINARY"), listOf(1, "NOCASE"), listOf(2, "RTRIM")))
            "encoding" -> qr(listOf("encoding"), listOf(listOf("UTF-8")))
            "function_list" -> qr(listOf("name", "builtin", "type", "enc", "narg", "flags"), emptyList())
            "module_list", "pragma_list" -> qr(listOf("name"), emptyList())
            "compile_options" -> qr(listOf("compile_options"), emptyList())
            else -> null
        }
    }

    private fun qr(columns: List<String>, rows: List<List<Any?>>): QueryResult =
        QueryResult(
            columns,
            rows.map { row ->
                row.map { v ->
                    when (v) {
                        null -> JsonNull
                        is Int -> JsonPrimitive(v)
                        is String -> JsonPrimitive(v)
                        else -> JsonPrimitive(v.toString())
                    }
                }
            },
        )

    internal fun invalidateIntrospection() {
        synchronized(introspectionCache) { introspectionCache.clear() }
    }

    /** Run `SELECT 1` so a misconfigured URL / unreachable backend fails at connect. */
    internal fun checkConnectivity() {
        execute("SELECT 1")
    }

    private fun require(sql: String?): String = sql ?: throw SQLException("sql is null")

    override fun createStatement(): Statement {
        ensureOpen()
        return D1Statement(this)
    }

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement {
        requireResultSetOptions(resultSetType, resultSetConcurrency, java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT)
        return createStatement()
    }

    override fun createStatement(
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int,
    ): Statement {
        requireResultSetOptions(resultSetType, resultSetConcurrency, resultSetHoldability)
        return createStatement()
    }

    override fun prepareStatement(sql: String?): PreparedStatement {
        ensureOpen()
        return D1PreparedStatement(this, require(sql))
    }

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
    ): PreparedStatement {
        requireResultSetOptions(resultSetType, resultSetConcurrency, java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT)
        return prepareStatement(sql)
    }

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int,
    ): PreparedStatement {
        requireResultSetOptions(resultSetType, resultSetConcurrency, resultSetHoldability)
        return prepareStatement(sql)
    }

    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement {
        if (autoGeneratedKeys != Statement.NO_GENERATED_KEYS) {
            throw SQLFeatureNotSupportedException("generated keys are not supported")
        }
        return prepareStatement(sql)
    }

    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement {
        if (columnIndexes != null && columnIndexes.isNotEmpty()) {
            throw SQLFeatureNotSupportedException("generated keys are not supported")
        }
        return prepareStatement(sql)
    }

    override fun prepareStatement(sql: String?, columnNames: Array<String>?): PreparedStatement {
        if (columnNames != null && columnNames.isNotEmpty()) {
            throw SQLFeatureNotSupportedException("generated keys are not supported")
        }
        return prepareStatement(sql)
    }

    override fun getMetaData(): DatabaseMetaData {
        ensureOpen()
        return D1DatabaseMetaData(this)
    }

    override fun isValid(timeout: Int): Boolean =
        if (timeout < 0) {
            throw SQLException("timeout must be non-negative")
        } else if (closed) {
            false
        } else {
            try {
                checkConnectivity()
                true
            } catch (_: Exception) {
                false
            }
        }

    internal fun ensureOpen() {
        if (closed) throw SQLException("connection is closed")
    }

    override fun nativeSQL(sql: String?): String {
        ensureOpen()
        return sql ?: throw SQLException("sql is null")
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        ensureOpen()
        if (!autoCommit) throw SQLFeatureNotSupportedException("d1 has no connection-level transactions (autocommit only)")
    }

    override fun getAutoCommit(): Boolean {
        ensureOpen()
        return true
    }

    override fun commit() {
        ensureOpen()
        throw SQLFeatureNotSupportedException("d1 has no connection-level transactions (autocommit only)")
    }

    override fun rollback() {
        ensureOpen()
        throw SQLFeatureNotSupportedException("d1 has no connection-level transactions (autocommit only)")
    }

    override fun close() {
        closed = true
        invalidateIntrospection()
    }

    override fun isClosed(): Boolean = closed

    override fun setReadOnly(readOnly: Boolean) {
        ensureOpen()
        // `false` restores only the URL-granted level; it must never turn the
        // default read connection into a writable one implicitly.
        access = if (readOnly) Access.READ else config.access
    }

    override fun isReadOnly(): Boolean = access == Access.READ

    override fun setCatalog(catalog: String?) {
        // D1 has no catalog concept.
    }

    override fun getCatalog(): String? = null

    override fun setSchema(schema: String?) {
        // D1 has no schema concept.
    }

    override fun getSchema(): String? = null

    override fun setTransactionIsolation(level: Int) {
        ensureOpen()
        if (level != Connection.TRANSACTION_NONE) {
            throw SQLFeatureNotSupportedException("only TRANSACTION_NONE is supported")
        }
    }

    override fun getTransactionIsolation(): Int {
        ensureOpen()
        return Connection.TRANSACTION_NONE
    }

    override fun getWarnings(): SQLWarning? = null

    override fun clearWarnings() {
        // No warnings are accumulated.
    }

    override fun setHoldability(holdability: Int) {
        ensureOpen()
        if (holdability != java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT) {
            throw SQLFeatureNotSupportedException("only CLOSE_CURSORS_AT_COMMIT is supported")
        }
    }

    override fun getHoldability(): Int = java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT

    override fun getTypeMap(): MutableMap<String, Class<*>> = mutableMapOf()

    override fun getNetworkTimeout(): Int = 0

    private fun requireResultSetOptions(type: Int, concurrency: Int, holdability: Int) {
        if (type != java.sql.ResultSet.TYPE_FORWARD_ONLY || concurrency != java.sql.ResultSet.CONCUR_READ_ONLY) {
            throw SQLFeatureNotSupportedException("only forward-only, read-only result sets are supported")
        }
        if (holdability != java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT) {
            throw SQLFeatureNotSupportedException("only CLOSE_CURSORS_AT_COMMIT is supported")
        }
    }
}
