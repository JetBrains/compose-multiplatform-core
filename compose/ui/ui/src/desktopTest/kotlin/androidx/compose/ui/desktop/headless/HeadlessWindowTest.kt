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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.scene.ComposeSceneFeatureFlags
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class HeadlessWindowTest {
    private lateinit var app: HeadlessApplication
    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        app = HeadlessApplication.initialize(System.getProperty("java.io.tmpdir"))
        scope = CoroutineScope(SupervisorJob())
    }

    @After fun tearDown() = runBlocking {
        scope.cancel()
        app.resetForReuse()
    }

    @Test
    fun windowRegistersRendersContentAndDisposesIdempotently() {
        val window = app.createWindow(ApplicationSession(scope)) { }
        assertTrue(app.windows.containsKey(window.id))
        window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Red))
        }
        assertTrue(window.isFrameRequested)
        window.render(nanoTime = 1L)
        assertFalse(window.isFrameRequested)
        val shot = window.captureScreenshot()
        val pixels = shot.toPixelMap()
        assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
        window.dispose()
        assertFalse(app.windows.containsKey(window.id))
        window.dispose() // idempotent
    }

    /**
     * With frame isolation on, the scene pins a snapshot when its frame domain activates during
     * construction. Any of the window's own snapshot state declared after the scene would be
     * invisible in that snapshot, and the first `setContent` — which reads it — would throw. No
     * priming render here on purpose: `setContent` must be legal on a freshly created window.
     */
    @Test
    fun setContentWorksOnAFreshWindowWhenFrameIsolationIsEnabled() = runBlocking {
        val previous = ComposeSceneFeatureFlags.isFrameIsolationEnabled
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = true
        try {
            val window = app.createWindow(ApplicationSession(scope)) { }
            window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Red))
            }
            // With isolation on, setContent schedules one catch-up frame on the event loop rather
            // than composing synchronously, so the content arrives with that frame, not with a
            // hand-driven render.
            app.awaitIdle()
            val pixels = window.captureScreenshot().toPixelMap()
            assertEquals(Color.Red, pixels[pixels.width / 2, pixels.height / 2])
            window.dispose()
        } finally {
            ComposeSceneFeatureFlags.isFrameIsolationEnabled = previous
        }
    }

    /**
     * A window with no display link must never invent a frame time.
     *
     * Reachable through [captureScreenshot], which renders on demand when nothing has rendered yet.
     * With a wall-clock default that frame lands around `System.nanoTime()`, and a caller driving a
     * virtual clock then sees it jump backwards on its own next render — which makes every elapsed
     * time the content computes garbage.
     */
    @Test
    fun aSelfScheduledFrameDoesNotInventAWallClockTime() = runBlocking {
        val frameTimes = mutableListOf<Long>()
        val window = app.createWindow(ApplicationSession(scope)) { }
        window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameNanos { frameTimes.add(it) }
                }
            }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize())
        }
        // Renders on its own because no frame has happened yet: the frame time is the window's to
        // choose, and it must not choose the wall clock.
        withContext(ComposeUIDispatcher) { window.captureScreenshot() }
        app.awaitIdle()
        for (frame in 1..3) {
            withContext(ComposeUIDispatcher) { window.render(nanoTime = frame * 16_000_000L) }
            app.awaitIdle()
        }
        // On the loop thread: the content is suspended in withFrameNanos inside the scene's snapshot,
        // and disposing from the test thread trips "cannot dispose while a child snapshot is open".
        withContext(ComposeUIDispatcher) { window.dispose() }

        assertTrue(frameTimes.size >= 3, "expected the driven frames to be observed: $frameTimes")
        assertEquals(frameTimes.sorted(), frameTimes, "frame clock went backwards: $frameTimes")
        assertTrue(
            frameTimes.all { it <= 48_000_000L },
            "a frame time exceeded the virtual clock, so wall-clock time leaked in: $frameTimes",
        )
    }

    @Test
    fun requestCloseDeliversTheReasonToTheHandler() {
        var reason: WindowCloseRequestReason? = null
        val window = app.createWindow(ApplicationSession(scope)) { reason = it }
        window.render(nanoTime = 1L)
        window.requestClose(WindowCloseRequestReason.ApplicationQuit)
        assertEquals(WindowCloseRequestReason.ApplicationQuit, reason)
        window.dispose()
    }
}
