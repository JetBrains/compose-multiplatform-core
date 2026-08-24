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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.test.setPreferredContentSizeCategory
import androidx.compose.ui.unit.Density
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
    fun traitCollectionChangeUpdatesAndRevertsComposeFontScaleWithoutChangingScreenDensity() =
        runUIKitInstrumentedTest {
            var density = 0f
            var fontScale = 0f
            setContent {
                density = LocalDensity.current.density
                fontScale = LocalDensity.current.fontScale
            }
            val initialDensity = density

            assertEquals(DefaultFontScale, fontScale)

            viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)
            waitUntil { fontScale == AccessibilityLargeFontScale }
            assertEquals(initialDensity, density)

            viewController.setPreferredContentSizeCategory(UIContentSizeCategoryLarge)
            waitUntil { fontScale == DefaultFontScale }
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

    /**
     * Android popups use platform density instead of inheriting the creator's LocalDensity override.
     * Keep iOS scene layers aligned with that behavior for both density and font scale.
     */
    @Test
    fun popupUsesPlatformDensityInsteadOfCreatorLocalDensity() = runUIKitInstrumentedTest {
        val showPopup = mutableStateOf(false)
        var platformDensity = 0f
        var platformFontScale = 0f
        var parentDensity = 0f
        var parentFontScale = 0f
        var popupDensity = 0f
        var popupFontScale = 0f

        setContent {
            platformDensity = LocalDensity.current.density
            platformFontScale = LocalDensity.current.fontScale

            CompositionLocalProvider(
                LocalDensity provides Density(CustomDensity, CustomFontScale)
            ) {
                parentDensity = LocalDensity.current.density
                parentFontScale = LocalDensity.current.fontScale

                if (showPopup.value) {
                    Popup {
                        popupDensity = LocalDensity.current.density
                        popupFontScale = LocalDensity.current.fontScale
                    }
                }
            }
        }

        assertEquals(CustomDensity, parentDensity)
        assertEquals(CustomFontScale, parentFontScale)

        showPopup.value = true
        waitUntil { popupDensity != 0f }

        assertEquals(platformDensity, popupDensity)
        assertEquals(platformFontScale, popupFontScale)
    }

    @Test
    fun openDialogUpdatesFontScaleAfterTraitCollectionChange() = runUIKitInstrumentedTest {
        var dialogFontScale = 0f

        setContent {
            Dialog(onDismissRequest = {}) {
                dialogFontScale = LocalDensity.current.fontScale
            }
        }

        assertEquals(DefaultFontScale, dialogFontScale)

        viewController.setPreferredContentSizeCategory(UIContentSizeCategoryAccessibilityLarge)

        waitUntil { dialogFontScale == AccessibilityLargeFontScale }
    }

    private companion object {
        const val DefaultFontScale = 1f
        const val AccessibilityLargeFontScale = 1.5f
        const val CustomDensity = 2.5f
        const val CustomFontScale = 1.7f
    }
}
