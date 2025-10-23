/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.compose.ui.layers

import androidx.compose.material.Button
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LocalDensityTest {
    @Test
    fun testCustomDensityNotPropagatedToDialog() = runUIKitInstrumentedTest {
        val customDensity = Density(density = 5f)
        var density = -1f

        setContent {
            CompositionLocalProvider(LocalDensity provides customDensity) {
                Dialog(onDismissRequest = {}) {
                    density = LocalDensity.current.density
                }
            }
        }

        assertNotEquals(customDensity.density, density)
        assertEquals(hostingViewController.view.density.density, density)
    }

    @Test
    fun testCustomDensityNotPropagatedToPopup() = runUIKitInstrumentedTest {
        val customDensity = Density(density = 5f)
        var density = -1f

        setContent {
            CompositionLocalProvider(LocalDensity provides customDensity) {
                Popup {
                    density = LocalDensity.current.density
                }
            }
        }

        assertNotEquals(customDensity.density, density)
        assertEquals(hostingViewController.view.density.density, density)
    }

    @Test
    fun testCustomDensityPropagatedInDialogContent() = runUIKitInstrumentedTest {
        val outerDensity = Density(density = 5f)
        val innerDensity = Density(density = 10f)
        var actualOuterDensity = -1f
        var actualInnerDensity = -1f

        setContent {
            CompositionLocalProvider(LocalDensity provides outerDensity) {
                Dialog(onDismissRequest = {}) {
                    actualOuterDensity = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides innerDensity) {
                        Button(onClick = {}) {
                            actualInnerDensity = LocalDensity.current.density
                        }
                    }
                }
            }
        }

        assertNotEquals(outerDensity.density, actualOuterDensity)
        assertEquals(innerDensity.density, actualInnerDensity)
    }

    @Test
    fun testCustomDensityPropagatedInPopupContent() = runUIKitInstrumentedTest {
        val outerDensity = Density(density = 5f)
        val innerDensity = Density(density = 10f)
        var actualOuterDensity = -1f
        var actualInnerDensity = -1f

        setContent {
            CompositionLocalProvider(LocalDensity provides outerDensity) {
                Popup {
                    actualOuterDensity = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides innerDensity) {
                        Button(onClick = {}) {
                            actualInnerDensity = LocalDensity.current.density
                        }
                    }
                }
            }
        }

        assertNotEquals(outerDensity.density, actualOuterDensity)
        assertEquals(innerDensity.density, actualInnerDensity)
    }
}