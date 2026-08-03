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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class) // Modifier.onPointerEvent
@Category(HeadlessTest::class)
class HeadlessEventInjectionTest {
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
    fun injectedClickReachesComposeContent() {
        val received = mutableListOf<PointerEventType>()
        val window = app.createWindow(ApplicationSession(scope)) { }
        window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize()
                    .onPointerEvent(PointerEventType.Press) { received += PointerEventType.Press }
                    .onPointerEvent(PointerEventType.Release) { received += PointerEventType.Release },
            )
        }
        window.render(nanoTime = 1L) // layout must exist before hit testing
        app.sendMouseEnter(window.id, DpOffset(5.dp, 5.dp))
        app.sendMouseDown(window.id, PointerButton.Primary, DpOffset(5.dp, 5.dp))
        app.sendMouseUp(window.id, PointerButton.Primary, DpOffset(5.dp, 5.dp))
        assertEquals(listOf(PointerEventType.Press, PointerEventType.Release), received)
        window.dispose()
    }

    @Test
    fun reuseWindowRebindsTheCloseRequestHandler() {
        val reasons = mutableListOf<String>()
        val window = app.createWindow(ApplicationSession(scope)) { reasons += "first:$it" }
        window.render(nanoTime = 1L)
        val reused = app.reuseWindow(window.id, ApplicationSession(scope)) { reasons += "second:$it" }
        assertTrue(reused === window)
        window.requestClose(WindowCloseRequestReason.UserRequest)
        assertEquals(listOf("second:UserRequest"), reasons)
        window.dispose()
    }

    @Test
    fun injectionToAnUnknownWindowIdIsIgnored() {
        app.sendMouseMove(androidx.compose.ui.desktop.LightweightWindowId(9999), DpOffset.Zero)
        // no exception = pass
    }
}
