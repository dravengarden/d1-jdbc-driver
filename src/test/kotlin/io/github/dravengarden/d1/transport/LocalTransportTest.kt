package io.github.dravengarden.d1.transport

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalTransportTest {
    @Test
    fun drainsNoisyStderrWithoutDeadlocking() {
        val transport = LocalTransport(timeoutSeconds = 5)
        val output = transport.run(listOf("sh", "-c", "head -c 1048576 /dev/zero | tr '\\0' x >&2; printf DONE"), null)
        assertTrue(output.endsWith("DONE"))
    }

    @Test
    fun enforcesTimeoutWhileTheProcessIsStillRunning() {
        val transport = LocalTransport(timeoutSeconds = 1)
        lateinit var failure: TransportException
        val elapsed =
            measureTimeMillis {
                failure = assertFailsWith { transport.run(listOf("sh", "-c", "sleep 30"), null) }
            }
        assertTrue(elapsed < 5_000, "timeout took ${elapsed}ms")
        assertTrue(failure.message!!.contains("timed out after 1s"))
    }

    @Test
    fun streamsStandardInputAndDoesNotEchoArgumentsInErrors() {
        val transport = LocalTransport(timeoutSeconds = 5)
        assertEquals("hello", transport.runWithInput(listOf("sh", "-c", "cat"), null, "hello"))
        val error = assertFailsWith<TransportException> { transport.run(listOf("sh", "-c", "exit 7", "sensitive-argument"), null) }
        assertFalse(error.message!!.contains("sensitive-argument"))
    }
}
