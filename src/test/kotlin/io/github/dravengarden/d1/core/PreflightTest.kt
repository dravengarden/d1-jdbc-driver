package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.transport.Transport
import io.github.dravengarden.d1.transport.TransportException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PreflightTest {
    @Test
    fun missingToolGivesAnActionableMessage() {
        val missing = object : Transport {
            override val description = "SSH host 'hawk'"

            override fun run(command: List<String>, workingDir: String?): String = "MISSING"
        }
        val e = assertFailsWith<IllegalStateException> { requireTool(missing, null, "sqlite3", "sqlite") }
        assertTrue("sqlite3" in e.message!!)
        assertTrue("SSH host 'hawk'" in e.message!!)
        assertTrue("sqlite=" in e.message!!, "should hint the override param")
    }

    @Test
    fun unreachableHostIsDistinguishedFromMissingTool() {
        val down = object : Transport {
            override val description = "SSH host 'hawk'"

            override fun run(command: List<String>, workingDir: String?): String =
                throw TransportException("ssh: connect to host hawk port 22: Connection refused")
        }
        val e = assertFailsWith<IllegalStateException> { requireTool(down, null, "curl", null) }
        assertTrue("unreachable" in e.message!! || "refused" in e.message!!)
        assertTrue("known_hosts" in e.message!!)
    }
}
