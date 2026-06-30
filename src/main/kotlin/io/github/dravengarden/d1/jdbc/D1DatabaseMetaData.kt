package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.model.QueryResult
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.RowIdLifetime
import java.sql.Types

/** SQLite's implicit single schema; D1 exposes nothing else. */
private const val SCHEMA = "main"

/**
 * Schema introspection for D1, backed by SQLite's own catalog: `sqlite_master`
 * and the `PRAGMA table_info / index_list / index_info` family, run through the
 * wrangler core. The catalog methods reshape those query results into the fixed
 * column layouts JDBC specifies (so a client's schema tree is populated), and the
 * many capability predicates return SQLite-appropriate constants instead of
 * throwing — a client calls dozens of them while opening a connection.
 *
 * D1 has no catalogs, so `TABLE_CAT` is always NULL. It has one implicit schema,
 * [SCHEMA] (`main`) — reported by `getSchemas()` and stamped on every table —
 * because `PRAGMA database_list` (what a generic client uses to enumerate schemas)
 * is blocked by D1, and a client needs a schema node to introspect under. The
 * `*Pattern`/`catalog`/`schema` arguments are otherwise ignored except for
 * table-name filtering.
 */
public class D1DatabaseMetaData(
    private val connection: D1Connection,
) : AbstractDatabaseMetaData() {
    private val config = connection.config

    private fun query(sql: String): QueryResult = connection.introspect(sql)

    /** Read column [name] from a [QueryResult] row as text (or null). */
    private fun QueryResult.text(row: List<kotlinx.serialization.json.JsonElement>, name: String): String? {
        val idx = columns.indexOf(name)
        if (idx < 0) return null
        return row[idx].cellString()
    }

    // --- identity ---------------------------------------------------------

    override fun getConnection(): Connection = connection

    override fun getURL(): String =
        buildString {
            append("jdbc:d1:?db=").append(config.database)
            append("&transport=").append(if (config.transport == TransportKind.SSH) "ssh" else "normal")
            append("&mode=").append(if (config.mode == Mode.REMOTE) "remote" else "local")
            config.sshHost?.let { append("&host=").append(it) }
        }

    override fun getUserName(): String = ""

    override fun isReadOnly(): Boolean = connection.isReadOnly

    override fun getDatabaseProductName(): String = "SQLite"

    override fun getDatabaseProductVersion(): String = "3 (Cloudflare D1)"

    override fun getDriverName(): String = "d1-jdbc-driver"

    override fun getDriverVersion(): String = "${getDriverMajorVersion()}.${getDriverMinorVersion()}"

    override fun getDriverMajorVersion(): Int = 0

    override fun getDriverMinorVersion(): Int = 1

    override fun getDatabaseMajorVersion(): Int = 3

    override fun getDatabaseMinorVersion(): Int = 0

    override fun getJDBCMajorVersion(): Int = 4

    override fun getJDBCMinorVersion(): Int = 2

    override fun getIdentifierQuoteString(): String = "\""

    override fun getSearchStringEscape(): String = "\\"

    override fun getExtraNameCharacters(): String = ""

    override fun getSQLKeywords(): String = ""

    override fun getNumericFunctions(): String = ""

    override fun getStringFunctions(): String = ""

    override fun getSystemFunctions(): String = ""

    override fun getTimeDateFunctions(): String = ""

    override fun getCatalogTerm(): String = "catalog"

    override fun getSchemaTerm(): String = "schema"

    override fun getProcedureTerm(): String = "procedure"

    override fun getCatalogSeparator(): String = "."

    override fun isCatalogAtStart(): Boolean = true

    override fun getSQLStateType(): Int = DatabaseMetaData.sqlStateSQL

    override fun getRowIdLifetime(): RowIdLifetime = RowIdLifetime.ROWID_UNSUPPORTED

    override fun getDefaultTransactionIsolation(): Int = Connection.TRANSACTION_SERIALIZABLE

    // --- case handling ----------------------------------------------------

    override fun storesUpperCaseIdentifiers(): Boolean = false

    override fun storesLowerCaseIdentifiers(): Boolean = false

    override fun storesMixedCaseIdentifiers(): Boolean = true

    override fun supportsMixedCaseIdentifiers(): Boolean = false

    override fun storesUpperCaseQuotedIdentifiers(): Boolean = false

    override fun storesLowerCaseQuotedIdentifiers(): Boolean = false

    override fun storesMixedCaseQuotedIdentifiers(): Boolean = true

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean = false

    // --- null ordering ----------------------------------------------------

    override fun nullsAreSortedHigh(): Boolean = false

    override fun nullsAreSortedLow(): Boolean = true

    override fun nullsAreSortedAtStart(): Boolean = false

    override fun nullsAreSortedAtEnd(): Boolean = false

    override fun nullPlusNonNullIsNull(): Boolean = true

    // --- capabilities -----------------------------------------------------

    override fun allProceduresAreCallable(): Boolean = false

    override fun allTablesAreSelectable(): Boolean = true

    override fun usesLocalFiles(): Boolean = false

    override fun usesLocalFilePerTable(): Boolean = false

    override fun supportsColumnAliasing(): Boolean = true

    override fun supportsAlterTableWithAddColumn(): Boolean = true

    override fun supportsAlterTableWithDropColumn(): Boolean = false

    override fun supportsConvert(): Boolean = false

    override fun supportsConvert(fromType: Int, toType: Int): Boolean = false

    override fun supportsTableCorrelationNames(): Boolean = true

    override fun supportsDifferentTableCorrelationNames(): Boolean = false

    override fun supportsExpressionsInOrderBy(): Boolean = true

    override fun supportsOrderByUnrelated(): Boolean = true

    override fun supportsGroupBy(): Boolean = true

    override fun supportsGroupByUnrelated(): Boolean = true

    override fun supportsGroupByBeyondSelect(): Boolean = true

    override fun supportsLikeEscapeClause(): Boolean = true

    override fun supportsMultipleResultSets(): Boolean = false

    override fun supportsMultipleTransactions(): Boolean = true

    override fun supportsNonNullableColumns(): Boolean = true

    override fun supportsMinimumSQLGrammar(): Boolean = true

    override fun supportsCoreSQLGrammar(): Boolean = true

    override fun supportsExtendedSQLGrammar(): Boolean = false

    override fun supportsANSI92EntryLevelSQL(): Boolean = true

    override fun supportsANSI92IntermediateSQL(): Boolean = false

    override fun supportsANSI92FullSQL(): Boolean = false

    override fun supportsIntegrityEnhancementFacility(): Boolean = false

    override fun supportsOuterJoins(): Boolean = true

    override fun supportsFullOuterJoins(): Boolean = false

    override fun supportsLimitedOuterJoins(): Boolean = true

    override fun supportsSchemasInDataManipulation(): Boolean = false

    override fun supportsSchemasInProcedureCalls(): Boolean = false

    override fun supportsSchemasInTableDefinitions(): Boolean = false

    override fun supportsSchemasInIndexDefinitions(): Boolean = false

    override fun supportsSchemasInPrivilegeDefinitions(): Boolean = false

    override fun supportsCatalogsInDataManipulation(): Boolean = false

    override fun supportsCatalogsInProcedureCalls(): Boolean = false

    override fun supportsCatalogsInTableDefinitions(): Boolean = false

    override fun supportsCatalogsInIndexDefinitions(): Boolean = false

    override fun supportsCatalogsInPrivilegeDefinitions(): Boolean = false

    override fun supportsPositionedDelete(): Boolean = false

    override fun supportsPositionedUpdate(): Boolean = false

    override fun supportsSelectForUpdate(): Boolean = false

    override fun supportsStoredProcedures(): Boolean = false

    override fun supportsSubqueriesInComparisons(): Boolean = true

    override fun supportsSubqueriesInExists(): Boolean = true

    override fun supportsSubqueriesInIns(): Boolean = true

    override fun supportsSubqueriesInQuantifieds(): Boolean = false

    override fun supportsCorrelatedSubqueries(): Boolean = true

    override fun supportsUnion(): Boolean = true

    override fun supportsUnionAll(): Boolean = true

    override fun supportsOpenCursorsAcrossCommit(): Boolean = false

    override fun supportsOpenCursorsAcrossRollback(): Boolean = false

    override fun supportsOpenStatementsAcrossCommit(): Boolean = false

    override fun supportsOpenStatementsAcrossRollback(): Boolean = false

    override fun doesMaxRowSizeIncludeBlobs(): Boolean = false

    override fun supportsTransactions(): Boolean = true

    override fun supportsTransactionIsolationLevel(level: Int): Boolean = level == Connection.TRANSACTION_SERIALIZABLE

    override fun supportsDataDefinitionAndDataManipulationTransactions(): Boolean = true

    override fun supportsDataManipulationTransactionsOnly(): Boolean = false

    override fun dataDefinitionCausesTransactionCommit(): Boolean = false

    override fun dataDefinitionIgnoredInTransactions(): Boolean = false

    override fun supportsResultSetType(type: Int): Boolean = type == ResultSet.TYPE_FORWARD_ONLY

    override fun supportsResultSetConcurrency(type: Int, concurrency: Int): Boolean =
        type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY

    override fun ownUpdatesAreVisible(type: Int): Boolean = false

    override fun ownDeletesAreVisible(type: Int): Boolean = false

    override fun ownInsertsAreVisible(type: Int): Boolean = false

    override fun othersUpdatesAreVisible(type: Int): Boolean = false

    override fun othersDeletesAreVisible(type: Int): Boolean = false

    override fun othersInsertsAreVisible(type: Int): Boolean = false

    override fun updatesAreDetected(type: Int): Boolean = false

    override fun deletesAreDetected(type: Int): Boolean = false

    override fun insertsAreDetected(type: Int): Boolean = false

    override fun supportsBatchUpdates(): Boolean = false

    override fun supportsSavepoints(): Boolean = false

    override fun supportsNamedParameters(): Boolean = false

    override fun supportsMultipleOpenResults(): Boolean = false

    override fun supportsGetGeneratedKeys(): Boolean = false

    override fun supportsResultSetHoldability(holdability: Int): Boolean =
        holdability == ResultSet.CLOSE_CURSORS_AT_COMMIT

    override fun getResultSetHoldability(): Int = ResultSet.CLOSE_CURSORS_AT_COMMIT

    override fun locatorsUpdateCopy(): Boolean = false

    override fun supportsStatementPooling(): Boolean = false

    override fun supportsStoredFunctionsUsingCallSyntax(): Boolean = false

    override fun autoCommitFailureClosesAllResultSets(): Boolean = false

    override fun generatedKeyAlwaysReturned(): Boolean = false

    // --- maxima (0 means "no known limit") --------------------------------

    override fun getMaxBinaryLiteralLength(): Int = 0

    override fun getMaxCharLiteralLength(): Int = 0

    override fun getMaxColumnNameLength(): Int = 0

    override fun getMaxColumnsInGroupBy(): Int = 0

    override fun getMaxColumnsInIndex(): Int = 0

    override fun getMaxColumnsInOrderBy(): Int = 0

    override fun getMaxColumnsInSelect(): Int = 0

    override fun getMaxColumnsInTable(): Int = 0

    override fun getMaxConnections(): Int = 0

    override fun getMaxCursorNameLength(): Int = 0

    override fun getMaxIndexLength(): Int = 0

    override fun getMaxSchemaNameLength(): Int = 0

    override fun getMaxProcedureNameLength(): Int = 0

    override fun getMaxCatalogNameLength(): Int = 0

    override fun getMaxRowSize(): Int = 0

    override fun getMaxStatementLength(): Int = 0

    override fun getMaxStatements(): Int = 0

    override fun getMaxTableNameLength(): Int = 0

    override fun getMaxTablesInSelect(): Int = 0

    override fun getMaxUserNameLength(): Int = 0

    // --- catalog / schema enumeration -------------------------------------

    override fun getCatalogs(): ResultSet = metaResultSet(listOf("TABLE_CAT"), emptyList())

    override fun getSchemas(): ResultSet =
        // D1 blocks `PRAGMA database_list`, so we synthesize SQLite's implicit
        // single schema "main" here. A generic JDBC client (DataGrip) needs a
        // schema node to introspect under, and every table reports schema "main"
        // (cf. `PRAGMA table_list`), so the tables below are tagged to match.
        metaResultSet(listOf("TABLE_SCHEM", "TABLE_CATALOG"), listOf(listOf(SCHEMA, null)))

    override fun getSchemas(catalog: String?, schemaPattern: String?): ResultSet = getSchemas()

    override fun getTableTypes(): ResultSet =
        metaResultSet(listOf("TABLE_TYPE"), listOf(listOf("TABLE"), listOf("VIEW")))

    // --- tables / columns -------------------------------------------------

    override fun getTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<String>?,
    ): ResultSet {
        val wanted = types?.map { it.uppercase() }?.toSet()
        val rows = mutableListOf<List<Any?>>()
        val q = query(
            "SELECT name, type FROM sqlite_master " +
                "WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' " +
                likeClause("name", tableNamePattern) +
                "ORDER BY name",
        )
        for (row in q.rows) {
            val name = q.text(row, "name") ?: continue
            val jdbcType = if (q.text(row, "type") == "view") "VIEW" else "TABLE"
            if (wanted != null && jdbcType !in wanted) continue
            rows += listOf(null, SCHEMA, name, jdbcType, null, null, null, null, null, null)
        }
        return metaResultSet(
            listOf(
                "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS",
                "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION",
            ),
            rows,
        )
    }

    override fun getColumns(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        columnNamePattern: String?,
    ): ResultSet {
        val rows = mutableListOf<List<Any?>>()
        for (table in tableNames(tableNamePattern)) {
            val info = query("PRAGMA table_info(${quoteSqlString(table)})")
            for (row in info.rows) {
                val colName = info.text(row, "name") ?: continue
                if (columnNamePattern != null && !matchesLike(colName, columnNamePattern)) continue
                val declared = info.text(row, "type").orEmpty()
                val notNull = info.text(row, "notnull") == "1"
                val nullable = if (notNull) DatabaseMetaData.columnNoNulls else DatabaseMetaData.columnNullable
                val ordinal = (info.text(row, "cid")?.toIntOrNull() ?: 0) + 1
                rows += listOf(
                    null, SCHEMA, table, colName,
                    sqliteTypeToJdbc(declared), declared.ifEmpty { "TEXT" },
                    0, null, null, 10,
                    nullable, null, info.text(row, "dflt_value"), null, null, null,
                    ordinal, if (notNull) "NO" else "YES",
                    null, null, null, null, "NO", "NO",
                )
            }
        }
        return metaResultSet(
            listOf(
                "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME",
                "DATA_TYPE", "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH",
                "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE", "REMARKS",
                "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH",
                "ORDINAL_POSITION", "IS_NULLABLE", "SCOPE_CATALOG", "SCOPE_SCHEMA",
                "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT", "IS_GENERATEDCOLUMN",
            ),
            rows,
        )
    }

    override fun getPrimaryKeys(catalog: String?, schema: String?, table: String?): ResultSet {
        val rows = mutableListOf<List<Any?>>()
        if (table != null) {
            val info = query("PRAGMA table_info(${quoteSqlString(table)})")
            for (row in info.rows) {
                val pk = info.text(row, "pk")?.toIntOrNull() ?: 0
                if (pk <= 0) continue
                rows += listOf(null, SCHEMA, table, info.text(row, "name"), pk.toShort(), null)
            }
            rows.sortBy { it[4] as Short }
        }
        return metaResultSet(
            listOf("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME"),
            rows,
        )
    }

    override fun getIndexInfo(
        catalog: String?,
        schema: String?,
        table: String?,
        unique: Boolean,
        approximate: Boolean,
    ): ResultSet {
        val rows = mutableListOf<List<Any?>>()
        if (table != null) {
            val list = query("PRAGMA index_list(${quoteSqlString(table)})")
            for (idxRow in list.rows) {
                val indexName = list.text(idxRow, "name") ?: continue
                val isUnique = list.text(idxRow, "unique") == "1"
                if (unique && !isUnique) continue
                val cols = query("PRAGMA index_info(${quoteSqlString(indexName)})")
                for (colRow in cols.rows) {
                    val seq = (cols.text(colRow, "seqno")?.toIntOrNull() ?: 0) + 1
                    rows += listOf(
                        null, SCHEMA, table, !isUnique, null, indexName,
                        DatabaseMetaData.tableIndexOther.toShort(), seq.toShort(),
                        cols.text(colRow, "name"), "A", 0, 0, null,
                    )
                }
            }
        }
        return metaResultSet(
            listOf(
                "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE", "INDEX_QUALIFIER",
                "INDEX_NAME", "TYPE", "ORDINAL_POSITION", "COLUMN_NAME", "ASC_OR_DESC",
                "CARDINALITY", "PAGES", "FILTER_CONDITION",
            ),
            rows,
        )
    }

    // --- empty catalogs for unsupported relational features ---------------

    override fun getImportedKeys(catalog: String?, schema: String?, table: String?): ResultSet = emptyKeys()

    override fun getExportedKeys(catalog: String?, schema: String?, table: String?): ResultSet = emptyKeys()

    override fun getCrossReference(
        parentCatalog: String?,
        parentSchema: String?,
        parentTable: String?,
        foreignCatalog: String?,
        foreignSchema: String?,
        foreignTable: String?,
    ): ResultSet = emptyKeys()

    override fun getProcedures(catalog: String?, schemaPattern: String?, procedureNamePattern: String?): ResultSet =
        metaResultSet(
            listOf(
                "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "RESERVED1", "RESERVED2",
                "RESERVED3", "REMARKS", "PROCEDURE_TYPE", "SPECIFIC_NAME",
            ),
            emptyList(),
        )

    override fun getFunctions(catalog: String?, schemaPattern: String?, functionNamePattern: String?): ResultSet =
        metaResultSet(
            listOf("FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME", "REMARKS", "FUNCTION_TYPE", "SPECIFIC_NAME"),
            emptyList(),
        )

    override fun getClientInfoProperties(): ResultSet =
        metaResultSet(listOf("NAME", "MAX_LEN", "DEFAULT_VALUE", "DESCRIPTION"), emptyList())

    override fun getTypeInfo(): ResultSet =
        metaResultSet(
            listOf(
                "TYPE_NAME", "DATA_TYPE", "PRECISION", "LITERAL_PREFIX", "LITERAL_SUFFIX",
                "CREATE_PARAMS", "NULLABLE", "CASE_SENSITIVE", "SEARCHABLE", "UNSIGNED_ATTRIBUTE",
                "FIXED_PREC_SCALE", "AUTO_INCREMENT", "LOCAL_TYPE_NAME", "MINIMUM_SCALE",
                "MAXIMUM_SCALE", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "NUM_PREC_RADIX",
            ),
            listOf(
                typeRow("TEXT", Types.VARCHAR),
                typeRow("INTEGER", Types.BIGINT),
                typeRow("REAL", Types.DOUBLE),
                typeRow("BLOB", Types.BLOB),
            ),
        )

    // --- helpers ----------------------------------------------------------

    private fun emptyKeys(): ResultSet =
        metaResultSet(
            listOf(
                "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
                "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME",
                "KEY_SEQ", "UPDATE_RULE", "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY",
            ),
            emptyList(),
        )

    private fun typeRow(name: String, jdbcType: Int): List<Any?> =
        listOf(
            name, jdbcType, 0, null, null, null,
            DatabaseMetaData.typeNullable, false, DatabaseMetaData.typeSearchable, false,
            false, false, name, 0, 0, 0, 0, 10,
        )

    /** The base table names matching [pattern] (NULL = all), excluding views. */
    private fun tableNames(pattern: String?): List<String> {
        val q = query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                likeClause("name", pattern) + "ORDER BY name",
        )
        return q.rows.mapNotNull { q.text(it, "name") }
    }

    /** A trailing `AND <col> LIKE '<pattern>' ` fragment, or empty when no filter. */
    private fun likeClause(column: String, pattern: String?): String =
        if (pattern == null || pattern == "%") "" else "AND $column LIKE ${quoteSqlString(pattern)} "

    /** SQL LIKE semantics for client-side filtering (`%` and `_` wildcards). */
    private fun matchesLike(value: String, pattern: String): Boolean {
        if (pattern == "%") return true
        val regex = buildString {
            append('^')
            for (c in pattern) {
                when (c) {
                    '%' -> append(".*")
                    '_' -> append('.')
                    else -> append(Regex.escape(c.toString()))
                }
            }
            append('$')
        }
        return Regex(regex, RegexOption.IGNORE_CASE).matches(value)
    }

    private fun sqliteTypeToJdbc(declared: String): Int {
        val t = declared.uppercase()
        return when {
            t.contains("INT") -> Types.BIGINT
            t.contains("CHAR") || t.contains("CLOB") || t.contains("TEXT") -> Types.VARCHAR
            t.contains("BLOB") || t.isEmpty() -> Types.BLOB
            t.contains("REAL") || t.contains("FLOA") || t.contains("DOUB") -> Types.DOUBLE
            else -> Types.VARCHAR
        }
    }
}
