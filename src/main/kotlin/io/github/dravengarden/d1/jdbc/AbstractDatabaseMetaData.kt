package io.github.dravengarden.d1.jdbc

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.RowIdLifetime
import java.sql.SQLFeatureNotSupportedException

private fun ni(name: String): Nothing =
    throw SQLFeatureNotSupportedException("d1: DatabaseMetaData.$name not supported")

/**
 * Default [DatabaseMetaData] implementation for the d1 JDBC driver where every
 * method throws [SQLFeatureNotSupportedException]. Concrete drivers override the
 * subset they actually support.
 */
public abstract class AbstractDatabaseMetaData : DatabaseMetaData {
    // java.sql.Wrapper
    override fun <T : Any?> unwrap(iface: Class<T>?): T = ni("unwrap")

    override fun isWrapperFor(iface: Class<*>?): Boolean = ni("isWrapperFor")

    // Boolean capability / behaviour methods
    override fun allProceduresAreCallable(): Boolean = ni("allProceduresAreCallable")

    override fun allTablesAreSelectable(): Boolean = ni("allTablesAreSelectable")

    override fun nullsAreSortedHigh(): Boolean = ni("nullsAreSortedHigh")

    override fun nullsAreSortedLow(): Boolean = ni("nullsAreSortedLow")

    override fun nullsAreSortedAtStart(): Boolean = ni("nullsAreSortedAtStart")

    override fun nullsAreSortedAtEnd(): Boolean = ni("nullsAreSortedAtEnd")

    override fun usesLocalFiles(): Boolean = ni("usesLocalFiles")

    override fun usesLocalFilePerTable(): Boolean = ni("usesLocalFilePerTable")

    override fun supportsMixedCaseIdentifiers(): Boolean = ni("supportsMixedCaseIdentifiers")

    override fun storesUpperCaseIdentifiers(): Boolean = ni("storesUpperCaseIdentifiers")

    override fun storesLowerCaseIdentifiers(): Boolean = ni("storesLowerCaseIdentifiers")

    override fun storesMixedCaseIdentifiers(): Boolean = ni("storesMixedCaseIdentifiers")

    override fun supportsMixedCaseQuotedIdentifiers(): Boolean =
        ni("supportsMixedCaseQuotedIdentifiers")

    override fun storesUpperCaseQuotedIdentifiers(): Boolean =
        ni("storesUpperCaseQuotedIdentifiers")

    override fun storesLowerCaseQuotedIdentifiers(): Boolean =
        ni("storesLowerCaseQuotedIdentifiers")

    override fun storesMixedCaseQuotedIdentifiers(): Boolean =
        ni("storesMixedCaseQuotedIdentifiers")

    override fun supportsAlterTableWithAddColumn(): Boolean =
        ni("supportsAlterTableWithAddColumn")

    override fun supportsAlterTableWithDropColumn(): Boolean =
        ni("supportsAlterTableWithDropColumn")

    override fun supportsColumnAliasing(): Boolean = ni("supportsColumnAliasing")

    override fun nullPlusNonNullIsNull(): Boolean = ni("nullPlusNonNullIsNull")

    override fun supportsConvert(): Boolean = ni("supportsConvert")

    override fun supportsConvert(fromType: Int, toType: Int): Boolean = ni("supportsConvert")

    override fun supportsTableCorrelationNames(): Boolean = ni("supportsTableCorrelationNames")

    override fun supportsDifferentTableCorrelationNames(): Boolean =
        ni("supportsDifferentTableCorrelationNames")

    override fun supportsExpressionsInOrderBy(): Boolean = ni("supportsExpressionsInOrderBy")

    override fun supportsOrderByUnrelated(): Boolean = ni("supportsOrderByUnrelated")

    override fun supportsGroupBy(): Boolean = ni("supportsGroupBy")

    override fun supportsGroupByUnrelated(): Boolean = ni("supportsGroupByUnrelated")

