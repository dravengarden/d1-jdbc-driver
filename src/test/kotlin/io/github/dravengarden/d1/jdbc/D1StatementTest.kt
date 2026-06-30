package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D1StatementTest {
    /** Records the SQL it last saw so parameter substitution can be asserted. */
    private class RecordingTransport : Transport {
        var lastSql: String? = null

        override fun run(command: List<String>, workingDir: String?): String {
            lastSql = command.last()
            return """[{"results":[{"id":7,"name":"alice"}],"success":true}]"""
        }
    }

    private fun connect(transport: Transport): D1Connection {
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
        return D1Connection(config, Wrangler(transport, config))
    }

    @Test
    fun statementExecutesQueryAndReadsRows() {
        val rs = connect(RecordingTransport()).createStatement().executeQuery("SELECT id, name FROM t")
        assertTrue(rs.next())
        assertEquals(7, rs.getInt("id"))
        assertEquals("alice", rs.getString("name"))
        assertFalse(rs.next())
    }

    @Test
    fun preparedStatementSubstitutesBoundParameters() {
        val transport = RecordingTransport()
        val ps = connect(transport).prepareStatement("SELECT id FROM t WHERE id = ? AND name = ?")
        ps.setInt(1, 7)
        ps.setString(2, "alice")
        ps.executeQuery()
        assertEquals("SELECT id FROM t WHERE id = 7 AND name = 'alice'", transport.lastSql)
    }
}
