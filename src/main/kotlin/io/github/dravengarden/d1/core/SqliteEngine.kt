package io.github.dravengarden.d1.core

import io.github.dravengarden.d1.model.QueryResult
import io.github.dravengarden.d1.transport.Transport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Reads a local (`mode=local`) D1 directly as the SQLite file miniflare keeps on
 * disk — no Node, no miniflare boot. A `wrangler d1 execute` spawn is ~1.5 s
 * (runtime cold-start); `sqlite3 -json` on the same file is ~milliseconds.
 *
 * Opened **read-only with a busy timeout** so it coexists with a running
 * `wrangler dev`: SQLite's WAL lets a reader run alongside the dev writer, and
 * direct reads see dev's committed writes (it's the same file). Writes are not
 * this engine's job — they stay on [Wrangler].
 *
 * The file lives at `<persist>/v3/d1/miniflare-D1DatabaseObject/<hash>.sqlite`;
 * the hash is miniflare-internal, so the path is resolved by listing that dir
 * (through the [transport], so it works on the remote for `transport=ssh`).
 */
public class SqliteEngine(
    private val transport: Transport,
    /** The sqlite shell, token-split (default `["sqlite3"]`). */
    private val sqliteCommand: List<String>,
    private val workingDir: String?,
    /** `--persist-to` dir; the D1 file is resolved under it. Null if [file] is set. */
    private val persistDir: String?,
    /** Explicit `.sqlite` path; skips persist-dir resolution when set. */
    private val file: String?,
) : Engine {
    private var resolved: String? = file

    override fun checkAvailable(): Unit = requireTool(transport, workingDir, sqliteCommand.first(), "sqlite")

    override fun query(sql: String): QueryResult {
        val target = resolved ?: resolveFile().also { resolved = it }
        // -readonly: never block / corrupt a running `wrangler dev` writer.
        // .timeout: wait out a transient writer lock instead of erroring.
        val argv = sqliteCommand + listOf("-readonly", "-json", "-cmd", ".timeout 3000", target, sql)
        return parse(transport.run(argv, workingDir))
    }

    private fun resolveFile(): String {
        val dir = requireNotNull(persistDir) { "sqlite engine needs persist= (or an explicit file=)" }
        val root = "$dir/$MINIFLARE_D1_SUBDIR"
        val found =
            transport.run(listOf("find", root, "-name", "*.sqlite"), workingDir)
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.endsWith("/metadata.sqlite") }
        return when (found.size) {
            1 -> found.single()
            0 -> error("no local D1 SQLite file under $root (has `wrangler dev`/migrations run yet?)")
            else -> error("multiple D1 files under $root; set file= to pick one: $found")
        }
    }

    public companion object {
        private const val MINIFLARE_D1_SUBDIR = "v3/d1/miniflare-D1DatabaseObject"
        private val json = Json { ignoreUnknownKeys = true }

        /** `sqlite3 -json` prints a bare `[ {…} ]` array (or nothing for no rows). */
        internal fun parse(stdout: String): QueryResult {
            val start = stdout.indexOf('[')
            if (start < 0) return QueryResult(emptyList(), emptyList())
            val rows = json.decodeFromString<List<JsonObject>>(stdout.substring(start))
            return tabulate(rows)
        }
    }
}
