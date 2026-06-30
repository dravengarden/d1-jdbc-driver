package io.github.dravengarden.d1.jdbc

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlTest {
    @Test
    fun quotesAndEscapesStrings() {
        assertEquals("'abc'", quoteSqlString("abc"))
        assertEquals("'O''Brien'", quoteSqlString("O'Brien"))
    }

    @Test
    fun rendersLiterals() {
        assertEquals("NULL", sqlLiteralOf(null))
        assertEquals("1", sqlLiteralOf(true))
        assertEquals("0", sqlLiteralOf(false))
        assertEquals("42", sqlLiteralOf(42))
        assertEquals("3.5", sqlLiteralOf(3.5))
        assertEquals("'hi'", sqlLiteralOf("hi"))
    }

    @Test
    fun substitutesPositionalParameters() {
        val sql = "SELECT * FROM t WHERE a = ? AND b = ?"
        assertEquals(
            "SELECT * FROM t WHERE a = 1 AND b = 'x'",
            substituteParams(sql, mapOf(1 to "1", 2 to "'x'")),
        )
    }

    @Test
    fun leavesQuestionMarksInsideStringLiteralsAlone() {
        val sql = "SELECT '? literal' WHERE a = ?"
        assertEquals(
            "SELECT '? literal' WHERE a = 9",
            substituteParams(sql, mapOf(1 to "9")),
        )
    }

    @Test
    fun rejectsMissingAndExtraBindings() {
        assertFailsWith<SQLException> { substituteParams("a = ?", emptyMap()) }
        assertFailsWith<SQLException> { substituteParams("a = ?", mapOf(1 to "1", 2 to "2")) }
    }
}
