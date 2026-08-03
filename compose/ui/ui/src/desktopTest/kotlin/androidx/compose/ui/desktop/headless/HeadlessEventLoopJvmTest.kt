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

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.HeadlessTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class HeadlessEventLoopJvmTest {
    // createHeadlessEventLoop points `skiko.library.path` at the tmpdir passed below; restore it
    // so later tests in this JVM load skiko natives from the classpath jar again.
    private var skikoLibraryPathBefore: String? = null

    @Before
    fun rememberSkikoLibraryPath() {
        skikoLibraryPathBefore = System.getProperty("skiko.library.path")
    }

    @After
    fun restoreSkikoLibraryPath() {
        val before = skikoLibraryPathBefore
        if (before == null) {
            System.clearProperty("skiko.library.path")
        } else {
            System.setProperty("skiko.library.path", before)
        }
    }

    @Test
    fun pendingTasksCountIsNonZeroWhileATaskBodyRuns() {
        val loop = createHeadlessEventLoop(libraryFolderPath = System.getProperty("java.io.tmpdir"))
        try {
            val taskEntered = CountDownLatch(1)
            val releaseTask = CountDownLatch(1)
            loop.dispatch {
                taskEntered.countDown()
                releaseTask.await(10, TimeUnit.SECONDS)
            }
            assertTrue(taskEntered.await(10, TimeUnit.SECONDS))
            // The task is RUNNING: it must still be counted, or an idle barrier
            // taken now would wrongly report the loop as drained.
            assertEquals(1, loop.pendingTasksCount)
            releaseTask.countDown()
            val barrier = CountDownLatch(1)
            loop.dispatch { barrier.countDown() }
            assertTrue(barrier.await(10, TimeUnit.SECONDS))
            assertEquals(0, loop.pendingTasksCount)
        } finally {
            loop.close(dropPendingTasks = true)
        }
    }

    @Test
    fun isCurrentThreadIsTrueOnlyInsideLoopTasks() {
        val loop = createHeadlessEventLoop(libraryFolderPath = System.getProperty("java.io.tmpdir"))
        try {
            assertFalse(loop.isCurrentThread())
            var insideLoop = false
            val done = CountDownLatch(1)
            loop.dispatch {
                insideLoop = loop.isCurrentThread()
                done.countDown()
            }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(insideLoop)
        } finally {
            loop.close(dropPendingTasks = true)
        }
    }
}
