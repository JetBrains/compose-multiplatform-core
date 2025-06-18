/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.test.shell

import android.annotation.SuppressLint
import androidx.test.shell.internal.CircularBuffer
import androidx.test.shell.internal.ShellInstaller
import androidx.test.shell.internal.uiAutomation
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.sleep
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Represents a process that is running sh as a shell user.
 *
 * Opposite to [Shell] this class doesn't handle the concept of command, rather it allows to read
 * and write in the process streams, i.e. stdin, stdout, stderr. For example, using [ShellProcess],
 * it's possible to write a file in shell space like `/data/local/tmp` launching `cp /dev/stdin
 * /data/local/tmp/myfile`:
 * ```kotlin
 * ShellProcess.create().use {
 *
 *   // Writing a file
 *   it.write("cp /dev/stdin /data/local/tmp/myfile")
 *   it.stdin.write(myByteByffer)
 *
 *   // Reading a file
 *   it.write("cat /data/local/tmp/somefile")
 *   assert(it.stdout.bufferedReader().readLine() == "This is a test")
 * }
 * ```
 *
 * For the vast majority of commands used for testing, [Shell] process is a better candidate and
 * automatically captures the full command output, as opposed to [ShellProcess] that doesn't
 * distinguish where a command ends and another starts.
 *
 * Note that this class is not thread safe due to internal state changes, although streams can be
 * read or written by another thread, as long as it's always the same. For example it's possible to
 * read [ShellProcess.stdOut] from another thread, as long as this doesn't change later.
 *
 * [ShellProcess] should be used only when access to streams is required, like in the above example.
 */
public class ShellProcess
internal constructor(
    private val shellExecFile: File,
    private val command: String,
    private val stdInSocketPort: Int,
    private val stdOutSocketPort: Int,
    private val stdErrSocketPort: Int,
    private val connectToNativeProcessTimeoutMs: Int,
    private val shellProcessVerboseLogs: Boolean,
    stdOutBufferSize: Int,
    stdErrBufferSize: Int,
) : AutoCloseable {

    public companion object {
        private const val DEFAULT_STDOUT_BUFFER_SIZE = 32 * 1024
        private const val DEFAULT_STDERR_BUFFER_SIZE = 32 * 1024
        private const val LOCALHOST = "localhost"

        /**
         * Creates a new [ShellProcess].
         *
         * Shell process connects to an internal native utility via tcp. The given base port and the
         * two following ones are used.
         *
         * @param baseTcpPort a base tcp port to use to communicate with the native utility. Note
         *   that the given port and the 2 following ones are used.
         * @param stdOutBufferSize the size of the circular buffer where to store the stdout of the
         *   running shell process.
         * @param stdErrBufferSize the size of the circular buffer where to store the stderr of the
         *   running shell process.
         * @param connectToNativeProcessTimeoutMs timeout in ms to connect to the native process.
         * @param nativeLogs whether the native cli process should print Android logs. The tag used
         *   on logcat is `NativeShellProcess`.
         * @return a running shell process, ready to accept commands.
         * @throws IOException if the underlying native utility does not start.
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            baseTcpPort: Int = Random.nextInt(from = 10240, until = 65536 - 3),
            stdOutBufferSize: Int = DEFAULT_STDOUT_BUFFER_SIZE,
            stdErrBufferSize: Int = DEFAULT_STDERR_BUFFER_SIZE,
            connectToNativeProcessTimeoutMs: Int = 1000,
            nativeLogs: Boolean = false,
        ): ShellProcess {
            return ShellProcess(
                    stdInSocketPort = baseTcpPort,
                    stdOutSocketPort = baseTcpPort + 1,
                    stdErrSocketPort = baseTcpPort + 2,
                    shellExecFile = ShellInstaller.shellExecutableFile,
                    command = "sh",
                    stdOutBufferSize = stdOutBufferSize,
                    stdErrBufferSize = stdErrBufferSize,
                    connectToNativeProcessTimeoutMs = connectToNativeProcessTimeoutMs,
                    shellProcessVerboseLogs = nativeLogs,
                )
                .also { it.start() }
        }
    }

    // These are for the input socket only
    private lateinit var stdInSocketClient: Socket
    private lateinit var stdInOutputStream: DataOutputStream

    // Whether the process is still active
    private val closedRef = AtomicBoolean()

    // Reader thread for stdout and stderr
    private lateinit var stdOutReadThread: Thread
    private lateinit var stdErrReadThread: Thread

    private val stdOutCircularBuffer = CircularBuffer(size = stdOutBufferSize)
    private val stdErrCircularBuffer = CircularBuffer(size = stdErrBufferSize)

    /**
     * An [InputStream] for the process standard output. Note that this is a circular buffer and is
     * capped by the given [stdOutBufferSize]. When the buffer is full, the kernel has an an
     * additional 64Kb of buffer. When also that is full, the shell process may stop until some
     * buffer becomes a available. If a large output is expected but it's not important to capture
     * it, a shell command may be launched piping in /dev/null.
     *
     * For example:
     * ```
     * cat large_file > /dev/null
     * ```
     */
    public val stdOut: InputStream
        get() = stdOutCircularBuffer.inputStream

    /**
     * An [InputStream] for the process standard error. Note that this is a circular buffer and is
     * capped by the given [stdOutBufferSize]. When the buffer is full, the kernel has an an
     * additional 64Kb of buffer. When also that is full, the shell process may stop until some
     * buffer becomes a available. If a large output is expected but it's not important to capture
     * it, a shell command may be launched piping in /dev/null.
     *
     * For example:
     * ```
     * cat large_file 2> /dev/null
     * ```
     */
    public val stdErr: InputStream
        get() = stdErrCircularBuffer.inputStream

    /** An [OutputStream] for the process standard output. */
    public val stdIn: OutputStream
        get() = stdInOutputStream

    /** Returns whether the process is closed. */
    public fun isClosed(): Boolean = closedRef.get()

    /**
     * Writes a string in the process standard input.
     *
     * @param string a utf-8 string to write in the process stdin.
     */
    public fun writeLine(string: String) {
        stdInOutputStream.write(string.toByteArray(Charsets.UTF_8))
        stdInOutputStream.writeBytes(System.lineSeparator())
    }

    /**
     * Closes the current process. Internally this simply sends the shell process the exit command.
     * This means that the process is not immediately terminated, but gracefully shutdown finishing
     * prior commands execution. Once a shell process is closed, further calls to close don't have
     * any effect, as the [androidx.test.shell.ShellProcess.stdIn] will be closed after the first
     * one.
     */
    override fun close() {
        if (!isClosed()) writeLine("exit")
    }

    private fun start() {
        val cmd =
            setOf(
                    if (shellProcessVerboseLogs) "1" else "0",
                    stdInSocketPort,
                    stdOutSocketPort,
                    stdErrSocketPort,
                    command,
                )
                .joinToString(" ")
                .let { "${shellExecFile.absolutePath} $it" }

        // Runs the command, non blocking.
        uiAutomation.executeShellCommand(cmd)

        // Initializes the input socket, this is also the signal that the process started.
        var retry = 0
        var socket: Socket? = null
        while (socket == null) {
            try {
                socket = Socket(LOCALHOST, stdInSocketPort)
            } catch (e: IOException) {
                // Thrown for connection refused.
                if (++retry > connectToNativeProcessTimeoutMs)
                    throw IOException("Can't connect to shell process.", e)

                // Before retrying to connect wait a bit.
                @SuppressLint("BanThreadSleep") sleep(1)
            }
        }
        stdInSocketClient = socket
        stdInOutputStream = DataOutputStream(socket.outputStream)
        closedRef.set(false)

        // Initializes stdout and stderr sockets and threads to read from those.
        stdOutReadThread =
            asyncReadFromSocket(
                socketPort = stdOutSocketPort,
                circularBuffer = stdOutCircularBuffer,
            )
        stdErrReadThread =
            asyncReadFromSocket(
                socketPort = stdErrSocketPort,
                circularBuffer = stdErrCircularBuffer,
            )
    }

    private fun asyncReadFromSocket(socketPort: Int, circularBuffer: CircularBuffer) = thread {
        val socketAddress = InetSocketAddress(LOCALHOST, socketPort)
        val socket = Socket()

        try {
            socket.connect(socketAddress)
            val dis = socket.inputStream
            val buffer = ByteArray(4096)
            while (socket.isConnected && !closedRef.get()) {
                val read = dis.read(buffer, 0, buffer.size)
                if (read == -1) break
                circularBuffer.outputStream.write(buffer, 0, read)
            }
        } finally {
            socket.close()

            circularBuffer.markClosed()

            // Close also the input stream
            stdInSocketClient.close()
            stdInOutputStream.close()

            // Mark this shell process as closed
            closedRef.set(true)
        }
    }
}
