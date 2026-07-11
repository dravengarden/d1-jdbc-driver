package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.transport.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpEngineTest {
    /** Captures the argv the engine hands the transport, and replays a fixed body. */
    private class Capture(val reply: String) : Transport {
        var argv: List<String> = emptyList()
        var stdin: String? = null

        override fun run(command: List<String>, workingDir: String?): String {
            argv = command
            return reply
        }

        override fun runWithInput(command: List<String>, workingDir: String?, standardInput: String): String {
            argv = command
            stdin = standardInput
            return reply
        }
    }

    private fun engine(transport: Transport, processTokenAvailable: Boolean = false) =
        HttpEngine(
            transport,
            workingDir = "/srv/project",
            accountId = "acc123",
            databaseId = "db-uuid",
            processTokenAvailable = processTokenAvailable,
            envFile = ".env",
            tokenVar = "CLOUDFLARE_API_TOKEN",
        )

    @Test
    fun parsesD1ApiResponseIncludingChangeCount() {
        val ok =
            """{"success":true,"result":[{"results":[{"n":30}],"success":true,
               "meta":{"changes":2,"last_row_id":7}}],"errors":[]}"""
        val r = HttpEngine.parse(ok)
        assertEquals(listOf("n"), r.columns)
        assertEquals(1, r.rows.size)
        assertEquals(2L, r.changes)
        assertEquals(7L, r.lastRowId)
    }

    @Test
    fun multiStatementReturnsTheLastResult() {
        // A client may send `<setup>; <query>`; the final statement's rows win.
        val multi =
            """{"success":true,"result":[
                 {"results":[{"a":1}],"success":true,"meta":{}},
                 {"results":[{"name":"accounts"},{"name":"sessions"}],"success":true,"meta":{}}
               ],"errors":[]}"""
        val r = HttpEngine.parse(multi)
        assertEquals(listOf("name"), r.columns)
        assertEquals(2, r.rows.size)
    }

    @Test
    fun surfacesApiErrors() {
        val err = """{"success":false,"result":[],"errors":[{"code":7500,"message":"no such table"}]}"""
        val e = assertFailsWith<IllegalStateException> { HttpEngine.parse(err) }
        assertTrue(e.message!!.contains("7500"))
        assertTrue(e.message!!.contains("no such table"))
    }

    @Test
    fun rejectsUnsuccessfulStatementInsideSuccessfulEnvelope() {
        val body = """{"success":true,"result":[{"results":[],"success":false,"error":"SQLITE_AUTH"}]}"""
        val error = assertFailsWith<IllegalArgumentException> { HttpEngine.parse(body) }
        assertTrue(error.message!!.contains("SQLITE_AUTH"))
    }

    @Test
    fun sendsJsonBodyOverStdinAndKeepsSqlOutOfArgv() {
        val cap = Capture("""{"success":true,"result":[{"results":[],"success":true,"meta":{}}]}""")
        engine(cap).query("SELECT name FROM t WHERE x IN ('a', 'b')")
        val cmd = cap.argv
        assertEquals(listOf("sh", "-c"), cmd.take(2))
        val script = cmd[2]
        assertTrue("api.cloudflare.com/client/v4/accounts/acc123/d1/database/db-uuid/query" in script)
        assertFalse("SELECT name FROM t" in script)
        assertTrue("--data-binary @-" in script && "CLOUDFLARE_API_TOKEN" in script && ".env" in script)
        assertTrue("mktemp" in script && "@$" in script)
        assertTrue(cap.stdin!!.contains("SELECT name FROM t WHERE x IN ('a', 'b')"))
    }

    @Test
    fun jdbcPasswordIsReferencedByEnvNameNotEmbeddedInArgv() {
        val cap = Capture("""{"success":true,"result":[{"results":[],"success":true,"meta":{}}]}""")
        engine(cap, processTokenAvailable = true).query("SELECT 1")
        val script = cap.argv[2]
        assertTrue("D1_JDBC_API_TOKEN" in script)
        assertFalse("cf-secret" in script)
        assertFalse("sed" in script, "explicit token must not read the env file")
    }
}
