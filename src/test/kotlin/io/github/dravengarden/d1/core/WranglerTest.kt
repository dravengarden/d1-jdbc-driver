package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.transport.Transport
import kotlin.test.Test
import kotlin.test.assertEquals

private object NoopTransport : Transport {
    override fun run(command: List<String>, workingDir: String?): String = error("unused")
}

class WranglerTest {
    private fun wrangler(
        mode: Mode = Mode.LOCAL,
        env: String? = null,
        config: String? = "edge/worker/wrangler.jsonc",
    ) = Wrangler(
        NoopTransport,
        D1Config(
            transport = TransportKind.NORMAL,
            sshHost = null,
            workingDir = null,
            database = "kuaitu-local",
            mode = mode,
            env = env,
            configPath = config,
            wranglerCommand = listOf("wrangler"),
        ),
    )

    @Test
    fun buildsLocalArgs() {
        assertEquals(
            listOf(
                "wrangler", "d1", "execute", "kuaitu-local",
                "--config", "edge/worker/wrangler.jsonc",
                "--local", "--json", "--command", "SELECT 1",
            ),
            wrangler().buildArgs("SELECT 1"),
        )
    }

    @Test
    fun buildsRemoteArgsWithEnv() {
        assertEquals(
            listOf(
                "wrangler", "d1", "execute", "kuaitu-local",
                "--config", "edge/worker/wrangler.jsonc",
                "--remote", "--env", "production", "--json", "--command", "SELECT 2",
            ),
            wrangler(mode = Mode.REMOTE, env = "production").buildArgs("SELECT 2"),
        )
    }

    @Test
    fun parsesWranglerJsonWithBanner() {
        // Real shape: a banner line, then the JSON array wrangler prints.
        val stdout =
            """
            kuaitu: loaded .env
            [ { "results": [ {"id":"acc_1","email":"a@b.co","email_verified":1},
                             {"id":"acc_2","email":"c@d.co","email_verified":0} ],
                "success": true, "meta": {"duration": 0} } ]
            """.trimIndent()
        val result = Wrangler.parse(stdout)
        assertEquals(listOf("id", "email", "email_verified"), result.columns)
        assertEquals(2, result.rows.size)
        assertEquals("\"acc_1\"", result.rows[0][0].toString())
    }

    @Test
    fun parsesEmptyResult() {
        val result = Wrangler.parse("""[ { "results": [], "success": true } ]""")
        assertEquals(0, result.rows.size)
        assertEquals(emptyList(), result.columns)
    }

    @Test
    fun parsesWriteMeta() {
        val result =
            Wrangler.parse("""[ { "results": [], "success": true, "meta": {"changes": 5, "last_row_id": 99} } ]""")
        assertEquals(5L, result.changes)
        assertEquals(99L, result.lastRowId)
    }
}
