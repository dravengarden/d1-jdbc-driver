package io.github.dravengarden.d1.jdbc

import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class D1DriverTest {
    private val driver = D1Driver()

    @Test
    fun acceptsOnlyD1Urls() {
        assertTrue(driver.acceptsURL("jdbc:d1:?db=x"))
        assertFalse(driver.acceptsURL("jdbc:sqlite:/tmp/x.db"))
    }

    @Test
    fun rejectsForeignUrlByReturningNull() {
        assertNull(driver.connect("jdbc:postgresql://localhost/x", Properties()))
    }

    @Test
    fun exposesRequiredDbProperty() {
        val db = driver.getPropertyInfo("jdbc:d1:?db=x", Properties()).single { it.name == "db" }
        assertTrue(db.required)
    }

    @Test
    fun connectSurfacesBackendFailureAsSqlException() {
        // `transport=normal` with a wrangler command that always fails makes the
        // SELECT 1 connectivity probe throw — connect() must wrap it, not leak it.
        val url = "jdbc:d1:?db=kuaitu-local&transport=normal&wrangler=${"/bin/false"}"
        val e = assertFailsWith<SQLException> { driver.connect(url, Properties()) }
        assertTrue(e.message!!.contains("kuaitu-local"), "message should name the db: ${e.message}")
    }

    @Test
    fun probeFalseSkipsConnectivityCheck() {
        // Bogus wrangler that would fail a SELECT 1 — but probe=false means connect()
        // must not run the probe, so opening the connection succeeds anyway.
        val url = "jdbc:d1:?db=x&transport=normal&wrangler=${"/bin/false"}&probe=false"
        val c = driver.connect(url, Properties())
        assertNotNull(c)
        c.close()
    }

    @Test
    fun registeredWithDriverManager() {
        // The static initializer registers an instance; DriverManager should find it.
        assertTrue(DriverManager.getDriver("jdbc:d1:?db=x") is D1Driver)
    }
}
