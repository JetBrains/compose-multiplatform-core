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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.runSession
import androidx.compose.ui.platform.withInfiniteAnimationFrameNanos
import androidx.compose.ui.scene.ComposeSceneFeatureFlags
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(HeadlessTest::class)
class HeadlessNoWindowAnimationTest {
    // The no-window animation policy is flag-independent, so a single (flag=false) run suffices.
    // The frame-isolation flag is still saved/restored defensively, per headless-venue convention.
    private var flagBefore = false
    private lateinit var app: HeadlessApplication

    @Before fun setUp() {
        flagBefore = ComposeSceneFeatureFlags.isFrameIsolationEnabled
        ComposeSceneFeatureFlags.isFrameIsolationEnabled = false
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
    fun infiniteAnimationInAWindowlessSessionSuspendsUntilAWindowAppears() = runBlocking {
        var showWindow by mutableStateOf(false)
        val infiniteBodyRan = CompletableDeferred<Unit>()
        val sessionDone = CompletableDeferred<Unit>()
        val job = launch {
            app.runSession(awaitShutdown = { sessionDone.await() }) {
                LaunchedEffect(Unit) {
                    withInfiniteAnimationFrameNanos { }   // must NOT run while windowless
                    infiniteBodyRan.complete(Unit)
                }
                if (showWindow) Window(onCloseRequest = { }) { }
            }
        }
        withTimeout(10_000) {
            // Give the session time to compose; the infinite-animation body must stay suspended.
            kotlinx.coroutines.delay(500)
            assertTrue(!infiniteBodyRan.isCompleted, "windowless session must gate infinite animations")
            showWindow = true
            infiniteBodyRan.await()   // wakes via snapshotFlow once windows becomes non-empty
        }
        sessionDone.complete(Unit); withTimeout(10_000) { job.join() }
    }

    @Test
    fun motionDurationScaleIsZeroWhileWindowlessAndOneWithAWindow() = runBlocking {
        var showWindow by mutableStateOf(false)
        val windowlessScale = CompletableDeferred<Float>()
        val windowedScale = CompletableDeferred<Float>()
        val sessionDone = CompletableDeferred<Unit>()
        val job = launch {
            app.runSession(awaitShutdown = { sessionDone.await() }) {
                LaunchedEffect(Unit) {
                    val scale = coroutineContext[MotionDurationScale]
                        ?: error("runSession installs no MotionDurationScale")
                    // Windowless phase: motion collapses to a single frame (scaleFactor 0f).
                    windowlessScale.complete(scale.scaleFactor)
                    // scaleFactor reads the snapshot-backed windows map, so this wakes once a
                    // window attaches — the load-bearing positive assertion.
                    snapshotFlow { scale.scaleFactor }.first { it == 1f }
                    windowedScale.complete(scale.scaleFactor)
                }
                if (showWindow) Window(onCloseRequest = { }) { }
            }
        }
        withTimeout(10_000) {
            assertEquals(0f, windowlessScale.await(), "windowless session runs motion at scale 0f")
            showWindow = true
            assertEquals(1f, windowedScale.await(), "a windowed session runs motion at scale 1f")
        }
        sessionDone.complete(Unit); withTimeout(10_000) { job.join() }
    }
}
