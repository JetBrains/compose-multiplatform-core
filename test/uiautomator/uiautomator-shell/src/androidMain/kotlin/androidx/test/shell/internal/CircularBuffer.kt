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

import android.annotation.SuppressLint
import java.io.InputStream
import java.io.OutputStream
import java.lang.Thread.sleep

/**
 * Allows creating a circular buffer that can be written and read through input [inputStream] and
 * output [outputStream]. Both write and read operations pause if the circular buffer is full. There
 * are no locks of [outputStream] and [inputStream] can be accessed by different threads, as long as
 * they are always the same, i.e. 1 producer thread, 1 consumer thread.
 */
internal class CircularBuffer(private val size: Int) {

    // countRead and countWritten are written by separate threads so we cannot use a single var
    private var countRead: UInt = 0u
    private var countWritten: UInt = 0u
    private val buffer: ByteArray = ByteArray(size)

    // These are visible for testing
    var readPtr = 0
    var writePtr = 0
    val count
        get() = (countWritten - countRead).toInt()

    private var closed = false

    fun markClosed() {
        closed = true
    }

    val inputStream =
        object : InputStream() {
            override fun read(): Int {

                // By specification, this is a blocking method that blocks until it's possible
                // to perform a read. In order to determine if we can read we can simply check
                // how far behind is the read pointer compared to the write pointer.
                while (count <= 0) {
                    @SuppressLint("BanThreadSleep") sleep(1)
                    if (closed) return -1
                }

                val b = buffer[readPtr++].toInt() and 0xFF
                readPtr = readPtr % size
                countRead++

                return b
            }

            override fun close() = markClosed()

            override fun read(b: ByteArray, offset: Int, len: Int): Int {
                if (len < 0 || b.isEmpty()) return 0

                // Wait for at least 1 byte to be available. This is also a blocking method by
                // specification.
                while (count <= 0) {
                    @SuppressLint("BanThreadSleep") sleep(1)
                    if (closed) return -1
                }

                val bytesToRead = minOf(len, available())
                if (bytesToRead <= 0) return -1

                System.arraycopy(buffer, readPtr, b, offset, bytesToRead)

                readPtr += bytesToRead
                countRead += bytesToRead.toUInt()
                readPtr = readPtr % size

                return bytesToRead
            }

            override fun available(): Int = if (count > 0) count else if (closed) -1 else 0
        }

    val outputStream =
        object : OutputStream() {
            override fun write(b: Int) {

                // Wait for buffer to be available. This requires waiting if the buffer is full.
                while (count >= buffer.size) {
                    @SuppressLint("BanThreadSleep") sleep(1)
                    if (closed) return
                }

                buffer[writePtr++] = b.toByte()
                writePtr = writePtr % size
                countWritten++
            }

            override fun write(b: ByteArray, offset: Int, len: Int) {
                if (len < 0 || b.isEmpty()) return

                // Wait for enough buffer to be available.
                // This requires waiting if the buffer is full.
                while (count >= buffer.size - len) {
                    @SuppressLint("BanThreadSleep") sleep(1)
                    if (closed) return
                }

                System.arraycopy(b, offset, buffer, writePtr, len)

                writePtr += len
                countWritten += len.toUInt()
                writePtr = writePtr % size
            }
        }
}
