package io.github.dravengarden.d1.cli

import io.github.dravengarden.d1.core.D1Config
import io.github.dravengarden.d1.core.Wrangler
import java.util.Properties
import kotlin.system.exitProcess

/**
 * A thin CLI over the wrangler core, for manual end-to-end checks before the
 * JDBC layer exists:
 *
 * ```
 * d1-cli "jdbc:d1:?db=kuaitu-local&mode=local&dir=/path&config=edge/worker/wrangler.jsonc" "SELECT * FROM accounts"
 * ```
 */
public fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: d1-cli <jdbc-url> <sql>")
        exitProcess(2)
    }
    val config = D1Config.parse(args[0], Properties())
    val result = Wrangler(config.toTransport(), config).execute(args[1])

    println(result.columns.joinToString(" | "))
    println("-".repeat(40))
    for (row in result.rows) {
        println(row.joinToString(" | ") { it.toString().removeSurrounding("\"") })
    }
    println("(${result.rows.size} rows)")
}
