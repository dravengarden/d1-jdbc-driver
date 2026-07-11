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
 * How a query reaches the D1 data. `AUTO` picks the fast path that fits: a local
 * SQLite read for `mode=local` (when a file is resolvable), else `WRANGLER`.
 */
public enum class EngineKind { AUTO, WRANGLER, SQLITE, HTTP }

/**
 * Client-side write guardrail, increasing. [READ] (the default) allows only
 * SELECT/PRAGMA; [WRITE] adds DML (INSERT/UPDATE/DELETE); [DDL] adds schema
 * changes (CREATE/ALTER/DROP/…). It refuses to *send* over-privileged SQL — it
 * is not a server-side boundary (use a read-only Cloudflare token for that).
 */
public enum class Access { READ, WRITE, DDL }

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
    /** Write guardrail level (default [Access.READ] — writes must be opted into). */
    val access: Access = Access.READ,
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
    /** Which engine runs queries (default [EngineKind.AUTO]). */
    val engine: EngineKind = EngineKind.AUTO,
    /** The sqlite shell for [EngineKind.SQLITE], token-split (default `["sqlite3"]`). */
    val sqliteCommand: List<String> = listOf("sqlite3"),
    /** Explicit `.sqlite` path for [EngineKind.SQLITE]; else resolved from [persistTo]. */
    val sqliteFile: String? = null,
    /** Cloudflare account id for [EngineKind.HTTP]. */
    val httpAccountId: String? = null,
    /** D1 database id (UUID) for [EngineKind.HTTP] — distinct from the [database] name. */
    val httpDatabaseId: String? = null,
    /** Env file the HTTP engine reads the token from on the host (default `.env`). */
    val httpEnvFile: String = ".env",
    /** Env var holding the Cloudflare token in [httpEnvFile] (default `CLOUDFLARE_API_TOKEN`). */
    val httpTokenVar: String = "CLOUDFLARE_API_TOKEN",
) {
    /** The engine that actually runs queries, resolving [EngineKind.AUTO]. */
    public fun toEngine(): Engine {
        validate()
        val transport = toTransport()
        return when (resolveEngine()) {
            EngineKind.SQLITE -> SqliteEngine(transport, sqliteCommand, workingDir, persistTo, sqliteFile)
            EngineKind.HTTP ->
                HttpEngine(
                    transport,
                    workingDir,
                    accountId = requireNotNull(httpAccountId) { "engine=http needs account=" },
                    databaseId = requireNotNull(httpDatabaseId) { "engine=http needs database-id=" },
                    processTokenAvailable = apiToken != null && this.transport == TransportKind.NORMAL,
                    envFile = httpEnvFile,
                    tokenVar = httpTokenVar,
                )
            else -> Wrangler(transport, this)
        }
    }

    private fun resolveEngine(): EngineKind =
        when (engine) {
            EngineKind.AUTO ->
                when {
                    mode == Mode.LOCAL && access == Access.READ && (persistTo != null || sqliteFile != null) -> EngineKind.SQLITE
                    mode == Mode.REMOTE && httpAccountId != null && httpDatabaseId != null -> EngineKind.HTTP
                    else -> EngineKind.WRANGLER
                }
            else -> engine
        }

    public fun toTransport(): Transport =
        when (transport) {
            TransportKind.NORMAL ->
                LocalTransport(
                    timeoutSeconds = timeoutSeconds,
                    environment = localTokenEnvironment(),
                )
            TransportKind.SSH ->
                SshTransport(
                    host = requireNotNull(sshHost) { "transport=ssh requires host=" },
                    sshCommand = sshCommand,
                    sshOptions = sshOptions,
                    timeoutSeconds = timeoutSeconds,
                )
        }

    private fun localTokenEnvironment(): Map<String, String> {
        val token = apiToken ?: return emptyMap()
        return when (resolveEngine()) {
            EngineKind.HTTP -> mapOf("D1_JDBC_API_TOKEN" to token)
            EngineKind.WRANGLER -> mapOf("CLOUDFLARE_API_TOKEN" to token)
            else -> emptyMap()
        }
    }

    private fun validate() {
        require(database.isNotBlank()) { "db= must not be blank" }
        require(wranglerCommand.isNotEmpty()) { "wrangler command must not be empty" }
        require(sshCommand.isNotEmpty()) { "ssh command must not be empty" }
        require(sqliteCommand.isNotEmpty()) { "sqlite command must not be empty" }
        require(timeoutSeconds <= MAX_TIMEOUT_SECONDS) { "timeout= must not exceed $MAX_TIMEOUT_SECONDS seconds" }
        if (transport == TransportKind.SSH) require(!sshHost.isNullOrBlank()) { "transport=ssh requires host=" }
        when (resolveEngine()) {
            EngineKind.SQLITE -> {
                require(mode == Mode.LOCAL) { "engine=sqlite only supports mode=local" }
                require(access == Access.READ) { "engine=sqlite is read-only; use engine=wrangler for write/ddl access" }
                require(persistTo != null || sqliteFile != null) { "engine=sqlite needs persist= or file=" }
            }
            EngineKind.HTTP -> {
                require(mode == Mode.REMOTE) { "engine=http only supports mode=remote" }
                require(!httpAccountId.isNullOrBlank()) { "engine=http needs account=" }
                require(!httpDatabaseId.isNullOrBlank()) { "engine=http needs database-id=" }
                require(ID_COMPONENT.matches(httpAccountId)) { "invalid account= identifier" }
                require(ID_COMPONENT.matches(httpDatabaseId)) { "invalid database-id= identifier" }
                require(ENV_NAME.matches(httpTokenVar)) { "invalid token-var= (expected a shell environment variable name)" }
            }
            else -> Unit
        }
    }

    /** Never include [apiToken] in logs, exception diagnostics, or debugger output. */
    override fun toString(): String =
        "D1Config(transport=$transport, sshHost=$sshHost, workingDir=$workingDir, database=$database, " +
            "mode=$mode, env=$env, configPath=$configPath, wranglerCommand=$wranglerCommand, " +
            "persistTo=$persistTo, sshCommand=$sshCommand, sshOptions=$sshOptions, timeoutSeconds=$timeoutSeconds, " +
            "probe=$probe, access=$access, cacheIntrospection=$cacheIntrospection, apiToken=<redacted>, " +
            "engine=$engine, sqliteCommand=$sqliteCommand, sqliteFile=$sqliteFile, httpAccountId=$httpAccountId, " +
            "httpDatabaseId=$httpDatabaseId, httpEnvFile=$httpEnvFile, httpTokenVar=$httpTokenVar)"

    public companion object {
        public const val URL_PREFIX: String = "jdbc:d1:"
        public const val DEFAULT_TIMEOUT_SECONDS: Long = 120
        private const val MAX_TIMEOUT_SECONDS = 86_400L
        private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val ID_COMPONENT = Regex("[A-Za-z0-9_-]+")

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
            val engine =
                when (val e = value("engine")?.lowercase() ?: "auto") {
                    "auto" -> EngineKind.AUTO
                    "wrangler" -> EngineKind.WRANGLER
                    "sqlite" -> EngineKind.SQLITE
                    "http" -> EngineKind.HTTP
                    else -> error("unknown engine: $e (expected auto|wrangler|sqlite|http)")
                }
            val wrangler = tokens("wrangler").ifEmpty { listOf("wrangler") }
            val sshCommand = tokens("ssh").ifEmpty { listOf("ssh") }
            val sqlite = tokens("sqlite").ifEmpty { listOf("sqlite3") }
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
                access = parseAccess(value("access"), value("readonly")),
                cacheIntrospection = flag("cache", default = true),
                // Token comes ONLY from the password property, never the URL.
                apiToken = props.getProperty("password")?.takeIf { it.isNotEmpty() },
                engine = engine,
                sqliteCommand = sqlite,
                sqliteFile = value("file"),
                httpAccountId = value("account"),
                httpDatabaseId = value("database-id"),
                httpEnvFile = value("env-file") ?: ".env",
                httpTokenVar = value("token-var") ?: "CLOUDFLARE_API_TOKEN",
            )
        }

        /** `access=read|write|ddl` (with aliases); falls back to the legacy `readonly=` flag. */
        private fun parseAccess(access: String?, readonly: String?): Access {
            access?.lowercase()?.let {
                return when (it) {
                    "read", "ro", "readonly" -> Access.READ
                    "write", "rw" -> Access.WRITE
                    "ddl", "full", "admin" -> Access.DDL
                    else -> error("unknown access: $it (expected read|write|ddl)")
                }
            }
            readonly?.lowercase()?.let {
                return when (it) {
                    "true", "1", "yes", "on" -> Access.READ
                    "false", "0", "no", "off" -> Access.WRITE
                    else -> error("invalid boolean for readonly= (expected true|false)")
                }
            }
            return Access.READ
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
