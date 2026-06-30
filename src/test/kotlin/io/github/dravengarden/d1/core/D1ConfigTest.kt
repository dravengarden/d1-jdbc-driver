package io.github.dravengarden.d1.core

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class D1ConfigTest {
    @Test
    fun parsesSshProxyUrl() {
        val url =
            "jdbc:d1:?transport=ssh&host=hawk&dir=/home/draven/kuaitu&db=kuaitu-local&mode=local&config=edge/worker/wrangler.jsonc"
        val c = D1Config.parse(url, Properties())
        assertEquals(TransportKind.SSH, c.transport)
        assertEquals("hawk", c.sshHost)
        assertEquals("/home/draven/kuaitu", c.workingDir)
        assertEquals("kuaitu-local", c.database)
        assertEquals(Mode.LOCAL, c.mode)
        assertEquals("edge/worker/wrangler.jsonc", c.configPath)
        assertEquals(listOf("wrangler"), c.wranglerCommand)
    }

    @Test
    fun defaultsAndRemoteAndCustomWrangler() {
        val url = "jdbc:d1:?db=kuaitu-production&mode=remote&env=production&wrangler=pnpm%20exec%20wrangler"
        val c = D1Config.parse(url, Properties())
        assertEquals(TransportKind.NORMAL, c.transport) // default
        assertEquals(Mode.REMOTE, c.mode)
        assertEquals("production", c.env)
        assertEquals(listOf("pnpm", "exec", "wrangler"), c.wranglerCommand)
    }

    @Test
    fun missingDbFails() {
        assertFailsWith<IllegalArgumentException> { D1Config.parse("jdbc:d1:?mode=local", Properties()) }
    }
}
