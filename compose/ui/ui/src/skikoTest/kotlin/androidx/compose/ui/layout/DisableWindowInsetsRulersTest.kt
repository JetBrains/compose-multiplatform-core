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

package androidx.compose.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.areWindowInsetsRulersEnabled
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.v2.runInternalSkikoComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, InternalTestApi::class)
class DisableWindowInsetsRulersTest {

    @AfterTest
    fun tearDown() {
        areWindowInsetsRulersEnabled = true
    }

    @Test
    fun disableWindowInsetsRulers() {
        androidx.compose.ui.disableWindowInsetsRulers()

        runInternalSkikoComposeUiTest(
            windowInsets = TestWindowInsets(systemBarsInsets = mutableStateOf(PlatformInsets(top = 100)))
        ) {
            var left = 0f
            var top = 0f
            var right = 0f
            var bottom = 0f

            setContent {
                Box(
                    Modifier.fillMaxSize().layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                            left = WindowInsetsRulers.StatusBars.current.left.current(Float.NaN)
                            top = WindowInsetsRulers.StatusBars.current.top.current(Float.NaN)
                            right = WindowInsetsRulers.StatusBars.current.right.current(Float.NaN)
                            bottom = WindowInsetsRulers.StatusBars.current.bottom.current(Float.NaN)
                        }
                    }
                )
            }

            waitForIdle()

            assertTrue(left.isNaN())
            assertTrue(top.isNaN())
            assertTrue(right.isNaN())
            assertTrue(bottom.isNaN())
        }
    }
}

private fun TestWindowInsets(
    systemBarsInsets: androidx.compose.runtime.State<PlatformInsets> = mutableStateOf(PlatformInsets.Zero)
): PlatformWindowInsets = object : PlatformWindowInsets {
    override val statusBars: PlatformInsets get() = PlatformInsets(
        getBottom = { systemBarsInsets.value.bottom },
        getTop = { systemBarsInsets.value.top },
        getLeft = { systemBarsInsets.value.left },
        getRight = { systemBarsInsets.value.right }
    )
}
