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

package androidx.compose.ui.desktop.windows

import androidx.compose.ui.HeadlessTest
import androidx.compose.ui.desktop.CaptionButtonKind
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.jetbrains.desktop.win32.LogicalPoint
import org.jetbrains.desktop.win32.LogicalSize
import org.jetbrains.desktop.win32.NCHitTestResult
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * Pure-JVM coverage for the custom-title-bar WM_NCHITTEST routing math (`hitTestCustomTitleBar`):
 * caption buttons claim their reported bounds (Snap Layouts / system minimize/maximize/close),
 * the remaining title-bar band is a native drag region (Caption), and everything else defers to
 * the toolkit default (null). win32 `LogicalPoint`/`LogicalSize` have public Float constructors,
 * and `NCHitTestResult` is a plain enum, so no natives are required.
 */
@Category(HeadlessTest::class)
class WindowsTitleBarHitTestTest {
    private val titleBarHeight = 44f
    private val clientSize = LogicalSize(800f, 600f)
    private val allButtons = mapOf(
        CaptionButtonKind.Minimize to DpRect(662.dp, 0.dp, 708.dp, 44.dp),
        CaptionButtonKind.Maximize to DpRect(708.dp, 0.dp, 754.dp, 44.dp),
        CaptionButtonKind.Close to DpRect(754.dp, 0.dp, 800.dp, 44.dp),
    )

    private fun hitTest(
        x: Float,
        y: Float,
        buttons: Map<CaptionButtonKind, DpRect> = allButtons,
    ): NCHitTestResult? = hitTestCustomTitleBar(
        captionButtonBounds = buttons,
        clientPoint = LogicalPoint(x, y),
        clientSize = clientSize,
        titleBarHeight = titleBarHeight,
    )

    @Test
    fun captionButtonsClaimTheirBounds() {
        assertEquals(NCHitTestResult.MinButton, hitTest(680f, 20f))
        assertEquals(NCHitTestResult.MaxButton, hitTest(730f, 20f))
        assertEquals(NCHitTestResult.Close, hitTest(780f, 20f))
    }

    @Test
    fun buttonBoundsAreInclusiveOnTheirEdges() {
        // Ranges are closed on both ends; a shared edge belongs to the first matching button.
        assertEquals(NCHitTestResult.MinButton, hitTest(662f, 0f))
        assertEquals(NCHitTestResult.Close, hitTest(800f, 44f))
    }

    @Test
    fun titleBarBandOutsideButtonsIsCaption() {
        assertEquals(NCHitTestResult.Caption, hitTest(400f, 20f))
        // The band is inclusive at both vertical edges (0f..titleBarHeight).
        assertEquals(NCHitTestResult.Caption, hitTest(400f, 0f))
        assertEquals(NCHitTestResult.Caption, hitTest(400f, 44f))
        // ... and at both horizontal client edges.
        assertEquals(NCHitTestResult.Caption, hitTest(0f, 20f))
        assertEquals(NCHitTestResult.Caption, hitTest(661f, 20f, buttons = emptyMap()))
    }

    @Test
    fun belowTheBandDefersToTheToolkitDefault() {
        assertNull(hitTest(400f, 44.1f))
        assertNull(hitTest(400f, 300f))
    }

    @Test
    fun outsideTheClientWidthDefersToTheToolkitDefault() {
        assertNull(hitTest(-0.1f, 20f, buttons = emptyMap()))
        assertNull(hitTest(800.1f, 20f, buttons = emptyMap()))
    }

    @Test
    fun clearedButtonAreaFallsBackToCaption() {
        val withoutClose = allButtons - CaptionButtonKind.Close
        assertEquals(NCHitTestResult.Caption, hitTest(780f, 20f, buttons = withoutClose))
    }

    @Test
    fun buttonBelowTheBandStillClaimsItsArea() {
        // The Compose title bar owns the reported bounds; the band check never pre-empts a button
        // even if the button extends past the nominal title-bar height.
        val tallClose = mapOf(CaptionButtonKind.Close to DpRect(754.dp, 0.dp, 800.dp, 60.dp))
        assertEquals(NCHitTestResult.Close, hitTest(780f, 50f, buttons = tallClose))
    }

    @Test
    fun emptyBoundsMakeTheWholeBandCaption() {
        assertEquals(NCHitTestResult.Caption, hitTest(780f, 20f, buttons = emptyMap()))
        assertNull(hitTest(780f, 50f, buttons = emptyMap()))
    }
}
