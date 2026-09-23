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

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import java.awt.datatransfer.StringSelection
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Regression coverage for the headless backend's clipboard wiring.
 *
 * `RootNodeOwner` used to build its clipboard eagerly, in its constructor, by calling the
 * OS-specific factory directly. On Linux that resolves to `GtkApplication.current()`, and
 * `GtkApplication` is never initialized under this backend — every headless window failed to
 * construct with `IllegalStateException: GtkApplication has not been initialized`, before
 * `setContent` ever ran. `RootNodeOwner` now reads `platformContext.clipboard`, which this
 * backend overrides with `HeadlessApplication`'s own in-memory `Clipboard`.
 *
 * Constructing the window and reaching `setContent` without throwing is most of this test; the
 * round trip on top proves the clipboard is actually functional, not just absent.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Category(HeadlessTest::class)
class HeadlessClipboardTest {
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
    fun clipboardRoundTripsThroughTheHeadlessBackend() = runBlocking {
        val window = app.createWindow(ApplicationSession(scope)) { }
        lateinit var clipboard: Clipboard
        window.setContent(onPreviewKeyEvent = { false }, onKeyEvent = { false }) {
            clipboard = LocalClipboard.current
            Box(Modifier)
        }
        withContext(ComposeUIDispatcher) { window.render(nanoTime = 1L) }

        assertNull(clipboard.getClipEntry())

        val entry = ClipEntry(StringSelection("hello headless world"))
        clipboard.setClipEntry(entry)
        assertEquals(entry, clipboard.getClipEntry())

        clipboard.setClipEntry(null)
        assertNull(clipboard.getClipEntry())

        window.dispose()
    }
}
