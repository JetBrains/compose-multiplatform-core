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

package androidx.compose.ui.viewinterop

import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.UIKit.UILayoutFittingCompressedSize
import platform.UIKit.UILayoutPriorityRequired
import platform.UIKit.UIView

internal class UIKitInteropElementMeasurePolicy(
    private val userComponent: UIView
): MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        if (constraints.hasFixedWidth && constraints.hasFixedHeight) {
            return layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        val minW = constraints.minWidth.toDp()
        val minH = constraints.minHeight.toDp()
        val maxW = constraints.maxWidth.toDp()
        val maxH = constraints.maxHeight.toDp()

        val fixedW = if (constraints.hasFixedWidth) minW else null
        val fixedH = if (constraints.hasFixedHeight) minH else null

        val measuredSize = userComponent.measureFittingSize(
            fixedWidth = fixedW,
            fixedHeight = fixedH,
            minWidth = minW,
            minHeight = minH,
            maxWidth = maxW,
            maxHeight = maxH,
        )

        return layout(measuredSize.width.roundToPx(), measuredSize.height.roundToPx()) {}
    }
}

/**
 * Measures UIKit view's Auto Layout compressed fitting size under the given Dp size constraints.
 *
 * The measurement is performed by temporarily applying size constraints to bound the solve:
 *  - For fixed axes: `== fixed`
 *  - For wrap axes: `<= max`
 */
internal fun UIView.measureFittingSize(
    fixedWidth: Dp? = null,
    fixedHeight: Dp? = null,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 0.dp,
    maxWidth: Dp,
    maxHeight: Dp,
): DpSize {
    val widthConstraint = if (fixedWidth != null) {
        widthAnchor.constraintEqualToConstant(fixedWidth.value.toDouble())
    } else {
        widthAnchor.constraintLessThanOrEqualToConstant(maxWidth.value.toDouble())
    }.apply {
        priority = UILayoutPriorityRequired
        active = true
    }

    val heightConstraint = if (fixedHeight != null) {
        heightAnchor.constraintEqualToConstant(fixedHeight.value.toDouble())
    } else {
        heightAnchor.constraintLessThanOrEqualToConstant(maxHeight.value.toDouble())
    }.apply {
        priority = UILayoutPriorityRequired
        active = true
    }

    return try {
        systemLayoutSizeFittingSize(UILayoutFittingCompressedSize.readValue())
            .useContents {
                DpSize(
                    width.dp.coerceIn(minWidth, maxWidth),
                    height.dp.coerceIn(minHeight, maxHeight)
                )
            }
            .let { if (it.width == 0.dp || it.height == 0.dp) DpSize.Zero else it }
    } finally {
        widthConstraint.active = false
        heightConstraint.active = false
    }
}