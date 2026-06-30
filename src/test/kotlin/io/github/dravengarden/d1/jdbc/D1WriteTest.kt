package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.Access
import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class D1WriteTest {
    /** Replays a canned write `meta` (changes/last_row_id) for any non-query SQL. */
    private class WriteTransport : Transport {
        var calls = 0

        override fun run(command: List<String>, workingDir: String?): String {
            calls++
            return """[{"results":[],"success":true,"meta":{"changes":3,"last_row_id":42}}]"""
        }
    }

    private fun connect(transport: Transport, access: Access = Access.WRITE): D1Connection {
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
                access = access,
            )
        return D1Connection(config, Wrangler(transport, config))
    }

    @Test
    fun executeUpdateReturnsAffectedRowCount() {
        val st = connect(WriteTransport()).createStatement()
        assertEquals(3, st.executeUpdate("UPDATE accounts SET email_verified = 1"))
    }

    @Test
    fun executeRoutesWritesToUpdateCount() {
        val st = connect(WriteTransport()).createStatement()
        assertFalse(st.execute("INSERT INTO accounts(id) VALUES ('x')"))
        assertEquals(3, st.updateCount)
        assertNull(st.resultSet)
    }

    @Test
    fun preparedExecuteUpdateReturnsAffectedRowCount() {
        val ps = connect(WriteTransport()).prepareStatement("DELETE FROM accounts WHERE id = ?")
        ps.setString(1, "x")
        assertEquals(3, ps.executeUpdate())
    }

    @Test
    fun readAccessRejectsWritesBeforeTouchingTheEngine() {
        val transport = WriteTransport()
        val st = connect(transport, access = Access.READ).createStatement()
        assertFailsWith<java.sql.SQLException> { st.executeUpdate("DELETE FROM accounts") }
        assertFailsWith<java.sql.SQLException> { st.execute("INSERT INTO accounts(id) VALUES ('x')") }
        assertEquals(0, transport.calls, "no engine call should happen for a rejected write")
    }

    @Test
    fun writeAccessStillRejectsDdl() {
        val transport = WriteTransport()
        val st = connect(transport, access = Access.WRITE).createStatement()
        // DML is allowed at write level...
        st.executeUpdate("UPDATE accounts SET email_verified = 1")
        assertEquals(1, transport.calls)
        // ...but a schema change needs access=ddl.
        val e = assertFailsWith<java.sql.SQLException> { st.executeUpdate("DROP TABLE accounts") }
        assertTrue(e.message!!.contains("access=ddl"))
        assertEquals(1, transport.calls, "rejected DDL must not reach the engine")
    }
}
