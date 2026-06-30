package io.github.dravengarden.d1.jdbc

import java.sql.SQLException

/**
 * `wrangler d1 execute --command` takes a single SQL string with no bind-parameter
 * channel, so [D1PreparedStatement] substitutes parameters client-side. These
 * helpers turn a JDBC value into a SQL literal and splice positional `?` markers,
 * skipping any `?` that sits inside a single-quoted string literal.
 */

/** Single-quote and escape a string for inlining as a SQL literal. */
internal fun quoteSqlString(value: String): String = "'" + value.replace("'", "''") + "'"

/**
 * Whether [sql] is a row-returning statement (drives `execute`'s result-set vs
 * update-count decision). wrangler's JSON can't distinguish a SELECT that matched
 * no rows from a write, so the leading keyword is the only reliable signal.
 */
internal fun looksLikeQuery(sql: String): Boolean {
    val keyword = sql.trimStart().takeWhile { !it.isWhitespace() && it != '(' }.uppercase()
    return keyword in setOf("SELECT", "PRAGMA", "WITH", "EXPLAIN", "VALUES")
}

/** Render a bound JDBC value as the SQL literal that replaces its `?`. */
internal fun sqlLiteralOf(value: Any?): String =
    when (value) {
        null -> "NULL"
        is Boolean -> if (value) "1" else "0"
        is Number -> value.toString()
        else -> quoteSqlString(value.toString())
    }

/**
 * Replace each top-level positional `?` in [sql] with the matching literal from
 * [params] (1-based). `?` characters inside single-quoted string literals are left
 * untouched. Throws if a placeholder has no bound value or a value is unused.
 */
internal fun substituteParams(sql: String, params: Map<Int, String>): String {
    val out = StringBuilder(sql.length)
    var inString = false
    var next = 1
    var i = 0
    while (i < sql.length) {
        val c = sql[i]
        when {
            inString -> {
                out.append(c)
                if (c == '\'') {
                    // A doubled '' is an escaped quote, not the end of the literal.
                    if (i + 1 < sql.length && sql[i + 1] == '\'') {
                        out.append('\'')
                        i++
                    } else {
                        inString = false
                    }
                }
            }
            c == '\'' -> {
                inString = true
                out.append(c)
            }
            c == '?' -> {
                out.append(params[next] ?: throw SQLException("no value bound for parameter $next"))
                next++
            }
            else -> out.append(c)
        }
        i++
    }
    val bound = params.keys.maxOrNull() ?: 0
    if (bound >= next) {
        throw SQLException("bound parameter $bound but the statement has only ${next - 1} placeholders")
    }
    return out.toString()
}