    override fun supportsGroupByBeyondSelect(): Boolean = ni("supportsGroupByBeyondSelect")

    override fun supportsLikeEscapeClause(): Boolean = ni("supportsLikeEscapeClause")

    override fun supportsMultipleResultSets(): Boolean = ni("supportsMultipleResultSets")

    override fun supportsMultipleTransactions(): Boolean = ni("supportsMultipleTransactions")

    override fun supportsNonNullableColumns(): Boolean = ni("supportsNonNullableColumns")

    override fun supportsMinimumSQLGrammar(): Boolean = ni("supportsMinimumSQLGrammar")

    override fun supportsCoreSQLGrammar(): Boolean = ni("supportsCoreSQLGrammar")

    override fun supportsExtendedSQLGrammar(): Boolean = ni("supportsExtendedSQLGrammar")

    override fun supportsANSI92EntryLevelSQL(): Boolean = ni("supportsANSI92EntryLevelSQL")

    override fun supportsANSI92IntermediateSQL(): Boolean = ni("supportsANSI92IntermediateSQL")

    override fun supportsANSI92FullSQL(): Boolean = ni("supportsANSI92FullSQL")

    override fun supportsIntegrityEnhancementFacility(): Boolean =
        ni("supportsIntegrityEnhancementFacility")

    override fun supportsOuterJoins(): Boolean = ni("supportsOuterJoins")

    override fun supportsFullOuterJoins(): Boolean = ni("supportsFullOuterJoins")

    override fun supportsLimitedOuterJoins(): Boolean = ni("supportsLimitedOuterJoins")

    override fun isCatalogAtStart(): Boolean = ni("isCatalogAtStart")

    override fun supportsSchemasInDataManipulation(): Boolean =
        ni("supportsSchemasInDataManipulation")

    override fun supportsSchemasInProcedureCalls(): Boolean =
        ni("supportsSchemasInProcedureCalls")

    override fun supportsSchemasInTableDefinitions(): Boolean =
        ni("supportsSchemasInTableDefinitions")

    override fun supportsSchemasInIndexDefinitions(): Boolean =
        ni("supportsSchemasInIndexDefinitions")

    override fun supportsSchemasInPrivilegeDefinitions(): Boolean =
        ni("supportsSchemasInPrivilegeDefinitions")

    override fun supportsCatalogsInDataManipulation(): Boolean =
        ni("supportsCatalogsInDataManipulation")

    override fun supportsCatalogsInProcedureCalls(): Boolean =
        ni("supportsCatalogsInProcedureCalls")

    override fun supportsCatalogsInTableDefinitions(): Boolean =
        ni("supportsCatalogsInTableDefinitions")

    override fun supportsCatalogsInIndexDefinitions(): Boolean =
        ni("supportsCatalogsInIndexDefinitions")

    override fun supportsCatalogsInPrivilegeDefinitions(): Boolean =
        ni("supportsCatalogsInPrivilegeDefinitions")

    override fun supportsPositionedDelete(): Boolean = ni("supportsPositionedDelete")

    override fun supportsPositionedUpdate(): Boolean = ni("supportsPositionedUpdate")

    override fun supportsSelectForUpdate(): Boolean = ni("supportsSelectForUpdate")

    override fun supportsStoredProcedures(): Boolean = ni("supportsStoredProcedures")

    override fun supportsSubqueriesInComparisons(): Boolean =
        ni("supportsSubqueriesInComparisons")

    override fun supportsSubqueriesInExists(): Boolean = ni("supportsSubqueriesInExists")

    override fun supportsSubqueriesInIns(): Boolean = ni("supportsSubqueriesInIns")

    override fun supportsSubqueriesInQuantifieds(): Boolean =
        ni("supportsSubqueriesInQuantifieds")

    override fun supportsCorrelatedSubqueries(): Boolean = ni("supportsCorrelatedSubqueries")

    override fun supportsUnion(): Boolean = ni("supportsUnion")

    override fun supportsUnionAll(): Boolean = ni("supportsUnionAll")

