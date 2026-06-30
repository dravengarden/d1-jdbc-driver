package io.github.dravengarden.d1.jdbc

import io.github.dravengarden.d1.core.D1Config
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.util.Properties
import java.util.logging.Logger

/**
 * JDBC entry point. Registered both via `META-INF/services/java.sql.Driver` and
 * the static initializer below. URLs look like:
 *
 * ```
 * jdbc:d1:?transport=ssh&host=hawk&dir=/path&db=kuaitu-local&mode=local&config=edge/worker/wrangler.jsonc
 * ```
 */
public class D1Driver : Driver {
    override fun acceptsURL(url: String?): Boolean = url?.startsWith(D1Config.URL_PREFIX) == true

    override fun connect(url: String?, info: Properties?): Connection? {
        if (!acceptsURL(url)) return null
        // Validate the URL eagerly so misconfiguration surfaces here, then prove the
        // backend answers before handing back a connection — DataGrip treats a
        // connection it cannot validate as dead.
        val config = D1Config.parse(url!!, info ?: Properties())
        val connection = D1Connection(config)
        if (config.probe) {
            try {
                connection.checkConnectivity()
            } catch (e: Exception) {
                throw SQLException("failed to reach d1 (db=${config.database}): ${e.message}", e)
            }
        }
        return connection
    }

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> =
        arrayOf(
            DriverPropertyInfo("transport", "normal").apply { choices = arrayOf("normal", "ssh") },
            DriverPropertyInfo("host", null),
            DriverPropertyInfo("dir", null),
            DriverPropertyInfo("db", null).apply { required = true },
            DriverPropertyInfo("mode", "local").apply { choices = arrayOf("local", "remote") },
            DriverPropertyInfo("env", null),
            DriverPropertyInfo("config", null),
            DriverPropertyInfo("wrangler", "wrangler"),
        )

    override fun getMajorVersion(): Int = 0

    override fun getMinorVersion(): Int = 1

    override fun jdbcCompliant(): Boolean = false

    override fun getParentLogger(): Logger = Logger.getLogger("io.github.dravengarden.d1")

    public companion object {
        init {
            DriverManager.registerDriver(D1Driver())
        }
    }
}
