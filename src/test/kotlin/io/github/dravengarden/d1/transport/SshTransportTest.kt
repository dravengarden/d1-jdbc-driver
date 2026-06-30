package io.github.dravengarden.d1.transport

import kotlin.test.Test
import kotlin.test.assertEquals

class SshTransportTest {
    private val ssh = SshTransport(host = "hawk")

    @Test
    fun wrapsArgvInACdAndQuotesEachToken() {
        assertEquals(
            "cd '/srv/kuaitu' && 'wrangler' 'd1' 'execute' 'kuaitu-local'",
            ssh.remoteCommand(listOf("wrangler", "d1", "execute", "kuaitu-local"), "/srv/kuaitu"),
        )
    }

    @Test
    fun omitsCdWhenNoWorkingDir() {
        assertEquals(
            "'wrangler' 'd1' 'execute'",
            ssh.remoteCommand(listOf("wrangler", "d1", "execute"), null),
        )
    }

    @Test
    fun escapesSingleQuotesInArguments() {
        assertEquals(
            "'--command' 'SELECT '\\''x'\\'''",
            ssh.remoteCommand(listOf("--command", "SELECT 'x'"), null),
        )
    }
}
