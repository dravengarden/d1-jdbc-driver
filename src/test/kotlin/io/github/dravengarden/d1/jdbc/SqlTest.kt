package io.github.dravengarden.d1.jdbc

import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val sql = "SELECT '? literal', \"? identifier\", `? other`, [? bracket] -- ? comment\nWHERE a = ? /* ? block */"
        assertEquals(
            "SELECT '? literal', \"? identifier\", `? other`, [? bracket] -- ? comment\nWHERE a = 9 /* ? block */",
            substituteParams(sql, mapOf(1 to "9")),
        )
    }

    @Test
    fun rejectsMissingAndExtraBindings() {
        assertFailsWith<SQLException> { substituteParams("a = ?", emptyMap()) }
        assertFailsWith<SQLException> { substituteParams("a = ?", mapOf(1 to "1", 2 to "2")) }
    }

    @Test
    fun classifiesRowReturningStatements() {
        assertTrue(looksLikeQuery("SELECT 1"))
        assertTrue(looksLikeQuery("  select * from t"))
        assertTrue(looksLikeQuery("PRAGMA table_info('t')"))
        assertTrue(looksLikeQuery("WITH x AS (SELECT 1) SELECT * FROM x"))
        assertFalse(looksLikeQuery("INSERT INTO t VALUES (1)"))
        assertFalse(looksLikeQuery("UPDATE t SET a = 1"))
        assertFalse(looksLikeQuery("DELETE FROM t"))
        assertFalse(looksLikeQuery("CREATE TABLE t (a)"))
        assertTrue(looksLikeQuery("INSERT INTO t VALUES (1) RETURNING id"))
    }

    @Test
    fun classifiesEveryStatementAndIgnoresKeywordsInCommentsAndStrings() {
        assertEquals(StatementClass.DML, classifyStatement("SELECT 1; DELETE FROM t"))
        assertEquals(StatementClass.DDL, classifyStatement("SELECT 1; DROP TABLE t"))
        assertEquals(StatementClass.READ, classifyStatement("-- DELETE ignored\nSELECT 'DROP TABLE t'"))
        assertEquals(StatementClass.READ, classifyStatement("WITH x AS (SELECT 'DELETE') SELECT * FROM x"))
        assertEquals(StatementClass.DML, classifyStatement("WITH x AS (SELECT 1) DELETE FROM t RETURNING id"))
    }

    @Test
    fun onlyAllowsKnownReadOnlyPragmasAtReadLevel() {
        assertEquals(StatementClass.READ, classifyStatement("PRAGMA main.table_xinfo('t')"))
        assertEquals(StatementClass.READ, classifyStatement("PRAGMA foreign_key_list('t')"))
        assertEquals(StatementClass.DML, classifyStatement("PRAGMA user_version = 2"))
        assertEquals(StatementClass.DML, classifyStatement("PRAGMA writable_schema"))
    }

    @Test
    fun rendersBlobTemporalAndRejectsNonFiniteNumbers() {
        assertEquals("X'00ff'", sqlLiteralOf(byteArrayOf(0, -1)))
        assertEquals("'2026-07-11'", sqlLiteralOf(java.time.LocalDate.of(2026, 7, 11)))
        assertFailsWith<SQLException> { sqlLiteralOf(Double.NaN) }
        assertFailsWith<SQLException> { sqlLiteralOf(Double.POSITIVE_INFINITY) }
    }
}
