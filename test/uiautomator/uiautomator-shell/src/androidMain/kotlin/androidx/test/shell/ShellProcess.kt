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
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.shell.internal.ShellInstaller
import androidx.test.shell.internal.TAG
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

private const val LOCALHOST = "localhost"

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
) : AutoCloseable {

    public companion object {

        /**
         * Creates a new [ShellProcess].
         *
         * Shell process connects to an internal native utility via tcp. The given base port and the
         * two following ones are used.
         *
         * @param baseTcpPort a base tcp port to use to communicate with the native utility. Note
         *   that the given port and the 2 following ones are used.
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
            connectToNativeProcessTimeoutMs: Int = 1000,
            nativeLogs: Boolean = false,
        ): ShellProcess {
            return ShellProcess(
                    stdInSocketPort = baseTcpPort,
                    stdOutSocketPort = baseTcpPort + 1,
                    stdErrSocketPort = baseTcpPort + 2,
                    shellExecFile = ShellInstaller.shellExecutableFile,
                    command = "sh",
                    connectToNativeProcessTimeoutMs = connectToNativeProcessTimeoutMs,
                    shellProcessVerboseLogs = nativeLogs,
                )
                .also { it.start() }
        }
    }

    // These are for the input socket only
    private lateinit var stdInSocketClient: Socket
    private lateinit var stdInOutputStream: DataOutputStream

    // This repeater reads from the socket on stdOutSocketPort and copies into this instance object
    // pipe. The data stream can be read accessing the associated input stream.
    private val stdOutSocketToPipeRepeater =
        SocketToPipeRepeater(
            streamName = "stdout",
            port = stdOutSocketPort,
            onClose = ::cleanUpStreams,
        )

    // This repeater reads from the socket on stdErrSocketPort and copies into this instance object
    // pipe. The data stream can be read accessing the associated input stream.
    private val stdErrSocketToPipeRepeater =
        SocketToPipeRepeater(
            streamName = "stderr",
            port = stdErrSocketPort,
            onClose = ::cleanUpStreams,
        )

    /**
     * An [InputStream] for the process standard output. Note that this is backed by a tcp
     * connection to the native cli utility. If a large output is expected but it's not important to
     * capture it, a shell command may be launched piping in /dev/null.
     *
     * For example:
     * ```
     * cat large_file > /dev/null
     * ```
     */
    public val stdOut: InputStream
        get() = stdOutSocketToPipeRepeater.inputStream

    /**
     * An [InputStream] for the process standard error. Note that this is backed by a tcp connection
     * to the native cli utility. If a large output is expected but it's not important to capture
     * it, a shell command may be launched piping in /dev/null.
     *
     * For example:
     * ```
     * cat large_file 2> /dev/null
     * ```
     */
    public val stdErr: InputStream
        get() = stdErrSocketToPipeRepeater.inputStream

    /** An [OutputStream] for the process standard output. */
    public val stdIn: OutputStream
        get() = stdInOutputStream

    /** Returns whether the process is closed. */
    public fun isClosed(): Boolean = stdOutSocketToPipeRepeater.isClosed()

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
     * any effect, as the [stdIn] will be closed after the first one.
     */
    override fun close() {
        if (!isClosed()) {
            try {
                stdInOutputStream.write(
                    "exit ${System.lineSeparator()}".toByteArray(Charsets.UTF_8)
                )
            } catch (_: IOException) {
                // This may throw when there are multiple rapid calls to close() so that
                // `isClosed` returns false but right before writing in the stream, the socket
                // actually closes.
                // Nothing do do here anyway, the socket was already closing and the fact the
                // IOException is throws means the stream is no more accessible.
            }
        }
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

        // Initializes stdout and stderr sockets and threads to read from those.
        stdOutSocketToPipeRepeater.start()
        stdErrSocketToPipeRepeater.start()
    }

    private fun cleanUpStreams() {
        stdInOutputStream.close()
        stdOutSocketToPipeRepeater.close()
        stdErrSocketToPipeRepeater.close()
    }
}

/**
 * Internal implementation that reads from a tcp socket on localhost:[port] and writes into a pipe
 * created with [ParcelFileDescriptor.createPipe]. The pipe read side is exposed as [inputStream]
 * for consumption. This adds a layer on top of the socket tcp stream that allows gracefully handle
 * cases where the tcp socket would throw an exception, like in the case of an IO exception.
 * Additionally it allows to finish reading the process output even after the socket is closed (as
 * that's copied into the pipe).
 */
private class SocketToPipeRepeater(
    private val streamName: String,
    private val port: Int,
    private val onClose: () -> (Unit),
) {

    val inputStream: InputStream

    private val runningRef: AtomicBoolean = AtomicBoolean(false)
    private val readFd: ParcelFileDescriptor
    private val writeFd: ParcelFileDescriptor
    private val outputStream: OutputStream

    init {
        val (readFd, writeFd) = ParcelFileDescriptor.createPipe()

        this.readFd = readFd
        this.inputStream = ParcelFileDescriptor.AutoCloseInputStream(readFd)

        this.writeFd = writeFd
        this.outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writeFd)
    }

    /**
     * Sets the running flag to off signaling the copy thread to stop. This doesn't happen
     * immediately and it's async to the method call.
     */
    fun close() = runningRef.set(false)

    /**
     * Connects to a localhost socket and starts copying from it into a pipe. The pipe can be read
     * from [inputStream].
     */
    fun start() {

        // Ensure this has been called only once.
        if (runningRef.getAndSet(true)) return

        val socketAddress = InetSocketAddress(LOCALHOST, port)
        val socket = Socket().apply { connect(socketAddress) }
        thread(name = "${streamName}SocketToPipeRepeaterThread") {
            try {
                val socketInputStream = socket.inputStream
                val buffer = ByteArray(8192)
                while (socket.isConnected && runningRef.get()) {
                    val read = socketInputStream.read(buffer, 0, buffer.size)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                }
            } catch (e: IOException) {
                // Can happen if the socket closes abruptly or there is a connection error
                Log.e(TAG, "$streamName Error reading $streamName", e)
            } finally {
                socket.close()
                outputStream.close()
                runningRef.set(false)
                onClose()
            }
        }
    }

    /** Returns the state of the running flag. */
    fun isClosed(): Boolean = !runningRef.get()
}
