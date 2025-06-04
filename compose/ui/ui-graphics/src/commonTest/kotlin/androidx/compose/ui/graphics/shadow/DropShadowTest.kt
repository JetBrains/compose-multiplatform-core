/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.graphics.shadow

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DropShadowTest {

    @Test
    fun testInnerShadowWithColor() {
        val dropShadow = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        assertEquals(dropShadow.radius, 1.dp)
        assertEquals(dropShadow.spread, 2.dp)
        assertEquals(dropShadow.color, Color.Cyan)
        assertEquals(dropShadow.brush, null)
        assertEquals(dropShadow.alpha, 0.5f)
        assertEquals(dropShadow.blendMode, BlendMode.Dst)
    }

    @Test
    fun testInnerShadowWithGradient() {
        val gradient = Brush.linearGradient(listOf(Color.Red, Color.Blue))
        val dropShadow = DropShadow(1.dp, gradient, 2.dp, 0.5f, BlendMode.Dst)
        assertEquals(dropShadow.radius, 1.dp)
        assertEquals(dropShadow.spread, 2.dp)
        assertEquals(dropShadow.color, Color.Black)
        assertEquals(dropShadow.brush, gradient)
        assertEquals(dropShadow.alpha, 0.5f)
        assertEquals(dropShadow.blendMode, BlendMode.Dst)
    }

    @Test
    fun testInnerShadowWithSolidColorBrush() {
        val solidColor = SolidColor(Color.Magenta)
        val dropShadow = DropShadow(1.dp, solidColor, 2.dp, 0.5f, BlendMode.Dst)
        assertEquals(dropShadow.radius, 1.dp)
        assertEquals(dropShadow.spread, 2.dp)
        assertEquals(dropShadow.color, Color.Magenta)
        assertEquals(dropShadow.brush, null)
        assertEquals(dropShadow.alpha, 0.5f)
        assertEquals(dropShadow.blendMode, BlendMode.Dst)
    }

    @Test
    fun testDropShadowEquals() {
        val shadow1 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        val shadow2 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        val shadow3 = DropShadow(1.dp, SolidColor(Color.Cyan), 2.dp, 0.5f, BlendMode.Dst)
        assertEquals(shadow1, shadow2)
        assertEquals(shadow2, shadow3)

        val shadow4 = DropShadow(2.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        assertNotEquals(shadow1, shadow4)

        val shadow5 = DropShadow(1.dp, Color.Cyan, 3.dp, 0.5f, BlendMode.Dst)
        assertNotEquals(shadow2, shadow5)

        val shadow6 = DropShadow(1.dp, Color.Yellow, 2.dp, 0.5f, BlendMode.Dst)
        assertNotEquals(shadow3, shadow6)

        val shadow7 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.6f, BlendMode.Dst)
        assertNotEquals(shadow1, shadow7)

        val shadow8 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Src)
        assertNotEquals(shadow1, shadow8)
    }

    @Test
    fun testDropShadowHashCode() {
        val shadow1 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        val shadow2 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        assertEquals(shadow1.hashCode(), shadow2.hashCode())
    }

    @Test
    fun testDropShadowToString() {
        val shadow1 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        val shadow2 = DropShadow(1.dp, Color.Cyan, 2.dp, 0.5f, BlendMode.Dst)
        assertEquals(shadow1.toString(), shadow2.toString())
    }
}
