package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.transport.LocalTransport
import io.github.dravengarden.d1.transport.SshTransport
import io.github.dravengarden.d1.transport.Transport
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Properties

public enum class Mode { LOCAL, REMOTE }

public enum class TransportKind { NORMAL, SSH }

/**
 * Everything needed to run a D1 query, parsed from a single JDBC URL of the form
 *
 * ```
 * jdbc:d1:?transport=ssh&host=hawk&dir=/path/to/project&db=kuaitu-local&mode=local&config=edge/worker/wrangler.jsonc
 * ```
 *
 * SSH auth is NOT encoded here — it is delegated to the OS `ssh` client.
 */
public data class D1Config(
    val transport: TransportKind,
    val sshHost: String?,
    val workingDir: String?,
    val database: String,
    val mode: Mode,
    val env: String?,
    val configPath: String?,
    /** The wrangler command, token-split (e.g. `["wrangler"]`, `["d1q"]`). */
    val wranglerCommand: List<String>,
    /** `--persist-to` for local mode (miniflare state dir, e.g. `.wrangler/state`). */
    val persistTo: String? = null,
) {
    public fun toTransport(): Transport =
        when (transport) {
            TransportKind.NORMAL -> LocalTransport()
            TransportKind.SSH ->
                SshTransport(
                    host = requireNotNull(sshHost) { "transport=ssh requires host=" },
                )
        }

    public companion object {
        public const val URL_PREFIX: String = "jdbc:d1:"

        public fun parse(url: String, props: Properties): D1Config {
            require(url.startsWith(URL_PREFIX)) { "not a d1 url: $url" }
            val query = url.removePrefix(URL_PREFIX).removePrefix("?")
            val params = parseQuery(query)

            fun value(key: String): String? = params[key] ?: props.getProperty(key)

            val transport =
                when (val t = value("transport")?.lowercase() ?: "normal") {
                    "normal" -> TransportKind.NORMAL
                    "ssh", "proxy" -> TransportKind.SSH
                    else -> error("unknown transport: $t (expected normal|ssh)")
                }
            val mode =
                when (val m = value("mode")?.lowercase() ?: "local") {
                    "local" -> Mode.LOCAL
                    "remote" -> Mode.REMOTE
                    else -> error("unknown mode: $m (expected local|remote)")
                }
            val wrangler = (value("wrangler") ?: "wrangler").trim().split(" ").filter { it.isNotEmpty() }

            return D1Config(
                transport = transport,
                sshHost = value("host"),
                workingDir = value("dir"),
                database = requireNotNull(value("db")) { "missing db= in url" },
                mode = mode,
                env = value("env"),
                configPath = value("config"),
                wranglerCommand = wrangler,
                persistTo = value("persist"),
            )
        }

        private fun parseQuery(query: String): Map<String, String> =
            query
                .split("&")
                .filter { it.isNotEmpty() }
                .associate { pair ->
                    val idx = pair.indexOf('=')
                    if (idx == -1) {
                        decode(pair) to ""
                    } else {
                        decode(pair.substring(0, idx)) to decode(pair.substring(idx + 1))
                    }
                }

        private fun decode(s: String): String = URLDecoder.decode(s, StandardCharsets.UTF_8)
    }
}
