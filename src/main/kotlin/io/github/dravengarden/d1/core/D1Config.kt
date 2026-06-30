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
    /** The wrangler command, token-split (e.g. `["wrangler"]`, `["pnpm", "exec", "wrangler"]`). */
    val wranglerCommand: List<String>,
    /** `--persist-to` for local mode (miniflare state dir, e.g. `.wrangler/state`). */
    val persistTo: String? = null,
    /** The ssh command for `transport=ssh`, token-split (default `["ssh"]`). */
    val sshCommand: List<String> = listOf("ssh"),
    /** Extra non-secret ssh args inserted before the host, e.g. `["-p", "2222"]`. */
    val sshOptions: List<String> = emptyList(),
    /** Per-command wrangler timeout in seconds (default 120). */
    val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    /** Whether `connect()` runs a `SELECT 1` connectivity probe (default true). */
    val probe: Boolean = true,
    /** Whether the connection rejects writes (default false). */
    val readOnly: Boolean = false,
    /** Whether schema introspection is cached per connection (default true). */
    val cacheIntrospection: Boolean = true,
    /**
     * Cloudflare API token for `mode=remote`, taken from the JDBC `password`
     * property — never from the URL. Injected into the local wrangler process
     * env as `CLOUDFLARE_API_TOKEN`. Ignored for `transport=ssh` (the token must
     * live on the server, e.g. its `.env`), since forwarding a secret over the
     * ssh command line is unsafe.
     */
    val apiToken: String? = null,
) {
    public fun toTransport(): Transport =
        when (transport) {
            TransportKind.NORMAL ->
                LocalTransport(
                    timeoutSeconds = timeoutSeconds,
                    environment = apiToken?.let { mapOf("CLOUDFLARE_API_TOKEN" to it) } ?: emptyMap(),
                )
            TransportKind.SSH ->
                SshTransport(
                    host = requireNotNull(sshHost) { "transport=ssh requires host=" },
                    sshCommand = sshCommand,
                    sshOptions = sshOptions,
                    timeoutSeconds = timeoutSeconds,
                )
        }

    public companion object {
        public const val URL_PREFIX: String = "jdbc:d1:"
        public const val DEFAULT_TIMEOUT_SECONDS: Long = 120

        public fun parse(url: String, props: Properties): D1Config {
            require(url.startsWith(URL_PREFIX)) { "not a d1 url: $url" }
            val query = url.removePrefix(URL_PREFIX).removePrefix("?")
            val params = parseQuery(query)

            fun value(key: String): String? = params[key] ?: props.getProperty(key)
            fun tokens(key: String): List<String> = value(key)?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: emptyList()
            fun flag(key: String, default: Boolean): Boolean =
                when (value(key)?.lowercase()) {
                    null -> default
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> error("invalid boolean for $key= (expected true|false)")
                }

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
            val wrangler = tokens("wrangler").ifEmpty { listOf("wrangler") }
            val sshCommand = tokens("ssh").ifEmpty { listOf("ssh") }
            val timeout =
                value("timeout")?.let {
                    it.toLongOrNull()?.takeIf { n -> n > 0 } ?: error("invalid timeout= (expected positive seconds)")
                } ?: DEFAULT_TIMEOUT_SECONDS

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
                sshCommand = sshCommand,
                sshOptions = tokens("ssh-opts"),
                timeoutSeconds = timeout,
                probe = flag("probe", default = true),
                readOnly = flag("readonly", default = false),
                cacheIntrospection = flag("cache", default = true),
                // Token comes ONLY from the password property, never the URL.
                apiToken = props.getProperty("password")?.takeIf { it.isNotEmpty() },
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
