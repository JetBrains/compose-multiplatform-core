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

package androidx.compose.ui.modifiers

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.UIKitKeepScreenOnManager
import androidx.compose.ui.test.runUIKitInstrumentedTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class KeepScreenOnModifierTest {

    @BeforeTest
    fun before() {
        UIKitKeepScreenOnManager.instance.reset()
    }

    @Test
    fun testFlagOnWhenModifierAdded() = runUIKitInstrumentedTest {
        setContent {
            Box(Modifier.keepScreenOn())
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }

    @Test
    fun testFlagOffWhenModifierRemoved() = runUIKitInstrumentedTest {
        var attach by mutableStateOf(true)

        setContent {
            if (attach) {
                Box(Modifier.keepScreenOn())
            }
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)

        attach = false

        waitForIdle()

        assertFalse(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }

    @Test
    fun testFlagOffWhenParentRemovedAndModifierInChild() = runUIKitInstrumentedTest {
        var attach by mutableStateOf(true)

        setContent {
            Box {
                if (attach) {
                    Box {
                        Box(Modifier.keepScreenOn())
                    }
                }
            }
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)

        attach = false

        waitForIdle()

        assertFalse(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }

    @Test
    fun testFlagOnWhenParentRemovedAndModifierInSibling() = runUIKitInstrumentedTest {
        var attach by mutableStateOf(true)

        setContent {
            Box {
                if (attach) {
                    Box {
                        Box(Modifier.keepScreenOn())
                    }
                }
                Box(Modifier.keepScreenOn())
            }
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)

        attach = false

        waitForIdle()

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }

    @Test
    fun testFlagOnWhenModifierRemovedInChild() = runUIKitInstrumentedTest {
        var attach by mutableStateOf(true)

        setContent {
            Box {
                Box(Modifier.keepScreenOn())
                Box {
                    if (attach) {
                        Box(Modifier.keepScreenOn())
                    }
                }
            }
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)

        attach = false

        waitForIdle()

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }

    @Test
    fun testFlagOnWhenModifierRemovedInSibling() = runUIKitInstrumentedTest {
        var attach by mutableStateOf(true)

        setContent {
            Box {
                Box(Modifier.keepScreenOn())
                if (attach) {
                    Box(Modifier.keepScreenOn())
                }
            }
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)

        attach = false

        waitForIdle()

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }


    @Test
    fun testFlagOffWhenAllModifiersRemoved() = runUIKitInstrumentedTest {
        var attach by mutableStateOf(true)

        setContent {
            Box {
                if (attach) {
                    Box(Modifier.keepScreenOn())
                }
                if (attach) {
                    Box(Modifier.keepScreenOn())
                }
            }
        }

        assertTrue(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)

        attach = false

        waitForIdle()

        assertFalse(UIKitKeepScreenOnManager.instance.isKeepScreenOnEnabled)
    }
}