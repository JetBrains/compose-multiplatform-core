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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.setPreferredContentSizeCategory
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryLarge

internal class FontScaleInHostingViewTest: FontScaleTest({ runUIKitInstrumentedTest(true, it) })
internal class FontScaleInHostingViewControllerTest: FontScaleTest({ runUIKitInstrumentedTest(false, it) })

internal abstract class FontScaleTest(
    private val runUIKitInstrumentedTest: (UIKitInstrumentedTest.() -> Unit) -> Unit
) {
    @Test
    fun traitCollectionChangeUpdatesComposeFontScale() = runUIKitInstrumentedTest {
        var fontScale = 0f
        setContent {
            fontScale = LocalDensity.current.fontScale
        }

        assertEquals(DefaultFontScale, fontScale)

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        waitUntil { fontScale == AccessibilityLargeFontScale }
    }

    @Test
    fun traitCollectionChangeCanBeReverted() = runUIKitInstrumentedTest {
        var fontScale = 0f
        setContent {
            fontScale = LocalDensity.current.fontScale
        }

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)
        waitUntil { fontScale == AccessibilityLargeFontScale }

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryLarge)
        waitUntil { fontScale == DefaultFontScale }
    }

    @Test
    fun traitCollectionChangeDoesNotChangeScreenDensity() = runUIKitInstrumentedTest {
        var density = 0f
        var fontScale = 0f
        setContent {
            density = LocalDensity.current.density
            fontScale = LocalDensity.current.fontScale
        }
        val initialDensity = density

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        waitUntil { fontScale == AccessibilityLargeFontScale }
        assertEquals(initialDensity, density)
    }

    @Test
    fun popupOpenedAfterTraitCollectionChangeUsesUpdatedFontScale() = runUIKitInstrumentedTest {
        val showPopup = mutableStateOf(false)
        var popupFontScale = 0f

        setContent {
            if (showPopup.value) {
                Popup {
                    popupFontScale = LocalDensity.current.fontScale
                }
            }
        }

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        showPopup.value = true
        waitUntil { popupFontScale == AccessibilityLargeFontScale }
    }

    @Test
    fun dialogOpenedAfterTraitCollectionChangeUsesUpdatedFontScale() = runUIKitInstrumentedTest {
        val showDialog = mutableStateOf(false)
        var dialogFontScale = 0f

        setContent {
            if (showDialog.value) {
                Dialog(onDismissRequest = {}) {
                    dialogFontScale = LocalDensity.current.fontScale
                }
            }
        }

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        showDialog.value = true
        waitUntil { dialogFontScale == AccessibilityLargeFontScale }
    }

    @Test
    fun openPopupUpdatesFontScaleAfterTraitCollectionChange() = runUIKitInstrumentedTest {
        var popupFontScale = 0f

        setContent {
            Popup {
                popupFontScale = LocalDensity.current.fontScale
            }
        }

        assertEquals(DefaultFontScale, popupFontScale)

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        waitUntil { popupFontScale == AccessibilityLargeFontScale }
    }

    @Test
    fun openDialogUpdatesFontScaleAfterTraitCollectionChange() = runUIKitInstrumentedTest {
        var popupFontScale = 0f

        setContent {
            Dialog(onDismissRequest = {}) {
                popupFontScale = LocalDensity.current.fontScale
            }
        }

        assertEquals(DefaultFontScale, popupFontScale)

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        waitUntil { popupFontScale == AccessibilityLargeFontScale }
    }

    private companion object {
        const val DefaultFontScale = 1f
        const val AccessibilityLargeFontScale = 1.5f
    }
}
