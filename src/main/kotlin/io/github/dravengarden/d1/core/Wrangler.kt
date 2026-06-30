package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.model.QueryResult
import io.github.dravengarden.d1.model.WranglerResult
import io.github.dravengarden.d1.transport.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull

/**
 * The functional core: turns a SQL string into a `wrangler d1 execute … --json`
 * invocation, runs it through a [Transport], and parses the JSON back into a
 * [QueryResult]. No JDBC here — this is independently testable.
 */
public class Wrangler(
    private val transport: Transport,
    private val config: D1Config,
) {
    public fun execute(sql: String): QueryResult = parse(transport.run(buildArgs(sql), config.workingDir))

    internal fun buildArgs(sql: String): List<String> =
        buildList {
            addAll(config.wranglerCommand)
            add("d1")
            add("execute")
            add(config.database)
            config.configPath?.let {
                add("--config")
                add(it)
            }
            if (config.mode == Mode.LOCAL) {
                add("--local")
                config.persistTo?.let {
                    add("--persist-to")
                    add(it)
                }
            } else {
                add("--remote")
            }
            config.env?.let {
                add("--env")
                add(it)
            }
            add("--json")
            add("--command")
            add(sql)
        }

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * wrangler may emit non-JSON lines before the array (e.g. a banner). Slice
         * from the first `[` so parsing is robust; then take the last statement's
         * result set.
         */
        internal fun parse(stdout: String): QueryResult {
            val start = stdout.indexOf('[')
            require(start >= 0) { "no JSON array in wrangler output:\n$stdout" }
            val results = json.decodeFromString<List<WranglerResult>>(stdout.substring(start))
            val rows = results.lastOrNull()?.results ?: emptyList()
            if (rows.isEmpty()) {
                return QueryResult(columns = emptyList(), rows = emptyList())
            }
            val columns = rows.first().keys.toList()
            val tabular =
                rows.map { row ->
                    columns.map { col -> row[col] ?: JsonNull }
                }
            return QueryResult(columns = columns, rows = tabular)
        }
    }
}
