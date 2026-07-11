package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.model.QueryResult
import io.github.dravengarden.d1.model.WranglerResult
import io.github.dravengarden.d1.transport.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The wrangler-backed [Engine]: turns a SQL string into a `wrangler d1 execute …
 * --json` invocation, runs it through a [Transport], and parses the JSON back
 * into a [QueryResult]. No JDBC here — this is independently testable.
 */
public class Wrangler(
    private val transport: Transport,
    private val config: D1Config,
) : Engine {
    override fun query(sql: String): QueryResult = parse(transport.run(buildArgs(sql), config.workingDir))

    override fun checkAvailable(): Unit =
        requireTool(transport, config.workingDir, config.wranglerCommand.first(), "wrangler")

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
         * Wrangler may emit non-JSON lines before the array (including bracketed
         * banners). Try candidate array starts from the end, then take the last
         * statement's result set.
         */
        internal fun parse(stdout: String): QueryResult {
            val results = decodeResults(stdout)
            val failure = results.firstOrNull { !it.success }
            require(failure == null) { "wrangler reported an unsuccessful statement: ${failure?.error ?: "unknown error"}" }
            val last = results.lastOrNull()
            val meta = last?.meta
            val changes = meta?.get("changes")?.jsonPrimitive?.longOrNull ?: 0L
            val lastRowId = meta?.get("last_row_id")?.jsonPrimitive?.longOrNull
            return tabulate(last?.results ?: emptyList(), changes, lastRowId)
        }

        private fun decodeResults(stdout: String): List<WranglerResult> {
            for (start in stdout.indices.reversed()) {
                if (stdout[start] != '[') continue
                val decoded = runCatching { json.decodeFromString<List<WranglerResult>>(stdout.substring(start).trim()) }.getOrNull()
                if (decoded != null) return decoded
            }
            error("no valid JSON result array in wrangler output:\n${stdout.takeLast(1000)}")
        }
    }
}
