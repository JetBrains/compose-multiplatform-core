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

package androidx.test.shell.internal

import androidx.kruth.assertThat
import androidx.test.filters.SmallTest
import androidx.test.shell.utils.asyncDelayed
import androidx.test.shell.utils.writeLine
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Before
import org.junit.Test

// Suppressing deprecations for DataInputStream#readline because we want to ensure compatibility
// with that api.
@Suppress("DEPRECATION")
@SmallTest
class CircularBufferTest {

    private lateinit var streams: CircularBuffer
    private lateinit var reader: BufferedReader

    @Before
    fun setup() {
        streams = CircularBuffer(size = 1024)
        reader = streams.inputStream.bufferedReader()
    }

    @Test
    fun dataInputStreamWriteLine() {
        val reader = DataInputStream(streams.inputStream)
        streams.writeLine("hello")
        assertThat(reader.readLine()).isEqualTo("hello")
    }

    @Test
    fun circularBuffer() {
        streams = CircularBuffer(size = 3)
        val dos = DataOutputStream(streams.outputStream)
        val dis = DataInputStream(streams.inputStream)

        fun write(vararg values: Int) = values.forEach { dos.write(it) }
        fun read(vararg values: Int) = values.forEach { assertThat(dis.read()).isEqualTo(it) }

        // Initial condition
        assertThat(streams.readPtr).isEqualTo(0)
        assertThat(streams.writePtr).isEqualTo(0)
        assertThat(streams.count).isEqualTo(0)

        write(0, 1)
        assertThat(streams.writePtr).isEqualTo(2)
        assertThat(streams.count).isEqualTo(2)

        read(0, 1)
        assertThat(streams.readPtr).isEqualTo(2)
        assertThat(streams.count).isEqualTo(0)

        // This flips the pointers
        write(2, 3, 4)
        assertThat(streams.writePtr).isEqualTo(2)
        assertThat(streams.count).isEqualTo(3)

        // Since the ptr are in the same position and the buffer is flipped, the write operation
        // will wait for the read ptr to move forward. We perform a delayed read so test this
        // behavior.
        asyncDelayed { read(2) }
        write(5)
        assertThat(streams.count).isEqualTo(3)
        assertThat(streams.writePtr).isEqualTo(0)
        assertThat(streams.readPtr).isEqualTo(0)

        // Now read all the elements of the circular buffer
        read(3, 4, 5)
        assertThat(streams.count).isEqualTo(0)
        assertThat(streams.writePtr).isEqualTo(0)
        assertThat(streams.readPtr).isEqualTo(0)

        // The next read is blocking so we make sure to start a delayed write
        asyncDelayed { write(6) }
        read(6)

        assertThat(streams.count).isEqualTo(0)
        assertThat(streams.writePtr).isEqualTo(1)
        assertThat(streams.readPtr).isEqualTo(1)
    }

    @Test
    fun singleLine() {
        streams.writeLine("hello")
        assertThat(reader.readLine()).isEqualTo("hello")
    }

    @Test
    fun multiLine() {
        streams.writeLine("cat")
        streams.writeLine("home")
        streams.writeLine("house")

        assertThat(reader.readLine()).isEqualTo("cat")
        assertThat(reader.readLine()).isEqualTo("home")
        assertThat(reader.readLine()).isEqualTo("house")
    }

    @Test
    fun readLineWhenDataIsNotAvailable() {
        // Wait a little so we ensure readLine is called first
        asyncDelayed(500) { streams.writeLine("hello") }

        assertThat(reader.readLine()).isEqualTo("hello")
    }
}
