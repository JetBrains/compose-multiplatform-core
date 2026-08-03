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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.runSession
import androidx.compose.ui.scene.ComposeSceneFeatureFlags
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@Category(HeadlessTest::class)
@RunWith(Parameterized::class)
class HeadlessSessionTest(private val frameIsolation: Boolean) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "frameIsolation={0}")
        fun flags() = listOf(false, true)
    }

    private var flagBefore = false
    private lateinit var app: HeadlessApplication

    @Before fun setUp() {
        flagBefore = ComposeSceneFeatureFlags.isFrameIsolationEnabled
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = frameIsolation
        app = HeadlessApplication.initialize(System.getProperty("java.io.tmpdir"))
    }

    @After fun tearDown() = runBlocking {
        try {
            app.resetForReuse()
        } finally {
            ComposeSceneFeatureFlags.isFrameIsolationEnabled = flagBefore
        }
    }

    @Test
    fun aWindowDeclaredAfterSessionStartAppearsAndDisappearsWithItsState() = runBlocking {
        var showWindow by mutableStateOf(false)
        val windowSeen = CompletableDeferred<Unit>()
        val sessionDone = CompletableDeferred<Unit>()
        val sessionJob = launch {
            app.runSession(awaitShutdown = { sessionDone.await() }) {
                if (showWindow) {
                    Window(onCloseRequested = { }) { }
                    LaunchedEffect(Unit) { windowSeen.complete(Unit) }
                }
            }
        }
        withTimeout(10_000) {
            // Session composes with zero windows first.
            while (app.windows.isNotEmpty()) kotlinx.coroutines.yield()
            showWindow = true
            windowSeen.await()
            assertEquals(1, app.windows.size)
            showWindow = false
            while (app.windows.isNotEmpty()) kotlinx.coroutines.yield()
        }
        sessionDone.complete(Unit)
        withTimeout(10_000) { sessionJob.join() }
        assertTrue(app.windows.isEmpty())
    }

    @Test
    fun aStateChangeInvalidatesTheWindowSceneAndRenderClearsIt() = runBlocking {
        var color by mutableStateOf(0)
        val ready = CompletableDeferred<HeadlessWindow>()
        val sessionDone = CompletableDeferred<Unit>()
        val sessionJob = launch {
            app.runSession(awaitShutdown = { sessionDone.await() }) {
                Window(onCloseRequested = { }) {
                    @Suppress("UNUSED_EXPRESSION") color
                    LaunchedEffect(Unit) {
                        ready.complete(app.windows.values.single())
                    }
                }
            }
        }
        withTimeout(10_000) {
            val window = ready.await()
            window.render(nanoTime = 1L)
            while (window.isFrameRequested) { window.render(nanoTime = 2L) }
            color = 1
            // The write must eventually mark the scene dirty…
            while (!window.isFrameRequested) kotlinx.coroutines.yield()
            // …and rendering consumes the request.
            window.render(nanoTime = 3L)
        }
        sessionDone.complete(Unit)
        withTimeout(10_000) { sessionJob.join() }
    }
}
