package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Access
import io.github.dravengarden.d1.core.Mode
import io.github.dravengarden.d1.core.TransportKind
import io.github.dravengarden.d1.core.Wrangler
import io.github.dravengarden.d1.transport.Transport
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D1JdbcContractTest {
    private object Rows : Transport {
        override fun run(command: List<String>, workingDir: String?): String =
            """[{"results":[{"id":1},{"id":2},{"id":3}],"success":true,"meta":{"changes":2147483648}}]"""
    }

    private fun connection(): D1Connection {
        val config =
            D1Config(
                transport = TransportKind.NORMAL,
                sshHost = null,
                workingDir = null,
                database = "db",
                mode = Mode.LOCAL,
                env = null,
                configPath = null,
                wranglerCommand = listOf("wrangler"),
                access = Access.WRITE,
            )
        return D1Connection(config, Wrangler(Rows, config))
    }

    @Test
    fun doesNotClaimOrSilentlyAcceptTransactions() {
        val connection = connection()
        assertTrue(connection.autoCommit)
        assertEquals(Connection.TRANSACTION_NONE, connection.transactionIsolation)
        assertFalse(connection.metaData.supportsTransactions())
        assertFailsWith<SQLFeatureNotSupportedException> { connection.autoCommit = false }
        assertFailsWith<SQLFeatureNotSupportedException> { connection.commit() }
        assertFailsWith<SQLFeatureNotSupportedException> { connection.transactionIsolation = Connection.TRANSACTION_SERIALIZABLE }
    }

    @Test
    fun closingConnectionInvalidatesExistingStatements() {
        val connection = connection()
        val statement = connection.createStatement()
        connection.close()
        assertTrue(statement.isClosed)
        assertFailsWith<SQLException> { statement.executeQuery("SELECT 1") }
    }

    @Test
    fun maxRowsAndCloseOnCompletionAreHonored() {
        val statement = connection().createStatement()
        statement.maxRows = 2
        statement.closeOnCompletion()
        val result = statement.executeQuery("SELECT id FROM t")
        assertTrue(result.next())
        assertTrue(result.next())
        assertFalse(result.next())
        result.close()
        assertTrue(statement.isClosed)
    }

    @Test
    fun largeUpdateCountsDoNotOverflow() {
        val statement = connection().createStatement()
        assertEquals(2_147_483_648L, statement.executeLargeUpdate("DELETE FROM t"))
        assertFailsWith<SQLException> { connection().createStatement().executeUpdate("DELETE FROM t") }
    }
}
