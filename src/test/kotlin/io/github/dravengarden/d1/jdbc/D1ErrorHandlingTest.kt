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
    fun authDeniedPragmaBecomesEmptyButUserQueryStillThrows() {
        // D1 denies PRAGMA on its internal _cf_* tables; that must not abort a sweep.
        val authDenied = object : Transport {
            override fun run(command: List<String>, workingDir: String?): String =
                throw RuntimeException("D1 API error: [7500] not authorized: SQLITE_AUTH")
        }
        val st = connect(authDenied).createStatement()
        val rs = st.executeQuery("""pragma "main".table_info("_cf_KV")""")
        assertFalse(rs.next()) // empty, not an error
        // a non-PRAGMA auth failure must still surface
        assertFailsWith<SQLException> { st.executeQuery("SELECT * FROM secret") }
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

    @Test
    fun d1BlockedInfoPragmasAreSynthesizedNotSentToWrangler() {
        // Throws on any wrangler call; a synthesized PRAGMA must not reach it.
        val neverCalled = object : Transport {
            override fun run(command: List<String>, workingDir: String?): String =
                throw RuntimeException("wrangler should not be invoked for: ${command.last()}")
        }
        val st = connect(neverCalled).createStatement()
        // All the no-arg informational PRAGMAs D1 rejects must be answered locally.
        for (p in listOf(
            "PRAGMA collation_list", "PRAGMA function_list", "PRAGMA module_list",
            "PRAGMA compile_options", "PRAGMA encoding", "PRAGMA pragma_list",
            "pragma database_list;", "PRAGMA main.database_list",
        )) {
            st.executeQuery(p).close() // must not throw / must not hit the transport
        }
        // Arg-taking PRAGMAs still go to wrangler (here, the throwing transport).
        assertFailsWith<SQLException> { st.executeQuery("PRAGMA table_info('accounts')") }
    }
}
