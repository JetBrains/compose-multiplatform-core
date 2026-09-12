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

package androidx.compose.ui.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.ComposeUIView
import androidx.compose.ui.window.Popup
import kotlin.test.Test
import kotlin.test.assertEquals

private val LocalTestValue = staticCompositionLocalOf { "default" }

@OptIn(ExperimentalComposeUiApi::class)
class CompositionContextTest {

    @Test
    fun compositionLocalPropagatedIntoPopup() = runUIKitInstrumentedTest {
        val providedValue = "root-value"
        var valueInsidePopup: String? = null

        setContent {
            CompositionLocalProvider(LocalTestValue provides providedValue) {
                Popup {
                    valueInsidePopup = LocalTestValue.current
                }
            }
        }

        assertEquals(providedValue, valueInsidePopup)
    }

    @Test
    fun rootCompositionLocalPropagatedIntoNestedComposeUIView() = runUIKitInstrumentedTest {
        val providedValue = "root-value"
        var valueInNestedContainer: String? = null

        setContent {
            CompositionLocalProvider(LocalTestValue provides providedValue) {
                UIKitView(
                    factory = {
                        ComposeUIView(
                            configure = { enforceStrictPlistSanityCheck = false }
                        ) {
                            valueInNestedContainer = LocalTestValue.current
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(placedAsOverlay = true)
                )
            }
        }

        assertEquals(providedValue, valueInNestedContainer)
    }

    @Test
    fun compositionLocalCanBeOverriddenInNestedComposeUIView() = runUIKitInstrumentedTest {
        val rootValue = "root-value"
        val overriddenValue = "overridden-value"
        var valueAtLevel1: String? = null
        var valueAtLevel2: String? = null

        setContent {
            CompositionLocalProvider(LocalTestValue provides rootValue) {
                // Level 1: nested ComposeUIView reading the value propagated from the root.
                UIKitView(
                    factory = {
                        ComposeUIView(
                            configure = { enforceStrictPlistSanityCheck = false }
                        ) {
                            valueAtLevel1 = LocalTestValue.current

                            // Override the composition local for everything below, including the
                            // next nested ComposeUIView.
                            CompositionLocalProvider(LocalTestValue provides overriddenValue) {
                                // Level 2: nested ComposeUIView reading the overridden value.
                                UIKitView(
                                    factory = {
                                        ComposeUIView(
                                            configure = { enforceStrictPlistSanityCheck = false }
                                        ) {
                                            valueAtLevel2 = LocalTestValue.current
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    properties = UIKitInteropProperties(placedAsOverlay = true)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    properties = UIKitInteropProperties(placedAsOverlay = true)
                )
            }
        }

        assertEquals(rootValue, valueAtLevel1)
        assertEquals(overriddenValue, valueAtLevel2)
    }
}
