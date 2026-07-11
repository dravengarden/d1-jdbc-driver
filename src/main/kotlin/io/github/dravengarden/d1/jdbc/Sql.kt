package io.github.dravengarden.d1.jdbc

import java.sql.SQLException

/** SQL helpers for statement routing, access checks, and client-side binds. */

/** Single-quote and escape a string for inlining as a SQLite literal. */
internal fun quoteSqlString(value: String): String = "'" + value.replace("'", "''") + "'"

/** What a statement does, for the access guardrail. Increasing privilege. */
internal enum class StatementClass { READ, DML, DDL }

private enum class ScanState { CODE, SINGLE_QUOTE, DOUBLE_QUOTE, BACKTICK, BRACKET, LINE_COMMENT, BLOCK_COMMENT }

private val DML_KEYWORDS = setOf("INSERT", "UPDATE", "DELETE", "REPLACE", "UPSERT")
private val DDL_KEYWORDS =
    setOf("CREATE", "DROP", "ALTER", "REINDEX", "VACUUM", "ANALYZE", "TRUNCATE", "ATTACH", "DETACH")
private val READ_ONLY_PRAGMAS =
    setOf(
        "COLLATION_LIST",
        "COMPILE_OPTIONS",
        "DATABASE_LIST",
        "ENCODING",
        "FOREIGN_KEY_LIST",
        "FUNCTION_LIST",
        "INDEX_INFO",
        "INDEX_LIST",
        "INDEX_XINFO",
        "MODULE_LIST",
        "PRAGMA_LIST",
        "TABLE_INFO",
        "TABLE_LIST",
        "TABLE_XINFO",
    )
private val SQL_NUMBER = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")

/** Whether the final statement returns rows (the engines expose its result). */
internal fun looksLikeQuery(sql: String): Boolean {
    val statement = splitStatements(sql).lastOrNull() ?: return false
    val words = codeWords(statement)
    return classifySingle(statement) == StatementClass.READ || "RETURNING" in words
}

/**
 * Classify every statement and return the highest required access. This closes
 * the `SELECT 1; DELETE …` hole that a leading-keyword-only check creates.
 */
internal fun classifyStatement(sql: String): StatementClass {
    val statements = splitStatements(sql)
    if (statements.isEmpty()) return StatementClass.DML
    return statements.map(::classifySingle).maxBy { it.ordinal }
}

private fun classifySingle(sql: String): StatementClass {
    val code = codeOnly(sql)
    val words = WORD.findAll(code).map { it.value.uppercase() }.toList()
    return when (words.firstOrNull()) {
        "SELECT", "EXPLAIN", "VALUES" -> StatementClass.READ
        "PRAGMA" -> classifyPragma(code)
        in DDL_KEYWORDS -> StatementClass.DDL
        in DML_KEYWORDS -> StatementClass.DML
        "WITH" ->
            when {
                words.any { it in DDL_KEYWORDS } -> StatementClass.DDL
                words.any { it in DML_KEYWORDS } -> StatementClass.DML
                else -> StatementClass.READ
            }
        else -> StatementClass.DML
    }
}

private fun classifyPragma(code: String): StatementClass {
    val body = code.substringAfter(WORD.find(code)?.value.orEmpty()).trimStart()
    if ('=' in body) return StatementClass.DML
    val name = body.substringBefore('(').trim().substringAfterLast('.').trim().uppercase()
    return if (name in READ_ONLY_PRAGMAS) StatementClass.READ else StatementClass.DML
}

/** Render a bound JDBC value as a SQLite literal. */
internal fun sqlLiteralOf(value: Any?): String =
    when (value) {
        null -> "NULL"
        is Boolean -> if (value) "1" else "0"
        is ByteArray -> "X'" + value.joinToString("") { "%02x".format(it) } + "'"
        is Number ->
            value.toString().takeIf(SQL_NUMBER::matches)
                ?: throw SQLException("unsupported non-finite or malformed numeric value: $value")
        is java.time.temporal.TemporalAccessor -> quoteSqlString(value.toString())
        is java.util.Date -> quoteSqlString(value.toString())
        else -> quoteSqlString(value.toString())
    }

/**
 * Replace positional markers outside literals, quoted identifiers, and comments.
 * Throws when a marker is missing or a supplied binding is unused.
 */
internal fun substituteParams(sql: String, params: Map<Int, String>): String {
    val out = StringBuilder(sql.length)
    var state = ScanState.CODE
    var next = 1
    var i = 0
    while (i < sql.length) {
        val c = sql[i]
        val n = sql.getOrNull(i + 1)
        when (state) {
            ScanState.CODE ->
                when {
                    c == '\'' -> state = ScanState.SINGLE_QUOTE
                    c == '"' -> state = ScanState.DOUBLE_QUOTE
                    c == '`' -> state = ScanState.BACKTICK
                    c == '[' -> state = ScanState.BRACKET
                    c == '-' && n == '-' -> state = ScanState.LINE_COMMENT
                    c == '/' && n == '*' -> state = ScanState.BLOCK_COMMENT
                    c == '?' -> {
                        out.append(params[next] ?: throw SQLException("no value bound for parameter $next"))
                        next++
                        i++
                        continue
                    }
                }
            ScanState.SINGLE_QUOTE -> if (c == '\'' && !isEscapedQuote(sql, i, c)) state = ScanState.CODE
            ScanState.DOUBLE_QUOTE -> if (c == '"' && !isEscapedQuote(sql, i, c)) state = ScanState.CODE
            ScanState.BACKTICK -> if (c == '`' && !isEscapedQuote(sql, i, c)) state = ScanState.CODE
            ScanState.BRACKET -> if (c == ']' && n != ']') state = ScanState.CODE
            ScanState.LINE_COMMENT -> if (c == '\n' || c == '\r') state = ScanState.CODE
            ScanState.BLOCK_COMMENT -> if (c == '*' && n == '/') state = ScanState.CODE
        }
        out.append(c)
        if (state == ScanState.BLOCK_COMMENT && c == '*' && n == '/') {
            out.append('/')
            i++
        } else if (state == ScanState.BRACKET && c == ']' && n == ']') {
            out.append(']')
            i++
        } else if ((state == ScanState.SINGLE_QUOTE || state == ScanState.DOUBLE_QUOTE || state == ScanState.BACKTICK) && c == n) {
            out.append(n)
            i++
        }
        i++
    }
    val bound = params.keys.maxOrNull() ?: 0
    if (bound >= next) throw SQLException("bound parameter $bound but the statement has only ${next - 1} placeholders")
    return out.toString()
}

