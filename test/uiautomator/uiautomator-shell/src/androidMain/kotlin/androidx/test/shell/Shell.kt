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

import android.util.Log
import androidx.test.shell.command.ApplicationCommands
import androidx.test.shell.command.PermissionCommands
import androidx.test.shell.command.ProcessCommands
import androidx.test.shell.command.RecorderCommands
import androidx.test.shell.command.ScreenCommands
import androidx.test.shell.command.WifiCommands
import androidx.test.shell.internal.TAG
import java.io.InputStream

/**
 * Allows to execute commands. This class builds on top of [ShellProcess] and abstracts the shell
 * streams to focus on the output of a single command execution. A [ShellProcess] is created for
 * each executed command, using the given factory [shellProcessFactoryBlock].
 */
public object Shell {

    private var shellProcessFactoryBlock: () -> (ShellProcess) = { ShellProcess.create() }

    /** Allows configuring the underlying [ShellProcess] utilized to execute the commands. */
    public fun setShellProcessFactory(factory: () -> (ShellProcess)) {
        this.shellProcessFactoryBlock = factory
    }

    /** Commands for wifi. */
    public fun wifi(): WifiCommands = WifiCommands(shell = this)

    /** Commands for screen. */
    public fun screen(): ScreenCommands = ScreenCommands(shell = this)

    /** Commands for application. */
    public fun application(packageName: String): ApplicationCommands =
        ApplicationCommands(shell = this, packageName = packageName)

    /** Commands for screen recorder. */
    public fun recorder(): RecorderCommands = RecorderCommands(shell = this)

    /** Commands for processes. */
    public fun process(): ProcessCommands = ProcessCommands(shell = this)

    /** Commands for permissions. */
    public fun permission(packageName: String): PermissionCommands =
        PermissionCommands(shell = this, packageName = packageName)

    /** Executes a given command and returns the ongoing [CommandOutput]. */
    public fun command(command: String): CommandOutput {
        val shellProcess = shellProcessFactoryBlock()
        shellProcess.writeLine(command)
        shellProcess.close()
        return CommandOutput(command = command, shellProcess = shellProcess)
    }

    /**
     * The output of a shell command executed via [Shell.command]. This class offers access to the
     * [stdOut] and [stdErr] of the launched command, with utility methods to wait for the full
     * output or parse the stream incrementally.
     */
    public class CommandOutput(
        private val command: String,
        private val shellProcess: ShellProcess,
    ) {

        /** Awaits the process termination and returns the full stdout of the launched command. */
        public val stdOut: String by lazy {
            shellProcess.stdOut.use { it.bufferedReader().readText().trim() }
        }

        /** Awaits the process termination and returns the full stderr of the launched command. */
        public val stdErr: String by lazy {
            shellProcess.stdErr.use { it.bufferedReader().readText().trim() }
        }

        /** Allows incrementally consuming the stdout of a single command as an [InputStream]. */
        public fun <T> stdOutStream(block: InputStream.() -> (T)): T =
            shellProcess.stdOut.use(block)

        /** Allows incrementally consuming the stderr of a single command as an [InputStream]. */
        public fun <T> stdErrStream(block: InputStream.() -> (T)): T =
            shellProcess.stdErr.use(block)

        /** Allows incrementally consuming the stdout of the launched command as lines. */
        public fun stdOutLines(): Sequence<String> = stdOutStream {
            bufferedReader().lineSequence()
        }

        /** Allows incrementally consuming the stderr of the launched command as lines. */
        public fun stdErrLines(): Sequence<String> = stdErrStream {
            bufferedReader().lineSequence()
        }

        internal fun String.assertEmpty() {
            if (isNotBlank()) throwWithCommandOutput()
        }

        internal fun String.assertNoFailure(
            vararg failureStrings: String = arrayOf("Error", "Failed", "Failure")
        ) {
            if (failureStrings.any { w -> contains(w, ignoreCase = true) }) throwWithCommandOutput()
        }

        internal fun throwWithCommandOutput() {
            val message = stdOutStdErrCommandOutput()
            message.lines().forEach { Log.e(TAG, it) }
            throw IllegalStateException(message)
        }

        internal fun stdOutStdErrCommandOutput(): String {
            val errorMsgLines =
                listOfNotNull(
                    "Fatal exception, command failed: `$command`",
                    *(if (stdOut.isNotBlank()) arrayOf("Stdout:", stdOut) else emptyArray()),
                    *(if (stdErr.isNotBlank()) arrayOf("StdErr:", stdErr) else emptyArray()),
                )
            return errorMsgLines.joinToString(separator = System.lineSeparator())
        }
    }
}