    override fun supportsOpenCursorsAcrossCommit(): Boolean =
        ni("supportsOpenCursorsAcrossCommit")

    override fun supportsOpenCursorsAcrossRollback(): Boolean =
        ni("supportsOpenCursorsAcrossRollback")

    override fun supportsOpenStatementsAcrossCommit(): Boolean =
        ni("supportsOpenStatementsAcrossCommit")

    override fun supportsOpenStatementsAcrossRollback(): Boolean =
        ni("supportsOpenStatementsAcrossRollback")

    override fun doesMaxRowSizeIncludeBlobs(): Boolean = ni("doesMaxRowSizeIncludeBlobs")

    override fun supportsTransactions(): Boolean = ni("supportsTransactions")

    override fun supportsTransactionIsolationLevel(level: Int): Boolean =
        ni("supportsTransactionIsolationLevel")

    override fun supportsDataDefinitionAndDataManipulationTransactions(): Boolean =
        ni("supportsDataDefinitionAndDataManipulationTransactions")

    override fun supportsDataManipulationTransactionsOnly(): Boolean =
        ni("supportsDataManipulationTransactionsOnly")

    override fun dataDefinitionCausesTransactionCommit(): Boolean =
        ni("dataDefinitionCausesTransactionCommit")

    override fun dataDefinitionIgnoredInTransactions(): Boolean =
        ni("dataDefinitionIgnoredInTransactions")

    override fun supportsResultSetType(type: Int): Boolean = ni("supportsResultSetType")

    override fun supportsResultSetConcurrency(type: Int, concurrency: Int): Boolean =
        ni("supportsResultSetConcurrency")

    override fun ownUpdatesAreVisible(type: Int): Boolean = ni("ownUpdatesAreVisible")

    override fun ownDeletesAreVisible(type: Int): Boolean = ni("ownDeletesAreVisible")

    override fun ownInsertsAreVisible(type: Int): Boolean = ni("ownInsertsAreVisible")

    override fun othersUpdatesAreVisible(type: Int): Boolean = ni("othersUpdatesAreVisible")

    override fun othersDeletesAreVisible(type: Int): Boolean = ni("othersDeletesAreVisible")

    override fun othersInsertsAreVisible(type: Int): Boolean = ni("othersInsertsAreVisible")

    override fun updatesAreDetected(type: Int): Boolean = ni("updatesAreDetected")

    override fun deletesAreDetected(type: Int): Boolean = ni("deletesAreDetected")

    override fun insertsAreDetected(type: Int): Boolean = ni("insertsAreDetected")

    override fun supportsBatchUpdates(): Boolean = ni("supportsBatchUpdates")

    override fun supportsSavepoints(): Boolean = ni("supportsSavepoints")

    override fun supportsNamedParameters(): Boolean = ni("supportsNamedParameters")

    override fun supportsMultipleOpenResults(): Boolean = ni("supportsMultipleOpenResults")

    override fun supportsGetGeneratedKeys(): Boolean = ni("supportsGetGeneratedKeys")

    override fun supportsResultSetHoldability(holdability: Int): Boolean =
        ni("supportsResultSetHoldability")

    override fun locatorsUpdateCopy(): Boolean = ni("locatorsUpdateCopy")

    override fun supportsStatementPooling(): Boolean = ni("supportsStatementPooling")

    override fun supportsStoredFunctionsUsingCallSyntax(): Boolean =
        ni("supportsStoredFunctionsUsingCallSyntax")

    override fun autoCommitFailureClosesAllResultSets(): Boolean =
        ni("autoCommitFailureClosesAllResultSets")

    override fun isReadOnly(): Boolean = ni("isReadOnly")

    override fun generatedKeyAlwaysReturned(): Boolean = ni("generatedKeyAlwaysReturned")

    // String / nullable-String methods
    override fun getURL(): String? = ni("getURL")

    override fun getUserName(): String? = ni("getUserName")

