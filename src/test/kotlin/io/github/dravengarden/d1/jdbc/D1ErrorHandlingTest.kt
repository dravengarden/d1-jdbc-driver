package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import io.github.dravengarden.d1.transport.TransportException
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class D1ErrorHandlingTest {
    private fun connect(transport: Transport): D1Connection {
        val config =
            D1Config(
                transport = TransportKind.NORMAL,
                sshHost = null,
                workingDir = null,
                database = "db",
                mode = Mode.LOCAL,
                env = null,
                configPath = null,
                wranglerCommand = listOf("wrangler"),
            )
        return D1Connection(config, Wrangler(transport, config))
    }

    @Test
    fun wranglerFailureSurfacesAsPlainSqlExceptionNotACustomClass() {
        // A custom exception class would break DataGrip's out-of-process (RMI)
        // deserialization, so the driver must only ever throw java.sql.SQLException.
        val boom = object : Transport {
            override fun run(command: List<String>, workingDir: String?): String =
                throw TransportException("command failed (exit 1): boom")
        }
        val st = connect(boom).createStatement()
        // assertFailsWith<SQLException> already asserts the thrown type is the
        // standard SQLException (TransportException is not a subtype, so a raw one
        // would fail this) — the driver translated it.
        val e = assertFailsWith<SQLException> { st.executeQuery("SELECT 1") }
        assertNull(e.cause, "no custom cause may travel to the client")
        assertTrue(e.message!!.contains("boom"))
    }

    @Test
    fun pragmaDatabaseListIsSynthesizedWithoutCallingWrangler() {
        // D1 blocks PRAGMA database_list; the driver answers it locally with "main".
        val neverCalled = object : Transport {
            override fun run(command: List<String>, workingDir: String?): String =
                throw AssertionError("wrangler should not be invoked for PRAGMA database_list")
        }
        val rs = connect(neverCalled).createStatement().executeQuery("PRAGMA database_list")
        assertTrue(rs.next())
        assertEquals("main", rs.getString("name"))
        assertFalse(rs.next())
    }
}
