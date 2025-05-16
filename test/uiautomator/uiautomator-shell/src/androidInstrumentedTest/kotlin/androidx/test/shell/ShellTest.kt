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

import androidx.kruth.assertThat
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.shell.utils.asyncDelayed
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Test

@SdkSuppress(minSdkVersion = 23)
@SmallTest
class ShellTest {

    @Test
    fun manyCommands() = withShell {
        val howMany = 100

        // Runs the above echo command to generate 100 outputs
        val list = (0 until howMany).map { command("echo $it").stdOut.text() }

        // Ensure that all the generated outputs are stored in the list
        assertThat(list)
            .containsExactly(*(0 until howMany).map { it.toString() }.toTypedArray())
            .inOrder()
    }

    @Test
    fun command() = withShell {
        val out = command("echo test")
        assertThat(out.stdOut.text()).isEqualTo("test")
        assertThat(out.stdErr.text()).isEqualTo("")
    }

    @Test
    fun multiCommand() = withShell {
        val out = command("echo 1; echo 2; echo 3;")
        assertThat(out.stdOut.text().lines()).containsExactly("1", "2", "3")
        assertThat(out.stdErr.text()).isEqualTo("")
    }

    @Test
    fun emptyCommand() = withShell {
        val out = command("echo")
        assertThat(out.stdOut.text()).isEqualTo("")
        assertThat(out.stdErr.text()).isEqualTo("")
    }

    @Test
    fun multiLineCommand() = withShell {
        val out = command("echo 1; echo 2; echo 3")
        assertThat(out.stdOut.text())
            .isEqualTo(
                """
            |1
            |2
            |3
        """
                    .trimMargin()
            )
        assertThat(out.stdErr.text()).isEqualTo("")
    }

    @Test
    fun nonExistingCommand() = withShell {
        val out = command("""echo "Error message" >&2""")
        assertThat(out.stdErr.text()).isEqualTo("Error message")
        assertThat(out.stdOut.text()).isEqualTo("")
    }

    @Test
    fun multipleTerminalInstances() = withShell {
        val path = "/sdcard/test"

        val output =
            Executors.newSingleThreadExecutor()
                .submit(
                    Callable {
                        Shell.create()
                            .command("while ! [ -f \"$path\" ]; do sleep 1; done && cat \"$path\"")
                    }
                )

        asyncDelayed(1000) { Shell.create().command("echo hello > $path") }

        val out = output.get(5, TimeUnit.SECONDS)
        assertThat(out.stdErr.text()).isEmpty()
        assertThat(out.stdOut.text()).isEqualTo("hello")
    }

    private fun withShell(block: Shell.() -> (Unit)): Unit = Shell.create().use { block(it) }
}
