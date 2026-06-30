package io.github.dravengarden.d1.transport

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Strategy for *where* the `wrangler` command runs. Everything else in the
 * driver (building the command, parsing JSON) is shared. An open interface so
 * tests and callers can supply their own transport.
 */
public interface Transport {
    /**
     * Run [command] (argv, already split) in [workingDir] and return its stdout.
     * Throws [TransportException] on a non-zero exit, including stderr.
     */
    public fun run(command: List<String>, workingDir: String?): String
}

public class TransportException(message: String) : RuntimeException(message)

private const val TIMEOUT_SECONDS = 120L

private fun exec(argv: List<String>, workingDir: String?): String {
    val process =
        ProcessBuilder(argv)
            .apply { workingDir?.let { directory(File(it)) } }
            .redirectErrorStream(false)
            .start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        throw TransportException("command timed out after ${TIMEOUT_SECONDS}s: ${argv.joinToString(" ")}")
    }
    if (process.exitValue() != 0) {
        throw TransportException("command failed (exit ${process.exitValue()}): ${argv.joinToString(" ")}\n$stderr")
    }
    return stdout
}

/** Runs `wrangler` on the same machine as the driver (DataGrip's host). */
public class LocalTransport : Transport {
    override fun run(command: List<String>, workingDir: String?): String = exec(command, workingDir)
}

/**
 * Runs `wrangler` on a remote host over SSH (e.g. hawk). Authentication is
 * delegated entirely to the OS `ssh` client + `~/.ssh/config` (keys, known_hosts,
 * ControlMaster) — no secrets here.
 */
public class SshTransport(
    private val host: String,
    private val sshOptions: List<String> = emptyList(),
) : Transport {
    override fun run(command: List<String>, workingDir: String?): String =
        exec(listOf("ssh") + sshOptions + listOf(host, remoteCommand(command, workingDir)), workingDir = null)

    /** The single shell string sent to the remote: `cd <dir> && <argv…>`, quoted. */
    internal fun remoteCommand(command: List<String>, workingDir: String?): String =
        buildString {
            if (workingDir != null) {
                append("cd ")
                append(shellQuote(workingDir))
                append(" && ")
            }
            append(command.joinToString(" ") { shellQuote(it) })
        }

    private fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"
}
