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
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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