    override fun getDatabaseProductName(): String? = ni("getDatabaseProductName")

    override fun getDatabaseProductVersion(): String? = ni("getDatabaseProductVersion")

    override fun getDriverName(): String? = ni("getDriverName")

    override fun getDriverVersion(): String? = ni("getDriverVersion")

    override fun getIdentifierQuoteString(): String? = ni("getIdentifierQuoteString")

    override fun getSQLKeywords(): String? = ni("getSQLKeywords")

    override fun getNumericFunctions(): String? = ni("getNumericFunctions")

    override fun getStringFunctions(): String? = ni("getStringFunctions")

    override fun getSystemFunctions(): String? = ni("getSystemFunctions")

    override fun getTimeDateFunctions(): String? = ni("getTimeDateFunctions")

    override fun getSearchStringEscape(): String? = ni("getSearchStringEscape")

    override fun getExtraNameCharacters(): String? = ni("getExtraNameCharacters")

    override fun getSchemaTerm(): String? = ni("getSchemaTerm")

    override fun getProcedureTerm(): String? = ni("getProcedureTerm")

    override fun getCatalogTerm(): String? = ni("getCatalogTerm")

    override fun getCatalogSeparator(): String? = ni("getCatalogSeparator")

    // Int methods
    override fun getDriverMajorVersion(): Int = ni("getDriverMajorVersion")

    override fun getDriverMinorVersion(): Int = ni("getDriverMinorVersion")

    override fun getMaxBinaryLiteralLength(): Int = ni("getMaxBinaryLiteralLength")

    override fun getMaxCharLiteralLength(): Int = ni("getMaxCharLiteralLength")

    override fun getMaxColumnNameLength(): Int = ni("getMaxColumnNameLength")

    override fun getMaxColumnsInGroupBy(): Int = ni("getMaxColumnsInGroupBy")

    override fun getMaxColumnsInIndex(): Int = ni("getMaxColumnsInIndex")

    override fun getMaxColumnsInOrderBy(): Int = ni("getMaxColumnsInOrderBy")

    override fun getMaxColumnsInSelect(): Int = ni("getMaxColumnsInSelect")

    override fun getMaxColumnsInTable(): Int = ni("getMaxColumnsInTable")

    override fun getMaxConnections(): Int = ni("getMaxConnections")

    override fun getMaxCursorNameLength(): Int = ni("getMaxCursorNameLength")

    override fun getMaxIndexLength(): Int = ni("getMaxIndexLength")

    override fun getMaxSchemaNameLength(): Int = ni("getMaxSchemaNameLength")

    override fun getMaxProcedureNameLength(): Int = ni("getMaxProcedureNameLength")

    override fun getMaxCatalogNameLength(): Int = ni("getMaxCatalogNameLength")

    override fun getMaxRowSize(): Int = ni("getMaxRowSize")

    override fun getMaxStatementLength(): Int = ni("getMaxStatementLength")

    override fun getMaxStatements(): Int = ni("getMaxStatements")

    override fun getMaxTableNameLength(): Int = ni("getMaxTableNameLength")

    override fun getMaxTablesInSelect(): Int = ni("getMaxTablesInSelect")

    override fun getMaxUserNameLength(): Int = ni("getMaxUserNameLength")

    override fun getDefaultTransactionIsolation(): Int = ni("getDefaultTransactionIsolation")

    override fun getResultSetHoldability(): Int = ni("getResultSetHoldability")

    override fun getDatabaseMajorVersion(): Int = ni("getDatabaseMajorVersion")

    override fun getDatabaseMinorVersion(): Int = ni("getDatabaseMinorVersion")

    override fun getJDBCMajorVersion(): Int = ni("getJDBCMajorVersion")

    override fun getJDBCMinorVersion(): Int = ni("getJDBCMinorVersion")

    override fun getSQLStateType(): Int = ni("getSQLStateType")

    // Connection
    override fun getConnection(): Connection = ni("getConnection")

