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
import java.io.DataInputStream
import org.junit.Test

@SdkSuppress(minSdkVersion = 23)
@SmallTest
class ShellProcessTest {

    @Test
    fun echo() {
        ShellProcess.create().use {
            it.writeLine("echo cat")
            assertThat(it.stdOut.bufferedReader().readLine()).isEqualTo("cat")
        }
    }

    @Test
    fun closeShell() {
        val shell = ShellProcess.create()
        assertThat(shell.isClosed()).isFalse()
        shell.close()

        // Wait at most 5 seconds for shell to close.
        val start = System.currentTimeMillis()
        while (!shell.isClosed() && System.currentTimeMillis() - start < 5000) {
            // Just wait
        }
        assertThat(shell.isClosed()).isTrue()
    }

    @Test
    fun pipe() {
        ShellProcess.create().use {
            it.writeLine("echo cat | sed 's/c/b/g'")
            assertThat(it.stdOut.bufferedReader().readLine()).isEqualTo("bat")
        }
    }

    @Test
    fun streams() {
        ShellProcess.create().use {
            it.writeLine("echo foo > /data/local/tmp/test")
            it.writeLine("cat /data/local/tmp/test")
            assertThat(it.stdOut.bufferedReader().readLine()).isEqualTo("foo")
        }
    }

    @Test
    fun multiline() {
        val shell = ShellProcess.create()
        val reader = shell.stdOut.bufferedReader()

        for (i in 0 until 100) {
            shell.writeLine("echo $i")
            assertThat(reader.readLine()).isEqualTo("$i")

            shell.writeLine("echo a; echo b; echo c;")
            assertThat(reader.readLine()).isEqualTo("a")
            assertThat(reader.readLine()).isEqualTo("b")
            assertThat(reader.readLine()).isEqualTo("c")
        }

        shell.close()
    }

    // Deprecation suppressed because we want to specifically test DataInputStream#readline to
    // ensure compatibility.
    @Suppress("DEPRECATION")
    @Test
    fun multilineWithDataInputStream() {
        val shell = ShellProcess.create()
        val dis = DataInputStream(shell.stdOut)

        for (i in 0 until 100) {
            shell.writeLine("echo $i")
            assertThat(dis.readLine()).isEqualTo("$i")

            shell.writeLine("echo a; echo b; echo c;")
            assertThat(dis.readLine()).isEqualTo("a")
            assertThat(dis.readLine()).isEqualTo("b")
            assertThat(dis.readLine()).isEqualTo("c")
        }

        shell.close()
    }

    @Test
    fun bufferedReaderReadText() {
        val pid =
            ShellProcess.create().use {
                it.writeLine("echo $$ ; exec sleep 10")
                it.stdOut.bufferedReader().readLine().trim().toInt()
            }
        ShellProcess.create().use {
            it.writeLine("kill -SIGINT $pid")
            it.writeLine("exit")
            it.stdOut.bufferedReader().readText()
        }
    }
}
