package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.model.QueryResult
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class D1ResultSetTest {
    private fun sample(): D1ResultSet {
        val rows =
            listOf(
                listOf(JsonPrimitive(7), JsonPrimitive("alice"), JsonPrimitive(2.5), JsonNull),
                listOf(JsonPrimitive(8), JsonPrimitive("bob"), JsonPrimitive(-1.0), JsonNull),
            )
        return D1ResultSet(QueryResult(listOf("id", "name", "ratio", "note"), rows), null)
    }

    @Test
    fun iteratesRowsAndCoercesCells() {
        val rs = sample()
        assertTrue(rs.next())
        assertEquals(7, rs.getInt("id"))
        assertEquals(7L, rs.getLong(1))
        assertEquals("alice", rs.getString("name"))
        assertEquals(2.5, rs.getDouble("ratio"), 0.0)
        assertTrue(rs.next())
        assertEquals("bob", rs.getString(2))
        assertFalse(rs.next())
    }

    @Test
    fun tracksSqlNull() {
        val rs = sample()
        rs.next()
        assertEquals(null, rs.getString("note"))
        assertTrue(rs.wasNull())
        rs.getString("name")
        assertFalse(rs.wasNull())
    }

    @Test
    fun findsColumnsCaseInsensitively() {
        val rs = sample()
        assertEquals(2, rs.findColumn("NAME"))
    }

    @Test
    fun infersColumnTypesFromCells() {
        val md = sample().metaData
        assertEquals(4, md.columnCount)
        assertEquals(Types.BIGINT, md.getColumnType(1))
        assertEquals(Types.VARCHAR, md.getColumnType(2))
        assertEquals(Types.DOUBLE, md.getColumnType(3))
        // All-null column defaults to TEXT/VARCHAR.
        assertEquals(Types.VARCHAR, md.getColumnType(4))
        assertEquals("id", md.getColumnName(1))
    }
}