    // RowIdLifetime
    override fun getRowIdLifetime(): RowIdLifetime = ni("getRowIdLifetime")

    // ResultSet-returning catalog methods
    override fun getProcedures(
        catalog: String?,
        schemaPattern: String?,
        procedureNamePattern: String?,
    ): ResultSet = ni("getProcedures")

    override fun getProcedureColumns(
        catalog: String?,
        schemaPattern: String?,
        procedureNamePattern: String?,
        columnNamePattern: String?,
    ): ResultSet = ni("getProcedureColumns")

    override fun getTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        types: Array<String>?,
    ): ResultSet = ni("getTables")

    override fun getSchemas(): ResultSet = ni("getSchemas")

    override fun getSchemas(catalog: String?, schemaPattern: String?): ResultSet =
        ni("getSchemas")

    override fun getCatalogs(): ResultSet = ni("getCatalogs")

    override fun getTableTypes(): ResultSet = ni("getTableTypes")

    override fun getColumns(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        columnNamePattern: String?,
    ): ResultSet = ni("getColumns")

    override fun getColumnPrivileges(
        catalog: String?,
        schema: String?,
        table: String?,
        columnNamePattern: String?,
    ): ResultSet = ni("getColumnPrivileges")

    override fun getTablePrivileges(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
    ): ResultSet = ni("getTablePrivileges")

    override fun getBestRowIdentifier(
        catalog: String?,
        schema: String?,
        table: String?,
        scope: Int,
        nullable: Boolean,
    ): ResultSet = ni("getBestRowIdentifier")

    override fun getVersionColumns(
        catalog: String?,
        schema: String?,
        table: String?,
    ): ResultSet = ni("getVersionColumns")

    override fun getPrimaryKeys(
        catalog: String?,
        schema: String?,
        table: String?,
    ): ResultSet = ni("getPrimaryKeys")

    override fun getImportedKeys(
        catalog: String?,
        schema: String?,
        table: String?,
    ): ResultSet = ni("getImportedKeys")

    override fun getExportedKeys(
        catalog: String?,
        schema: String?,
        table: String?,
    ): ResultSet = ni("getExportedKeys")

    override fun getCrossReference(
        parentCatalog: String?,
        parentSchema: String?,
        parentTable: String?,
        foreignCatalog: String?,
        foreignSchema: String?,
        foreignTable: String?,
    ): ResultSet = ni("getCrossReference")

    override fun getTypeInfo(): ResultSet = ni("getTypeInfo")

    override fun getIndexInfo(
        catalog: String?,
        schema: String?,
        table: String?,
        unique: Boolean,
        approximate: Boolean,
    ): ResultSet = ni("getIndexInfo")

    override fun getUDTs(
        catalog: String?,
        schemaPattern: String?,
        typeNamePattern: String?,
        types: IntArray?,
    ): ResultSet = ni("getUDTs")

    override fun getSuperTypes(
        catalog: String?,
        schemaPattern: String?,
        typeNamePattern: String?,
    ): ResultSet = ni("getSuperTypes")

    override fun getSuperTables(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
    ): ResultSet = ni("getSuperTables")

    override fun getAttributes(
        catalog: String?,
        schemaPattern: String?,
        typeNamePattern: String?,
        attributeNamePattern: String?,
    ): ResultSet = ni("getAttributes")

    override fun getClientInfoProperties(): ResultSet = ni("getClientInfoProperties")

    override fun getFunctions(
        catalog: String?,
        schemaPattern: String?,
        functionNamePattern: String?,
    ): ResultSet = ni("getFunctions")

    override fun getFunctionColumns(
        catalog: String?,
        schemaPattern: String?,
        functionNamePattern: String?,
        columnNamePattern: String?,
    ): ResultSet = ni("getFunctionColumns")

    override fun getPseudoColumns(
        catalog: String?,
        schemaPattern: String?,
        tableNamePattern: String?,
        columnNamePattern: String?,
    ): ResultSet = ni("getPseudoColumns")
}
