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

    @Test
    fun defaultsForOptionalParams() {
        val c = D1Config.parse("jdbc:d1:?db=x", Properties())
        assertEquals(listOf("ssh"), c.sshCommand)
        assertEquals(emptyList(), c.sshOptions)
        assertEquals(120L, c.timeoutSeconds)
        assertEquals(true, c.probe)
        assertEquals(false, c.readOnly)
        assertEquals(true, c.cacheIntrospection)
    }

    @Test
    fun parsesAllTunableParams() {
        val url =
            "jdbc:d1:?db=x&ssh=ssh%20-F%20/tmp/cfg&ssh-opts=-p%202222%20-o%20ConnectTimeout=5" +
                "&timeout=30&probe=false&readonly=true&cache=off"
        val c = D1Config.parse(url, Properties())
        assertEquals(listOf("ssh", "-F", "/tmp/cfg"), c.sshCommand)
        assertEquals(listOf("-p", "2222", "-o", "ConnectTimeout=5"), c.sshOptions)
        assertEquals(30L, c.timeoutSeconds)
        assertEquals(false, c.probe)
        assertEquals(true, c.readOnly)
        assertEquals(false, c.cacheIntrospection)
    }

    @Test
    fun rejectsBadBooleanAndTimeout() {
        assertFailsWith<IllegalStateException> { D1Config.parse("jdbc:d1:?db=x&probe=maybe", Properties()) }
        assertFailsWith<IllegalStateException> { D1Config.parse("jdbc:d1:?db=x&timeout=0", Properties()) }
    }

    @Test
    fun propertiesProvideValuesToo() {
        val props = Properties().apply { setProperty("readonly", "true") }
        assertEquals(true, D1Config.parse("jdbc:d1:?db=x", props).readOnly)
    }

    @Test
    fun apiTokenComesFromPasswordPropertyOnly() {
        val props = Properties().apply { setProperty("password", "cf-token-123") }
        assertEquals("cf-token-123", D1Config.parse("jdbc:d1:?db=x&mode=remote", props).apiToken)
        // A token in the URL is NOT picked up — secrets never travel in the URL.
        assertEquals(null, D1Config.parse("jdbc:d1:?db=x&password=in-url", Properties()).apiToken)
    }
}
