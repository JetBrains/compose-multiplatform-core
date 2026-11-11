/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.benchmarks.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.readText

internal fun appendLog(path: Path, text: String) {
    Files.writeString(path, text, CREATE, APPEND)
}

internal class Processes(
    private val root: Path,
    private val environment: Map<String, String>,
    private val commandLog: Path,
) : AutoCloseable {
    private val active = mutableSetOf<ManagedProcess>()
    private var closed = false

    @Synchronized
    fun start(command: List<String>, log: Path, output: Path = log): ManagedProcess {
        check(!closed) { "launcher is stopping" }
        appendLog(log, "\n$ ${command.joinToString(" ")}\n")
        val builder = ProcessBuilder(command).directory(root.toFile())
            .redirectOutput(ProcessBuilder.Redirect.appendTo(output.toFile()))
        if (output == log) builder.redirectErrorStream(true)
        else builder.redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()))
        builder.environment().putAll(environment)
        return ManagedProcess(builder.start()) { synchronized(this) { active.remove(it) } }
            .also { active.add(it) }
    }

    fun run(
        command: List<String>,
        log: Path,
        timeoutSeconds: Long? = null,
        checkExit: Boolean = true
    ) {
        println("Running: ${command.joinToString(" ")}")
        start(command, log).use { child ->
            if (timeoutSeconds == null) child.process.waitFor()
            else check(
                child.process.waitFor(
                    timeoutSeconds,
                    TimeUnit.SECONDS
                )
            ) { "command timed out; see $log" }
            check(!checkExit || child.process.exitValue() == 0) { "command failed; see $log" }
        }
    }

    fun capture(command: List<String>, timeoutSeconds: Long = 30): String {
        val output = Files.createTempFile("benchmark-command-", ".log")
        try {
            start(command, commandLog, output).use { child ->
                if (!child.process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw TimeoutException("command timed out: ${command.joinToString(" ")}")
                }
                val text = output.readText()
                appendLog(commandLog, text)
                check(child.process.exitValue() == 0) { "command failed; see $commandLog" }
                return text
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }

    override fun close() {
        val children = synchronized(this) {
            closed = true
            active.toList().reversed()
        }
        children.forEach { it.close() }
    }
}

internal class ManagedProcess(
    val process: Process,
    private val onClose: (ManagedProcess) -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        var interrupted = Thread.interrupted()
        try {
            // Only our descendants are considered. Browser instances use isolated profiles.
            // Gradle and Kotlin daemons are intentionally allowed to outlive a build command.
            val descendants = process.descendants().use { stream -> stream.toList() }.filterNot {
                val command = it.info().commandLine().orElse("")
                "GradleDaemon" in command || "KotlinCompileDaemon" in command
            }
            val handles = descendants.reversed() + process.toHandle()
            handles.forEach { runCatching { it.destroy() } }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (handles.any { it.isAlive } && System.nanoTime() < deadline) {
                try {
                    Thread.sleep(25)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            handles.filter { it.isAlive }.forEach { runCatching { it.destroyForcibly() } }
            try {
                process.waitFor(3, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                interrupted = true
            }
        } finally {
            onClose(this)
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}

internal fun deleteTree(directory: Path) {
    Files.walk(directory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }
}
