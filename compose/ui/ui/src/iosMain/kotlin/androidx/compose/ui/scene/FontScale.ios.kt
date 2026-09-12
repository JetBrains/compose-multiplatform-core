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

package androidx.compose.ui.scene

import androidx.annotation.VisibleForTesting
import platform.UIKit.UIContentSizeCategory
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityExtraLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityLarge
import platform.UIKit.UIContentSizeCategoryAccessibilityMedium
import platform.UIKit.UIContentSizeCategoryExtraExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraLarge
import platform.UIKit.UIContentSizeCategoryExtraSmall
import platform.UIKit.UIContentSizeCategoryLarge
import platform.UIKit.UIContentSizeCategoryMedium
import platform.UIKit.UIContentSizeCategorySmall
import platform.UIKit.UIContentSizeCategoryUnspecified
import platform.UIKit.UIView

internal fun FontScaleProvider(
    view: UIView,
    onFontScaleChanged: (Float) -> Unit,
) = FontScaleProvider(
    { view.traitCollection.preferredContentSizeCategory ?: UIContentSizeCategoryUnspecified },
    onFontScaleChanged = onFontScaleChanged,
)

internal class FontScaleProvider @VisibleForTesting constructor(
    private val preferredContentSizeCategory: () -> UIContentSizeCategory,
    private val onFontScaleChanged: (Float) -> Unit,
) {
    var fontScale: Float = calculateFontScale()
        private set

    fun onTraitCollectionDidChange() {
        val newValue = calculateFontScale()

        if (newValue != fontScale) {
            fontScale = newValue
            onFontScaleChanged(newValue)
        }
    }

    private fun calculateFontScale(): Float =
        uiContentSizeCategoryToFontScaleMap[preferredContentSizeCategory()] ?: 1f
}

private val uiContentSizeCategoryToFontScaleMap = mapOf(
    UIContentSizeCategoryExtraSmall to 0.8f,
    UIContentSizeCategorySmall to 0.85f,
    UIContentSizeCategoryMedium to 0.9f,
    UIContentSizeCategoryLarge to 1f, // default preference
    UIContentSizeCategoryExtraLarge to 1.1f,
    UIContentSizeCategoryExtraExtraLarge to 1.2f,
    UIContentSizeCategoryExtraExtraExtraLarge to 1.3f,

    // These values don't match the scale shown by Text Size because iOS uses non-linear scaling
    // calculated by UIFontMetrics, while Compose uses linear scaling.
    UIContentSizeCategoryAccessibilityMedium to 1.4f,
    UIContentSizeCategoryAccessibilityLarge to 1.5f,
    UIContentSizeCategoryAccessibilityExtraLarge to 1.6f,
    UIContentSizeCategoryAccessibilityExtraExtraLarge to 1.7f,
    UIContentSizeCategoryAccessibilityExtraExtraExtraLarge to 1.8f,
)