private fun isEscapedQuote(sql: String, index: Int, quote: Char): Boolean =
    sql.getOrNull(index + 1) == quote

/** Split on semicolons that are actual SQL syntax, not quoted/comment text. */
private fun splitStatements(sql: String): List<String> {
    val result = mutableListOf<String>()
    var state = ScanState.CODE
    var start = 0
    var i = 0
    while (i < sql.length) {
        val c = sql[i]
        val n = sql.getOrNull(i + 1)
        when (state) {
            ScanState.CODE ->
                when {
                    c == '\'' -> state = ScanState.SINGLE_QUOTE
                    c == '"' -> state = ScanState.DOUBLE_QUOTE
                    c == '`' -> state = ScanState.BACKTICK
                    c == '[' -> state = ScanState.BRACKET
                    c == '-' && n == '-' -> state = ScanState.LINE_COMMENT
                    c == '/' && n == '*' -> state = ScanState.BLOCK_COMMENT
                    c == ';' -> {
                        sql.substring(start, i).trim().takeIf { it.isNotEmpty() }?.let(result::add)
                        start = i + 1
                    }
                }
            ScanState.SINGLE_QUOTE -> if (c == '\'' && n != '\'') state = ScanState.CODE else if (c == '\'' && n == '\'') i++
            ScanState.DOUBLE_QUOTE -> if (c == '"' && n != '"') state = ScanState.CODE else if (c == '"' && n == '"') i++
            ScanState.BACKTICK -> if (c == '`' && n != '`') state = ScanState.CODE else if (c == '`' && n == '`') i++
            ScanState.BRACKET -> if (c == ']' && n != ']') state = ScanState.CODE else if (c == ']' && n == ']') i++
            ScanState.LINE_COMMENT -> if (c == '\n' || c == '\r') state = ScanState.CODE
            ScanState.BLOCK_COMMENT -> if (c == '*' && n == '/') {
                state = ScanState.CODE
                i++
            }
        }
        i++
    }
    sql.substring(start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
    return result
}

/** Replace literals/comments with spaces while preserving executable SQL text. */
private fun codeOnly(sql: String): String {
    val out = StringBuilder(sql.length)
    var state = ScanState.CODE
    var i = 0
    while (i < sql.length) {
        val c = sql[i]
        val n = sql.getOrNull(i + 1)
        when (state) {
            ScanState.CODE ->
                when {
                    c == '\'' -> { state = ScanState.SINGLE_QUOTE; out.append(' ') }
                    c == '"' -> { state = ScanState.DOUBLE_QUOTE; out.append(' ') }
                    c == '`' -> { state = ScanState.BACKTICK; out.append(' ') }
                    c == '[' -> { state = ScanState.BRACKET; out.append(' ') }
                    c == '-' && n == '-' -> { state = ScanState.LINE_COMMENT; out.append("  "); i++ }
                    c == '/' && n == '*' -> { state = ScanState.BLOCK_COMMENT; out.append("  "); i++ }
                    else -> out.append(c)
                }
            ScanState.SINGLE_QUOTE -> { out.append(' '); if (c == '\'' && n != '\'') state = ScanState.CODE else if (c == '\'' && n == '\'') { out.append(' '); i++ } }
            ScanState.DOUBLE_QUOTE -> { out.append(' '); if (c == '"' && n != '"') state = ScanState.CODE else if (c == '"' && n == '"') { out.append(' '); i++ } }
            ScanState.BACKTICK -> { out.append(' '); if (c == '`' && n != '`') state = ScanState.CODE else if (c == '`' && n == '`') { out.append(' '); i++ } }
            ScanState.BRACKET -> { out.append(' '); if (c == ']' && n != ']') state = ScanState.CODE else if (c == ']' && n == ']') { out.append(' '); i++ } }
            ScanState.LINE_COMMENT -> { out.append(' '); if (c == '\n' || c == '\r') state = ScanState.CODE }
            ScanState.BLOCK_COMMENT -> { out.append(' '); if (c == '*' && n == '/') { out.append(' '); state = ScanState.CODE; i++ } }
        }
        i++
    }
    return out.toString()
}

private fun codeWords(sql: String): Set<String> = WORD.findAll(codeOnly(sql)).map { it.value.uppercase() }.toSet()

private val WORD = Regex("[A-Za-z_][A-Za-z0-9_]*")
