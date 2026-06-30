package io.github.dravengarden.d1.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SqliteEngineTest {
    @Test
    fun parsesSqliteJsonRows() {
        val result = SqliteEngine.parse("""[ {"id":"acc_1","n":2}, {"id":"acc_2","n":5} ]""")
        assertEquals(listOf("id", "n"), result.columns)
        assertEquals(2, result.rows.size)
        assertEquals("\"acc_1\"", result.rows[0][0].toString())
    }

    @Test
    fun parsesEmptyOutputAsNoRows() {
        // sqlite3 -json prints nothing when a query returns no rows.
        assertEquals(0, SqliteEngine.parse("").rows.size)
        assertEquals(emptyList(), SqliteEngine.parse("").columns)
    }
}
