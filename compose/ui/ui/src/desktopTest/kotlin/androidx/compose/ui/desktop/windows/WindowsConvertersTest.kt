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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.jetbrains.desktop.win32.LogicalPoint
import org.jetbrains.desktop.win32.LogicalSize
import org.jetbrains.desktop.win32.PhysicalPoint
import org.junit.Test
import org.junit.experimental.categories.Category

/**
 * win32 geometry is Float-based (`LogicalPoint`/`LogicalSize`) and Int-based (`PhysicalPoint`) —
 * unlike macOS KDT geometry, which is Double-based. All three have public constructors, so the
 * converter math is pure-JVM testable.
 */
@Category(HeadlessTest::class)
class WindowsConvertersTest {
    private val density2x = Density(2f)

    @Test
    fun logicalSizeConversions() {
        assertEquals(DpSize(800.dp, 600.dp), LogicalSize(800f, 600f).toDpSize())
        assertEquals(IntSize(1600, 1200), LogicalSize(800f, 600f).roundToIntSize(density2x))
    }

    @Test
    fun logicalPointConversionsAndArithmetic() {
        assertEquals(DpOffset(10.dp, 20.dp), LogicalPoint(10f, 20f).toDpOffset())
        val sum = LogicalPoint(10f, 20f) + LogicalPoint(1f, 2f)
        assertEquals(11f, sum.x)
        assertEquals(22f, sum.y)
        val diff = LogicalPoint(10f, 20f) - LogicalPoint(1f, 2f)
        assertEquals(9f, diff.x)
        assertEquals(18f, diff.y)
    }

    @Test
    fun offsetToNativeConversions() {
        // Offset is in px; at 2x density the logical (dp-valued) coordinates halve.
        val logical = Offset(20f, 40f).toLogicalPoint(density2x)
        assertEquals(10f, logical.x)
        assertEquals(20f, logical.y)
        val physical = Offset(20.4f, 40.6f).toPhysicalPoint()
        assertEquals(20, physical.x)
        assertEquals(41, physical.y)
        val logicalSize = Size(20f, 40f).toLogicalSize(density2x)
        assertEquals(10f, logicalSize.width)
        assertEquals(20f, logicalSize.height)
    }

    @Test
    fun dpOffsetToLogicalPointIsValuePreserving() {
        val logical = DpOffset(15.dp, 25.dp).toLogicalPoint()
        assertEquals(15f, logical.x)
        assertEquals(25f, logical.y)
    }
}
