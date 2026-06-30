package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
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
                "type IN ('table','view')" in sql ->
                    """[{"results":[{"name":"accounts","type":"table"},
                                    {"name":"v_active","type":"view"}],"success":true}]"""
                "type='table'" in sql ->
                    """[{"results":[{"name":"accounts"}],"success":true}]"""
                "PRAGMA table_info('accounts')" in sql ->
                    """[{"results":[
                          {"cid":0,"name":"id","type":"TEXT","notnull":1,"dflt_value":null,"pk":1},
                          {"cid":1,"name":"email","type":"TEXT","notnull":0,"dflt_value":null,"pk":0}
                        ],"success":true}]"""
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
    fun reportsPrimaryKey() {
        val rs = metaData().getPrimaryKeys(null, null, "accounts")
        assertTrue(rs.next())
        assertEquals("id", rs.getString("COLUMN_NAME"))
        assertEquals(1, rs.getInt("KEY_SEQ"))
    }
}
