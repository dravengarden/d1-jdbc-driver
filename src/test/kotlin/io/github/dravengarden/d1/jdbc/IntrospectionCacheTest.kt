package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import kotlin.test.Test
import kotlin.test.assertEquals

class IntrospectionCacheTest {
    /** Counts every wrangler invocation so cache hits/misses are observable. */
    private class CountingTransport : Transport {
        var count = 0

        override fun run(command: List<String>, workingDir: String?): String {
            count++
            val sql = command.last()
            return if ("sqlite_master" in sql) {
                """[{"results":[{"name":"accounts","type":"table"}],"success":true}]"""
            } else {
                """[{"results":[],"success":true,"meta":{"changes":1}}]"""
            }
        }
    }

    private val transport = CountingTransport()

    private val connection: D1Connection =
        D1Config(
            transport = TransportKind.NORMAL,
            sshHost = null,
            workingDir = null,
            database = "kuaitu-local",
            mode = Mode.LOCAL,
            env = null,
            configPath = null,
            wranglerCommand = listOf("wrangler"),
        ).let { D1Connection(it, Wrangler(transport, it)) }

    @Test
    fun repeatedIntrospectionHitsTheCache() {
        val md = connection.metaData
        md.getTables(null, null, null, null)
        md.getTables(null, null, null, null)
        assertEquals(1, transport.count, "second getTables should be served from cache")
    }

    @Test
    fun writeInvalidatesTheCache() {
        connection.metaData.getTables(null, null, null, null)
        assertEquals(1, transport.count)
        // A write clears the schema cache (DDL can change the schema)...
        connection.createStatement().executeUpdate("UPDATE accounts SET email_verified = 1")
        assertEquals(2, transport.count)
        // ...so the next introspection re-queries instead of returning stale rows.
        connection.metaData.getTables(null, null, null, null)
        assertEquals(3, transport.count)
    }
}
