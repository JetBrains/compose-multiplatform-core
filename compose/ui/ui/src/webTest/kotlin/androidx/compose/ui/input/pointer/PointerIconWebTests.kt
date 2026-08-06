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

package androidx.compose.ui.input.pointer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.w3c.dom.pointerevents.PointerEvent
import org.w3c.dom.pointerevents.PointerEventInit

/**
 * Tests for `PointerIcon(keyword)` on the web target.
 *
 * The factory itself is a thin wrapper around [BrowserCursor], so the tests focus on the
 * user-visible contract: hovering an element annotated with
 * `Modifier.pointerHoverIcon(PointerIcon(<keyword>))` must set
 * `HTMLCanvasElement.style.cursor` to `<keyword>` via
 * `ComposeWindowInternal.web.kt#setPointerIcon`.
 *
 * A tiny unit-level assertion locks the factory API surface ([BrowserCursor] is `internal`, so
 * it is only reachable from the same module).
 */
@OptIn(ExperimentalComposeUiApi::class)
class PointerIconWebTests : OnCanvasTests {

    @Test
    fun returnsBrowserCursor_withGivenId() {
        val icon = PointerIcon("grab")
        assertTrue(icon is BrowserCursor, "Expected BrowserCursor, got ${icon::class}")
        assertEquals("grab", icon.id)
    }


    @Test
    fun setsCanvasCursor_forGrabKeyword() = runTest {
        assertHoverSetsCursor(keyword = "grab", expected = "grab")
    }

    @Test
    fun setsCanvasCursor_forZoomInKeyword() = runTest {
        assertHoverSetsCursor(keyword = "zoom-in", expected = "zoom-in")
    }

    @Test
    fun setsCanvasCursor_forHelpKeyword() = runTest {
        assertHoverSetsCursor(keyword = "help", expected = "help")
    }

    @Test
    fun setsCanvasCursor_forColResizeKeyword() = runTest {
        assertHoverSetsCursor(keyword = "col-resize", expected = "col-resize")
    }

    @Test
    fun setsCanvasCursor_forNotAllowedKeyword() = runTest {
        assertHoverSetsCursor(keyword = "not-allowed", expected = "not-allowed")
    }

    @Test
    fun setsCanvasCursor_forUrlCustomImage() = runTest {
        val cssValue = "url(\"$SVG_DATA_URL\"), auto"

        createComposeWindow {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(PointerIcon(cssValue))
            )
        }
        hoverCanvas()

        // Browsers may normalize quoting/whitespace on `style.cursor` for url() values.
        // Assert structurally rather than requiring exact byte-for-byte equality.
        val actual = getCanvas().style.cursor
        assertTrue(
            actual.startsWith("url("),
            "Expected style.cursor to start with url(...); was: $actual",
        )
        assertTrue(
            actual.endsWith(", auto") || actual.endsWith(",auto"),
            "Expected style.cursor to end with a `, auto` fallback; was: $actual",
        )
    }

    @Test
    fun setsCanvasCursor_forUrlCustomImage_withHotspot() = runTest {
        val cssValue = "url(\"$SVG_DATA_URL\") 16 16, pointer"

        createComposeWindow {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(PointerIcon(cssValue))
            )
        }
        hoverCanvas()

        val actual = getCanvas().style.cursor
        assertTrue(
            actual.startsWith("url("),
            "Expected style.cursor to start with url(...); was: $actual",
        )
        assertTrue(
            "16 16" in actual,
            "Expected style.cursor to preserve `16 16` hotspot; was: $actual",
        )
        assertTrue(
            actual.endsWith(", pointer") || actual.endsWith(",pointer"),
            "Expected style.cursor to end with a `, pointer` fallback; was: $actual",
        )
    }

    @Test
    fun pointerHoverIcon_updatesCanvasCursor_whenIconChanges() = runApplicationTest {
        var keyword by mutableStateOf("grab")

        createComposeWindow {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(PointerIcon(keyword))
            )
        }
        hoverCanvas()
        assertEquals("grab", getCanvas().style.cursor)

        keyword = "crosshair"
        // Wait for the state change to propagate through recomposition and layout
        // before re-hovering, otherwise `Modifier.pointerHoverIcon` still holds the old icon.
        awaitIdle()
        awaitAnimationFrame()

        // Re-hover to trigger the pointer icon resolution pipeline after recomposition.
        hoverCanvas()
        assertEquals("crosshair", getCanvas().style.cursor)
    }


    @Test
    fun builtInHandAlias_mapsTo_pointerCssKeyword() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Hand))
        }
        hoverCanvas()

        // On web, `PointerIcon.Hand` is backed by `BrowserCursor("pointer")` — see
        // `PointerIcon.web.kt` (`pointerIconHand`).
        assertEquals("pointer", getCanvas().style.cursor)
    }

    @Test
    fun builtInTextAlias_mapsTo_textCssKeyword() = runTest {
        createComposeWindow {
            Box(Modifier.fillMaxSize().pointerHoverIcon(PointerIcon.Text))
        }
        hoverCanvas()

        assertEquals("text", getCanvas().style.cursor)
    }

    private suspend fun assertHoverSetsCursor(keyword: String, expected: String) {
        createComposeWindow {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerHoverIcon(PointerIcon(keyword))
            )
        }
        hoverCanvas()
        assertEquals(
            expected,
            getCanvas().style.cursor,
            "keyword=$keyword should set canvas.style.cursor to $expected",
        )
    }

    /**
     * Dispatches a synthetic `pointerenter` + `pointermove` pair on the canvas so that
     * `Modifier.pointerHoverIcon` resolves and `ComposeWindowInternal.setPointerIcon`
     * writes to `canvas.style.cursor`.
     */
    private fun hoverCanvas(clientX: Int = 10, clientY: Int = 10) {
        dispatchEvents(
            PointerEvent(
                "pointerenter",
                PointerEventInit(clientX = clientX, clientY = clientY, pointerType = "mouse"),
            ),
            PointerEvent(
                "pointermove",
                PointerEventInit(clientX = clientX, clientY = clientY, pointerType = "mouse"),
            ),
        )
    }

    private companion object {
        /**
         * The same inline SVG data URL used by the mpp demo's web `PointerIconExample`.
         * Kept inline so the test does not depend on any external asset.
         */
        const val SVG_DATA_URL: String =
            "data:image/svg+xml;utf8," +
                "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'>" +
                "<circle cx='16' cy='16' r='12' fill='%23ff5722' stroke='white' stroke-width='2'/>" +
                "<circle cx='16' cy='16' r='3' fill='white'/>" +
                "</svg>"
    }
}
