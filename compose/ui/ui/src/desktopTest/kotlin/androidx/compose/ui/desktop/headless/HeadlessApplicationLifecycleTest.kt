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

import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.HeadlessTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class HeadlessApplicationLifecycleTest {
    private fun libraryFolder(): String = System.getProperty("java.io.tmpdir")

    // createHeadlessEventLoop points `skiko.library.path` at libraryFolder(); restore it so later
    // tests in this JVM load skiko natives from the classpath jar again.
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
    fun initializeInstallsTheHeadlessDispatcherAndResetForReuseUninstallsIt() = runBlocking {
        val app = HeadlessApplication.initialize(libraryFolder())
        try {
            assertSame(app, HeadlessApplication.current)
            assertEquals("Dispatchers.HeadlessMain", ComposeUIDispatcher.toString())
        } finally {
            app.resetForReuse()
        }
        // After reset, the platform dispatcher is back and `current` throws the pinned message.
        val failure = assertFailsWith<IllegalStateException> { HeadlessApplication.current }
        assertTrue(failure.message!!.contains("pending tasks"), failure.message)
        assertTrue(ComposeUIDispatcher.toString() != "Dispatchers.HeadlessMain")
    }

    @Test
    fun reinitializeAfterResetReusesTheSameEventLoopAndRevalidatesTheDispatcher() = runBlocking {
        val app = HeadlessApplication.initialize(libraryFolder())
        val loopBefore = app.eventLoop
        app.resetForReuse()
        val again = HeadlessApplication.initialize(libraryFolder())
        try {
            assertSame(loopBefore, again.eventLoop)
            assertEquals("Dispatchers.HeadlessMain", ComposeUIDispatcher.toString())
        } finally {
            again.resetForReuse()
        }
    }

    /**
     * The point of [HeadlessApplication.awaitIdle] over a single dispatch barrier: a task that
     * queues further work must not be reported as quiescence. Here each task enqueues its
     * successor, so one barrier would return with the chain still running.
     */
    @Test
    fun awaitIdleDrainsWorkQueuedByAlreadyQueuedWork() = runBlocking {
        val app = HeadlessApplication.initialize(libraryFolder())
        try {
            val ran = AtomicInteger(0)
            fun enqueueChain(remaining: Int) {
                app.eventLoop.dispatch {
                    ran.incrementAndGet()
                    if (remaining > 1) enqueueChain(remaining - 1)
                }
            }
            enqueueChain(10)
            app.awaitIdle()
            assertEquals(10, ran.get())
        } finally {
            app.resetForReuse()
        }
    }

    @Test
    fun awaitIdleRejectsBeingCalledFromTheEventLoopThread() = runBlocking {
        val app = HeadlessApplication.initialize(libraryFolder())
        try {
            // pendingTasksCount counts the running task, so from the loop thread quiescence is
            // unreachable and a silent one-second timeout would be the only symptom.
            val failure = assertFailsWith<IllegalStateException> {
                withContext(ComposeUIDispatcher) {
                    assertTrue(app.eventLoop.isCurrentThread())
                    app.awaitIdle()
                }
            }
            assertTrue(failure.message!!.contains("event-loop thread"), failure.message)
        } finally {
            app.resetForReuse()
        }
    }

    /**
     * The default budget is a deadlock guard, so it must not be a performance assertion: a caller
     * stepping frames would otherwise fail whenever a machine was slow. This drives a tail far longer
     * than shutdown's 1s budget and expects it to be waited out, then shows a deliberately tight
     * budget still reports.
     */
    @Test
    fun awaitIdleToleratesASlowTailButHonoursAnExplicitBudget() = runBlocking {
        val app = HeadlessApplication.initialize(libraryFolder())
        try {
            app.eventLoop.dispatch { Thread.sleep(1_500) }
            app.awaitIdle() // default budget: must outlast a tail longer than shutdown's

            app.eventLoop.dispatch { Thread.sleep(1_000) }
            val failure = assertFailsWith<IllegalStateException> { app.awaitIdle(timeoutMillis = 50) }
            assertTrue(failure.message!!.contains("did not become idle"), failure.message)
            app.awaitIdle() // let the tail finish so teardown is clean
        } finally {
            app.resetForReuse()
        }
    }

    @Test
    fun clipboardIsInMemoryAndClearedOnReset() = runBlocking {
        val app = HeadlessApplication.initialize(libraryFolder())
        try {
            assertEquals(null, app.getClipEntry())
        } finally {
            app.resetForReuse()
        }
    }
}
