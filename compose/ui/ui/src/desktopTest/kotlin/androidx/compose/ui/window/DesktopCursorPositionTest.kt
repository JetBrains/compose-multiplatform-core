/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.window

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toDpOffset
import androidx.compose.ui.unit.toOffset
import com.google.common.truth.Truth.assertThat
import java.awt.Robot
import org.junit.Test

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
internal class DesktopCursorPositionTest {

    private val windowSize = IntSize(200, 200)
    private val anchorPosition = IntOffset(0, 0)
    private val anchorSize = IntSize(100, 100)
    private val popupSize = IntSize(20, 20)

    @Test
    fun `pointer position with single component`(): Unit = runApplicationTest {
        var pointerPosition: IntOffset? = null
        var window: ComposeWindow? = null
        val robot = Robot()
        val pxTargetOffset = IntOffset(84, 58)
        var pointerMoved by mutableStateOf(false)
        launchTestWindowApplication(
            WindowState(WindowPlacement.Maximized),
        ) {
            window = this.window

            Box(
                modifier = Modifier
                    .size(200.dp, 200.dp)
            ){
                if (pointerMoved) {
                    pointerPosition = rememberCursorPositionProvider().calculatePosition(
                        IntRect(anchorPosition, anchorSize),
                        windowSize,
                        LayoutDirection.Ltr,
                        popupSize
                    )
                }
            }
        }
        awaitIdle()

        val contentLocation = window?.contentPane?.locationOnScreen ?: java.awt.Point(0, 0)
        moveMouse(
            contentLocation.x + pxTargetOffset.x,
            contentLocation.y + pxTargetOffset.y
        )
        awaitIdle()
        pointerMoved = true
        awaitIdle()

        //calculatePosition returns an IntOffset but that includes the density factor,
        // so we need to convert it back to Dp and then to pixels again to compare with the original IntOffset
        val pxPointerPosition = pointerPosition?.toDpOffset(window?.density ?: Density(1f))
        assertThat(pxPointerPosition).isEqualTo(pxTargetOffset)
    }

    private fun IntOffset.toDpOffset(density: Density): IntOffset {
        return with(density) { IntOffset(x.toDp().value.toInt(), y.toDp().value.toInt()) }
    }
}