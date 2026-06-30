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
    fun wrapsThePipelineInBase64SoNestedQuotesSurviveShellLayers() {
        val cap = Capture("""{"success":true,"result":[{"results":[],"success":true,"meta":{}}]}""")
        engine(cap).query("SELECT name FROM t WHERE x IN ('a', 'b')")
        val cmd = cap.argv
        assertEquals(listOf("sh", "-c"), cmd.take(2))
        // The outer command is base64-only — no quotes for a second shell layer
        // (transport=ssh) to mangle.
        val wrapper = cmd[2]
        assertTrue(Regex("^printf %s [A-Za-z0-9+/=]+ \\| base64 -d \\| sh$").matches(wrapper), wrapper)
        assertFalse("'" in wrapper, "the wrapper must contain no single quotes")
        // The decoded script is the real pipeline: token from .env, piped header, curl.
        val b64 = wrapper.removePrefix("printf %s ").substringBefore(' ')
        val script = String(java.util.Base64.getDecoder().decode(b64))
        assertTrue("api.cloudflare.com/client/v4/accounts/acc123/d1/database/db-uuid/query" in script)
        // The SQL rides --data-raw; its single quotes are shq-escaped within the
        // pipeline, so check the un-quoted prefix is present.
        assertTrue("--data-raw" in script && "SELECT name FROM t WHERE x IN (" in script)
        assertTrue("CLOUDFLARE_API_TOKEN" in script && ".env" in script && "-H @-" in script)
    }

    @Test
    fun explicitTokenIsEmbeddedNotReadFromEnv() {
        val cap = Capture("""{"success":true,"result":[{"results":[],"success":true,"meta":{}}]}""")
        engine(cap, token = "cf-secret").query("SELECT 1")
        val script = String(java.util.Base64.getDecoder().decode(cap.argv[2].removePrefix("printf %s ").substringBefore(' ')))
        assertTrue("TOKEN='cf-secret'" in script)
        assertFalse("sed" in script, "explicit token must not read the env file")
    }
}
