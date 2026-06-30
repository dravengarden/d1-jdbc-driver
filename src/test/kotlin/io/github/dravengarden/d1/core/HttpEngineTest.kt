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

        override fun run(command: List<String>, workingDir: String?): String {
            argv = command
            return reply
        }
    }

    private fun engine(transport: Transport, token: String? = null) =
        HttpEngine(
            transport,
            workingDir = "/srv/project",
            accountId = "acc123",
            databaseId = "db-uuid",
            explicitToken = token,
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
    fun buildsCurlPipelineThatReadsTokenFromEnvFileNotArgv() {
        val cap = Capture("""{"success":true,"result":[{"results":[],"success":true,"meta":{}}]}""")
        engine(cap).query("SELECT 1")
        val cmd = cap.argv
        assertEquals(listOf("sh", "-c"), cmd.take(2))
        val pipeline = cmd[2]
        assertTrue("api.cloudflare.com/client/v4/accounts/acc123/d1/database/db-uuid/query" in pipeline)
        assertTrue("--data-raw" in pipeline && "SELECT 1" in pipeline)
        // token read fresh from .env, piped via -H @-, never named on the command line
        assertTrue("CLOUDFLARE_API_TOKEN" in pipeline && ".env" in pipeline)
        assertTrue("-H @-" in pipeline)
    }

    @Test
    fun explicitTokenIsEmbeddedNotReadFromEnv() {
        val cap = Capture("""{"success":true,"result":[{"results":[],"success":true,"meta":{}}]}""")
        engine(cap, token = "cf-secret").query("SELECT 1")
        val pipeline = cap.argv[2]
        assertTrue("TOKEN='cf-secret'" in pipeline)
        assertFalse("sed" in pipeline, "explicit token must not read the env file")
    }
}
