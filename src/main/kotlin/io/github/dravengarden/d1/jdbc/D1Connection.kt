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
    private var autoCommit = true
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
        requireAccess(sql)
        synthetic(sql)?.let { return it }
        return try {
            engine.query(sql)
        } catch (e: SQLException) {
            throw e
        } catch (e: Exception) {
            throw SQLException(e.message ?: e.toString())
        }
    }

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
        if (config.cacheIntrospection) introspectionCache.getOrPut(sql) { execute(sql) } else execute(sql)

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
        introspectionCache.clear()
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

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement = createStatement()

    override fun createStatement(
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int,
    ): Statement = createStatement()

    override fun prepareStatement(sql: String?): PreparedStatement {
        ensureOpen()
        return D1PreparedStatement(this, require(sql))
    }

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
    ): PreparedStatement = prepareStatement(sql)

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int,
    ): PreparedStatement = prepareStatement(sql)

    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement = prepareStatement(sql)

    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement = prepareStatement(sql)

    override fun prepareStatement(sql: String?, columnNames: Array<String>?): PreparedStatement = prepareStatement(sql)

    override fun getMetaData(): DatabaseMetaData = D1DatabaseMetaData(this)

    override fun isValid(timeout: Int): Boolean =
        if (closed) {
            false
        } else {
            try {
                checkConnectivity()
                true
            } catch (_: Exception) {
                false
            }
        }

    private fun ensureOpen() {
        if (closed) throw SQLException("connection is closed")
    }

    override fun nativeSQL(sql: String?): String = sql ?: ""

    override fun setAutoCommit(autoCommit: Boolean) {
        this.autoCommit = autoCommit
    }

    override fun getAutoCommit(): Boolean = autoCommit

    override fun commit() {
        // Each wrangler invocation is atomic; there is no open transaction to commit.
    }

    override fun rollback() {
        throw SQLException("d1 has no transaction control (autocommit only)")
    }

    override fun close() {
        closed = true
    }

    override fun isClosed(): Boolean = closed

    override fun setReadOnly(readOnly: Boolean) {
        // JDBC's coarse toggle maps onto the access ladder: read vs (at least) write.
        access = if (readOnly) Access.READ else maxOf(access, Access.WRITE)
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
        // Only TRANSACTION_NONE is meaningful.
    }

    override fun getTransactionIsolation(): Int = Connection.TRANSACTION_NONE

    override fun getWarnings(): SQLWarning? = null

    override fun clearWarnings() {
        // No warnings are accumulated.
    }

    override fun setHoldability(holdability: Int) {
        // Single holdability; ignore.
    }

    override fun getHoldability(): Int = java.sql.ResultSet.CLOSE_CURSORS_AT_COMMIT

    override fun getTypeMap(): MutableMap<String, Class<*>> = mutableMapOf()

    override fun getNetworkTimeout(): Int = 0
}
