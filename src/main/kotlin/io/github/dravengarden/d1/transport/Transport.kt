package io.github.dravengarden.d1.transport

import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
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

    /** Run [command] while streaming [standardInput] to its stdin. */
    public fun runWithInput(command: List<String>, workingDir: String?, standardInput: String): String =
        throw TransportException("${this::class.java.name} does not support command stdin")

    /** Human-readable host for error messages (e.g. `SSH host 'hawk'`). */
    public val description: String get() = "the engine host"
}

public class TransportException(message: String) : RuntimeException(message)

private const val DEFAULT_TIMEOUT_SECONDS = 120L
private const val MAX_OUTPUT_BYTES = 64 * 1024 * 1024
private const val ERROR_OUTPUT_CHARS = 16 * 1024
private const val PROCESS_POLL_MILLIS = 50L

private fun exec(
    argv: List<String>,
    workingDir: String?,
    timeoutSeconds: Long,
    environment: Map<String, String> = emptyMap(),
    standardInput: String? = null,
): String {
    require(argv.isNotEmpty()) { "command must not be empty" }
    val executable = argv.first()
    val process =
        try {
            ProcessBuilder(argv)
                .apply {
                    workingDir?.let { directory(File(it)) }
                    if (environment.isNotEmpty()) environment().putAll(environment)
                }
                // A single concurrently-drained stream cannot deadlock when a
                // child is noisy on stderr. The exit code still distinguishes
                // successful output from diagnostics.
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw TransportException("failed to start '$executable': ${e.message}")
    }

    val outputTask = FutureTask { readBounded(process.inputStream) }
    val inputTask =
        FutureTask {
            process.outputStream.use { stream ->
                standardInput?.let { stream.write(it.toByteArray(StandardCharsets.UTF_8)) }
            }
        }
    Thread(outputTask, "d1-command-output").apply { isDaemon = true }.start()
    Thread(inputTask, "d1-command-input").apply { isDaemon = true }.start()

    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    while (process.isAlive) {
        if (outputTask.isDone) outputTask.failureOrNull()?.let { failProcess(process, it) }
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) {
            terminateTree(process)
            throw TransportException("command '$executable' timed out after ${timeoutSeconds}s")
        }
        process.waitFor(minOf(PROCESS_POLL_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1)), TimeUnit.MILLISECONDS)
    }

    val output = outputTask.value()
    try {
        inputTask.value()
    } catch (e: Exception) {
        throw TransportException("failed to send input to '$executable': ${e.message}")
    }
    if (process.exitValue() != 0) {
        throw TransportException(
            "command '$executable' failed (exit ${process.exitValue()}):\n${output.takeLast(ERROR_OUTPUT_CHARS)}",
        )
    }
    return output
}

private fun readBounded(stream: InputStream): String =
    stream.use {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > MAX_OUTPUT_BYTES) {
                throw TransportException("command output exceeded ${MAX_OUTPUT_BYTES / (1024 * 1024)} MiB")
            }
            bytes.write(buffer, 0, count)
        }
        bytes.toString(StandardCharsets.UTF_8)
    }

private fun failProcess(process: Process, failure: Throwable): Nothing {
    terminateTree(process)
    throw if (failure is TransportException) failure else TransportException(failure.message ?: failure.toString())
}

private fun terminateTree(process: Process) {
    val descendants = process.descendants().toList().asReversed()
    descendants.forEach { it.destroy() }
    process.destroy()
    process.waitFor(250, TimeUnit.MILLISECONDS)
    descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
    if (process.isAlive) process.destroyForcibly()
    process.waitFor(1, TimeUnit.SECONDS)
}

private fun <T> FutureTask<T>.failureOrNull(): Throwable? {
    if (!isDone) return null
    return try {
        get()
        null
    } catch (e: ExecutionException) {
        e.cause ?: e
    }
}

private fun <T> FutureTask<T>.value(): T =
    try {
        get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

/**
 * Runs `wrangler` on the same machine as the driver (DataGrip's host).
 * [environment] is merged into the child process env — used to pass
 * `CLOUDFLARE_API_TOKEN` for `mode=remote` without putting it on the command line.
 */
public class LocalTransport(
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    private val environment: Map<String, String> = emptyMap(),
) : Transport {
    override val description: String = "this machine"

    override fun run(command: List<String>, workingDir: String?): String =
        exec(command, workingDir, timeoutSeconds, environment)

    override fun runWithInput(command: List<String>, workingDir: String?, standardInput: String): String =
        exec(command, workingDir, timeoutSeconds, environment, standardInput)
}

/**
 * Runs `wrangler` on a remote host over SSH (e.g. a dev server). Authentication
 * is delegated entirely to the OS `ssh` client + `~/.ssh/config` (keys,
 * known_hosts, ControlMaster) — no secrets here. [sshCommand] (default `ssh`) and
 * [sshOptions] (e.g. `-p 2222`) let callers point at a different ssh / port /
 * jump host without putting any credential in the URL.
 */
public class SshTransport(
    private val host: String,
    private val sshCommand: List<String> = listOf("ssh"),
    private val sshOptions: List<String> = emptyList(),
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : Transport {
    override val description: String = "SSH host '$host'"

    override fun run(command: List<String>, workingDir: String?): String =
        exec(sshArgv(command, workingDir), workingDir = null, timeoutSeconds)

    override fun runWithInput(command: List<String>, workingDir: String?, standardInput: String): String =
        exec(
            sshArgv(command, workingDir),
            workingDir = null,
            timeoutSeconds,
            standardInput = standardInput,
        )

    internal fun sshArgv(command: List<String>, workingDir: String?): List<String> =
        sshCommand + sshOptions + listOf("--", host, remoteCommand(command, workingDir))

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
