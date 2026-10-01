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

package androidx.compose.ui.tooling.recomposition

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.tooling.ComposeToolingApi
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.headless.HeadlessApplication
import androidx.compose.ui.desktop.headless.HeadlessWindow
import androidx.compose.ui.desktop.runSession
import androidx.compose.ui.tooling.recomposition.RecompositionInspector.Companion.recomposer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Attaches the inspector and its overlay to a real window running under frame isolation and opens
 * the inspector window, all from outside any composition, the way a Fleet action does.
 */
@OptIn(InternalComposeUiApi::class, ComposeToolingApi::class)
class InspectorHeadlessTest {
    private lateinit var app: HeadlessApplication

    @Before
    fun setUp() {
        app = HeadlessApplication.initialize(System.getProperty("java.io.tmpdir"), frameIsolation = true)
    }

    @After
    fun tearDown() = runBlocking { app.resetForReuse() }

    @Test
    fun overlayAttachesAndStateChangesAreRecordedWithNamesAndBounds() = runBlocking {
        var counter by mutableStateOf(0)
        val ready = CompletableDeferred<HeadlessWindow>()
        val sessionDone = CompletableDeferred<Unit>()
        val sessionJob = launch {
            app.runSession(awaitShutdown = { sessionDone.await() }) {
                Window(onCloseRequest = { }) {
                    RecompositionInspectable {
                        Box { BasicText("count $counter") }
                        LaunchedEffect(Unit) { ready.complete(app.windows.values.single()) }
                    }
                }
            }
        }
        withTimeout(20_000) {
            val inspected = ready.await()
            withContext(ComposeUIDispatcher) { repeat(5) { inspected.render(nanoTime = 1L + it) } }
            val inspector = assertNotNull(RecompositionInspector.of(inspected))
            inspector.settings.publishIntervalNanos = 0

            withContext(ComposeUIDispatcher) {
                inspector.start()
                inspector.isOverlayVisible = true
            }
            app.awaitIdle()
            withContext(ComposeUIDispatcher) { repeat(10) { inspected.render(nanoTime = 10L + it) } }
            app.awaitIdle()
            assertNull(inspected.inspection!!.frameRecomposer.recomposer.asRecomposerInfo().errorState.value)

            withContext(ComposeUIDispatcher) { counter = 1 }
            app.awaitIdle()
            withContext(ComposeUIDispatcher) { repeat(10) { inspected.render(nanoTime = 100L + it) } }
            app.awaitIdle()

            val snapshot = inspector.snapshot.value
            assertTrue(snapshot.frameCount > 0, "no frames recorded")
            assertTrue(snapshot.hasSourceInformation, "source information was not collected; frames=${snapshot.frameCount} scopes=" +
                snapshot.scopes.joinToString { "${it.name}:${it.runCount}" })
            val invalidated = snapshot.scopes.filter { it.invalidators.isNotEmpty() }
            assertTrue(
                invalidated.isNotEmpty(),
                "expected an invalidated scope, got: " + snapshot.scopes.joinToString {
                    "${it.name} runs=${it.runCount} cause=${it.lastRunCause}"
                },
            )
            assertTrue(snapshot.scopes.any { it.name == "BasicText" }, "expected a resolved BasicText scope")
            assertTrue(snapshot.scopes.any { it.bounds != null }, "expected node bounds for the overlay")

            // The inspector window opens from a plain UI-thread callback, outside any composition.
            val inspectorWindow = withContext(ComposeUIDispatcher) { inspector.openInspectorWindow() } as HeadlessWindow
            app.awaitIdle()
            withContext(ComposeUIDispatcher) {
                repeat(10) {
                    inspectorWindow.render(nanoTime = 200L + it)
                    inspected.render(nanoTime = 200L + it)
                }
            }
            app.awaitIdle()
            val inspectorError = inspectorWindow.inspection!!.frameRecomposer.recomposer.asRecomposerInfo().errorState.value
            assertNull(inspectorError, "inspector window failed to compose: ${inspectorError?.cause}")
            assertEquals(2, app.windows.size)
            // The inspector window's own scopes must not show up in the inspected data.
            assertTrue(inspector.snapshot.value.scopes.none { it.name == "RecompositionInspectorPanel" })
            // Actions targeting the inspector window resolve to the inspector that owns it.
            assertEquals(inspector, RecompositionInspector.of(inspectorWindow, app))
            assertEquals(inspector, RecompositionInspector.existing(inspectorWindow))

            // The window's own close button delivers the request inside the scene's frame
            // transaction; the window must still go away.
            withContext(ComposeUIDispatcher) { inspectorWindow.requestClose() }
            app.awaitIdle()
            withContext(ComposeUIDispatcher) { inspected.render(nanoTime = 300L) }
            app.awaitIdle()
            assertEquals(1, app.windows.size, "inspector window still open after requestClose")
            assertNull(RecompositionInspector.existing(inspectorWindow))

            withContext(ComposeUIDispatcher) {
                inspector.closeInspectorWindow()
                inspector.isOverlayVisible = false
                inspector.close()
            }
            app.awaitIdle()
            assertEquals(1, app.windows.size)
        }
        sessionDone.complete(Unit)
        withTimeout(10_000) { sessionJob.join() }
    }
}
