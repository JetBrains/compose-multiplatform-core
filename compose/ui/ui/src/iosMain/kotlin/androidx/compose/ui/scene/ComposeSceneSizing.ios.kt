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

package androidx.compose.ui.scene

import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.window.ComposeContainerView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIViewNoIntrinsicMetric

/**
 * Provides Compose scene sizes to UIKit's sizeThatFits and intrinsicContentSize APIs.
 */
internal class ComposeSceneSizing(
    private val view: ComposeContainerView,
    private val measureSceneSize: (Constraints) -> IntSize?,
) {
    /**
     * A Compose scene needs a non-zero UIKit layout before it can provide a preferred size for a
     * UIKit sizing proposal. Until then, use UIKit's default sizing result.
     */
    private var hasNonZeroSceneExtent = false
    private var lastSizeThatFitsResult: CValue<CGSize>? = null

    fun sizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
        val constraints = size.useContents {
            Constraints(
                maxWidth = width.toConstraintValue(view.density),
                maxHeight = height.toConstraintValue(view.density)
            )
        }

        val measuredSize = if (hasNonZeroSceneExtent) {
            measureSceneSize(constraints)
        } else {
            null
        }
        return (measuredSize?.toCGSize(view.density) ?: fallbackSizeThatFits(size)).also {
            lastSizeThatFitsResult = it
        }
    }

    /**
     * UIKit does not supply a proposal for an intrinsic-size query. Compose is measured only for
     * explicit sizeThatFits proposals, so return the latest fitting result rather than measure
     * with an unbounded axis.
     */
    fun intrinsicContentSize(): CValue<CGSize>? = lastSizeThatFitsResult

    /**
     * Called after the current UIKit bounds have been measured and laid out by Compose.
     */
    fun onLayout() {
        hasNonZeroSceneExtent = view.bounds.useContents {
            size.width > 0.0 || size.height > 0.0
        }
    }

    private fun fallbackSizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
        val viewSizeThatFits by lazy { view.superSizeThatFits(size) }

        val width = if (size.useContents { width } == UIViewNoIntrinsicMetric) {
            viewSizeThatFits.useContents { width }
        } else {
            size.useContents { width }
        }
        val height = if (size.useContents { height } == UIViewNoIntrinsicMetric) {
            viewSizeThatFits.useContents { height }
        } else {
            size.useContents { height }
        }

        return with(view.density) {
            DpSize(width.dp, height.dp).toSize().toIntSize().toCGSize(this)
        }
    }
}

private fun CGFloat.toConstraintValue(density: Density): Int {
    if (this == UIViewNoIntrinsicMetric) return Constraints.Infinity
    val px = with(density) { dp.roundToPx() }
    if (px >= 0 || px == Constraints.Infinity) return px
    throw IllegalArgumentException("Invalid constraint size: $this")
}

private fun IntSize.toCGSize(density: Density) = with(density) {
    CGSizeMake(width.toDp().value.toDouble(), height.toDp().value.toDouble())
}
