package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.model.QueryResult
import io.github.dravengarden.d1.model.WranglerResult
import io.github.dravengarden.d1.transport.Transport
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/**
 * Queries a remote (`mode=remote`) D1 via the Cloudflare D1 REST API — no Node,
 * no wrangler. Runs `curl` through a [Transport], so `transport=ssh` makes the
 * request egress from the remote host (handy when its path to Cloudflare beats
 * the client's) while `transport=normal` calls Cloudflare from the client.
 *
 * The API token is **never put on the command line**: it is read fresh on the
 * host from an env file (so a rotated token is picked up) and piped to curl's
 * `-H @-` over the (encrypted, for ssh) channel; or, if supplied via the JDBC
 * `password`, embedded by the driver. The SQL is not a secret and rides
 * `--data-raw`.
 */
public class HttpEngine(
    private val transport: Transport,
    private val workingDir: String?,
    accountId: String,
    databaseId: String,
    /** Token from the JDBC `password`; null → read [tokenVar] from [envFile] on the host. */
    private val explicitToken: String?,
    /** Env file the host reads the token from when [explicitToken] is null (e.g. `.env`). */
    private val envFile: String,
    private val tokenVar: String,
) : Engine {
    private val url =
        "https://api.cloudflare.com/client/v4/accounts/$accountId/d1/database/$databaseId/query"

    override fun checkAvailable(): Unit = requireTool(transport, workingDir, "curl", null)

    override fun query(sql: String): QueryResult {
        // JsonObject.toString() is valid, correctly-escaped JSON: {"sql":"…"}.
        val body = JsonObject(mapOf("sql" to JsonPrimitive(sql))).toString()
        // The pipeline is full of single quotes (the JSON body, the SQL's own
        // string literals). Over transport=ssh it goes through a SECOND shell-quote
        // layer (SshTransport), which mangles those quotes and corrupts the SQL.
        // base64 has no shell-special chars, so wrapping the script in it survives
        // any number of shell layers; the host decodes and runs it verbatim.
        val b64 = Base64.getEncoder().encodeToString(pipeline(body).toByteArray(Charsets.UTF_8))
        return parse(transport.run(listOf("sh", "-c", "printf %s $b64 | base64 -d | sh"), workingDir))
    }

    /** A POSIX-sh one-liner: get the token, pipe the auth header to curl, POST the query. */
    private fun pipeline(body: String): String {
        val token =
            if (explicitToken != null) {
                "TOKEN=${shq(explicitToken)}"
            } else {
                // Read the token fresh from the env file each call (picks up rotation).
                "TOKEN=\$(sed -n ${shq("s/^$tokenVar=//p")} ${shq(envFile)} | head -n1 | tr -d ${shq("\"")})"
            }
        return "$token; printf ${shq("Authorization: Bearer %s")} \"\$TOKEN\" | " +
            "curl -sS -H @- -H ${shq("Content-Type: application/json")} --data-raw ${shq(body)} ${shq(url)}"
    }

    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class ApiResponse(
            val success: Boolean = false,
            val result: List<WranglerResult> = emptyList(),
            val errors: List<ApiError> = emptyList(),
        )

        @Serializable
        private data class ApiError(val code: Int = 0, val message: String = "")

        internal fun parse(stdout: String): QueryResult {
            val start = stdout.indexOf('{')
            require(start >= 0) { "no JSON object in D1 API response:\n$stdout" }
            val resp = json.decodeFromString<ApiResponse>(stdout.substring(start))
            if (!resp.success) {
                val why = resp.errors.joinToString("; ") { "[${it.code}] ${it.message}" }.ifEmpty { stdout.take(300) }
                error("D1 API error: $why")
            }
            // The D1 API returns one result per statement; take the LAST (matching
            // Wrangler.parse) so a multi-statement query — as a client may send for
            // introspection — yields the final statement's rows, not the first.
            val last = resp.result.lastOrNull()
            val meta = last?.meta
            val changes = meta?.get("changes")?.jsonPrimitive?.longOrNull ?: 0L
            val lastRowId = meta?.get("last_row_id")?.jsonPrimitive?.longOrNull
            return tabulate(last?.results ?: emptyList(), changes, lastRowId)
        }
    }
}
