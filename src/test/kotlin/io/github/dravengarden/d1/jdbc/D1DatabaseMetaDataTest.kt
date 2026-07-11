package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the SQL-backed [D1DatabaseMetaData] catalog methods through a fake
 * transport that returns canned `wrangler --json` payloads keyed off the SQL it
 * is handed, so the reshaping logic is exercised without a real D1.
 */
class D1DatabaseMetaDataTest {
    /** Matches on the `--command` SQL (the last argv element) and replays JSON. */
    private object FakeTransport : Transport {
        override fun run(command: List<String>, workingDir: String?): String {
            val sql = command.last()
            return when {
                "type IN ('table','view')" in sql && "name LIKE 'accounts'" in sql ->
                    """[{"results":[{"name":"accounts","type":"table"}],"success":true}]"""
                "type IN ('table','view')" in sql && "name LIKE 'v_active'" in sql ->
                    """[{"results":[{"name":"v_active","type":"view"}],"success":true}]"""
                "type IN ('table','view')" in sql ->
                    """[{"results":[{"name":"accounts","type":"table"},
                                    {"name":"v_active","type":"view"}],"success":true}]"""
                "PRAGMA table_xinfo('accounts')" in sql ->
                    """[{"results":[
                          {"cid":0,"name":"id","type":"TEXT","notnull":0,"dflt_value":null,"pk":1,"hidden":0},
                          {"cid":1,"name":"email","type":"TEXT","notnull":0,"dflt_value":null,"pk":0,"hidden":0}
                        ],"success":true}]"""
                "PRAGMA table_xinfo('v_active')" in sql ->
                    """[{"results":[{"cid":0,"name":"id","type":"TEXT","notnull":0,"pk":0,"hidden":0}],"success":true}]"""
                "PRAGMA foreign_key_list('accounts')" in sql ->
                    """[{"results":[{"id":0,"seq":0,"table":"teams","from":"team_id","to":"id","on_update":"NO ACTION","on_delete":"CASCADE"}],"success":true}]"""
                else -> """[{"results":[],"success":true}]"""
            }
        }
    }

    private fun metaData(): D1DatabaseMetaData {
        val config =
            D1Config(
                transport = TransportKind.NORMAL,
                sshHost = null,
                workingDir = null,
                database = "kuaitu-local",
                mode = Mode.LOCAL,
                env = null,
                configPath = null,
                wranglerCommand = listOf("wrangler"),
            )
        val connection = D1Connection(config, Wrangler(FakeTransport, config))
        return D1DatabaseMetaData(connection)
    }

    @Test
    fun listsTablesAndViews() {
        val rs = metaData().getTables(null, null, null, null)
        val seen = mutableMapOf<String, String>()
        while (rs.next()) {
            seen[rs.getString("TABLE_NAME")!!] = rs.getString("TABLE_TYPE")!!
        }
        assertEquals(mapOf("accounts" to "TABLE", "v_active" to "VIEW"), seen)
    }

    @Test
    fun filtersTablesByType() {
        val rs = metaData().getTables(null, null, null, arrayOf("TABLE"))
        val names = buildList { while (rs.next()) add(rs.getString("TABLE_NAME")) }
        assertEquals(listOf("accounts"), names)
    }

    @Test
    fun listsColumnsWithTypesAndNullability() {
        val rs = metaData().getColumns(null, null, "accounts", null)
        assertTrue(rs.next())
        assertEquals("id", rs.getString("COLUMN_NAME"))
        assertEquals(Types.VARCHAR, rs.getInt("DATA_TYPE"))
        assertEquals(1, rs.getInt("ORDINAL_POSITION"))
        assertEquals("NO", rs.getString("IS_NULLABLE"))
        assertTrue(rs.next())
        assertEquals("email", rs.getString("COLUMN_NAME"))
        assertEquals("YES", rs.getString("IS_NULLABLE"))
    }

    @Test
    fun reportsSingleMainSchemaAndTagsTables() {
        val md = metaData()
        val schemas = md.getSchemas()
        assertTrue(schemas.next())
        assertEquals("main", schemas.getString("TABLE_SCHEM"))
        assertFalse(schemas.next())
        // tables are stamped with that schema so a client can introspect under it
        val tables = md.getTables(null, null, null, null)
        assertTrue(tables.next())
        assertEquals("main", tables.getString("TABLE_SCHEM"))
    }

    @Test
    fun reportsPrimaryKey() {
        val rs = metaData().getPrimaryKeys(null, null, "accounts")
        assertTrue(rs.next())
        assertEquals("id", rs.getString("COLUMN_NAME"))
        assertEquals(1, rs.getInt("KEY_SEQ"))
    }

    @Test
    fun listsViewColumnsAndForeignKeys() {
        val columns = metaData().getColumns(null, "main", "v_active", null)
        assertTrue(columns.next())
        assertEquals("id", columns.getString("COLUMN_NAME"))

        val keys = metaData().getImportedKeys(null, "main", "accounts")
        assertTrue(keys.next())
        assertEquals("teams", keys.getString("PKTABLE_NAME"))
        assertEquals("team_id", keys.getString("FKCOLUMN_NAME"))
        assertEquals(java.sql.DatabaseMetaData.importedKeyCascade, keys.getInt("DELETE_RULE"))
    }

    @Test
    fun rejectsNonMatchingCatalogAndSchemaPatterns() {
        assertFalse(metaData().getTables("other", null, null, null).next())
        assertFalse(metaData().getColumns(null, "other", "%", null).next())
    }
}
