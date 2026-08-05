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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toCGSize
import androidx.compose.ui.unit.toDpSize
import androidx.compose.ui.window.ComposeContainerView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIViewNoIntrinsicMetric

internal interface ComposeSceneSizing {
    fun sizeThatFits(size: CValue<CGSize>): CValue<CGSize>

    fun intrinsicContentSize(): CValue<CGSize>? = null

    fun onLayout()
}

internal fun ComposeSceneSizing(
    view: ComposeContainerView,
    measureSceneSize: (Constraints) -> IntSize?,
    usesIntrinsicContentSize: Boolean,
): ComposeSceneSizing = if (usesIntrinsicContentSize) {
    ComposeSceneIntrinsicSizing(
        view = view,
        measureSceneSize = measureSceneSize,
    )
} else {
    ComposeSceneSizeThatFits(
        view = view,
        measureSceneSize = measureSceneSize,
    )
}

/**
 * Measures the preferred Compose scene size for an explicit UIKit proposal.
 *
 * This is the only sizing path used by SwiftUI's iOS 16+ `sizeThatFits` API.
 */
private class ComposeSceneSizeThatFits(
    private val view: ComposeContainerView,
    private val measureSceneSize: (Constraints) -> IntSize?,
) : ComposeSceneSizing {
    private var hasNonZeroSceneExtent = false

    override fun sizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
        val constraints = size.useContents {
            Constraints(
                maxWidth = width.toConstraintValue(view.density),
                maxHeight = height.toConstraintValue(view.density)
            )
        }

        val measuredSize = if (hasNonZeroSceneExtent) {
            measureSceneSize(constraints)?.toDpSize(view.density)?.toCGSize()
        } else {
            null
        }

        return measuredSize ?: view.fallbackSizeThatFits(size)
    }

    override fun onLayout() {
        // UIKit bounds have been measured and laid out by Compose, so
        // later explicit proposals can use the Compose measurement instead of the UIKit fallback
        hasNonZeroSceneExtent = view.bounds.useContents {
            size.width > 0.0 || size.height > 0.0
        }
    }
}

/**
 * UIKit does not provide an explicit proposal for an intrinsic-size query. This bridge therefore
 * remembers the latest proposal and exposes its result as the intrinsic size.
 */
private class ComposeSceneIntrinsicSizing(
    private val view: ComposeContainerView,
    private val measureSceneSize: (Constraints) -> IntSize?,
) : ComposeSceneSizing {
    private var hasNonZeroSceneExtent = false
    private var lastSizeThatFitsProposal: CValue<CGSize>? = null
    private var lastSizeThatFitsResult: CValue<CGSize>? = null

    override fun sizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
        val constraints = size.useContents {
            Constraints(
                maxWidth = width.toConstraintValue(view.density),
                maxHeight = height.toConstraintValue(view.density)
            )
        }

        val measuredSceneSize = if (hasNonZeroSceneExtent) {
            measureSceneSize(constraints)
                // The first Compose measurement after UIKit supplies non-zero bounds can still be 0x0.
                // Publishing it as the intrinsic size would collapse the host before Compose can remeasure,
                // so keep the UIKit fallback until either axis has a measured extent.
                // A real zero value on only one axis remains valid.
                ?.takeIf { it.width > 0 || it.height > 0 }
                ?.toDpSize(view.density)
                ?.toCGSize()
        } else {
            null
        }

        val result = measuredSceneSize ?: view.fallbackSizeThatFits(size)
        val previousResult = lastSizeThatFitsResult
        lastSizeThatFitsResult = result
        lastSizeThatFitsProposal = size
        // UIKit's layout can change the cached intrinsic answer from the fallback to the Compose
        // measurement without Compose requesting a layout. LayoutInvalidationHandler does not run
        // in that case, so notify intrinsic-size consumers when the reported answer changes.
        if (previousResult?.hasSameSize(result) != true) {
            view.invalidateIntrinsicContentSize()
        }
        return result
    }

    override fun intrinsicContentSize(): CValue<CGSize>? = lastSizeThatFitsResult

    override fun onLayout() {
        hasNonZeroSceneExtent = view.bounds.useContents {
            size.width > 0.0 || size.height > 0.0
        }
        lastSizeThatFitsProposal?.let(::sizeThatFits)
    }

    private fun CValue<CGSize>.hasSameSize(other: CValue<CGSize>): Boolean =
        useContents { width to height } == other.useContents { width to height }
}

private fun ComposeContainerView.fallbackSizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
    val viewSizeThatFits by lazy { this.superSizeThatFits(size) }

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

    return CGSizeMake(width, height)
}

private fun CGFloat.toConstraintValue(density: Density): Int {
    if (this == UIViewNoIntrinsicMetric) return Constraints.Infinity
    val px = with(density) { dp.roundToPx() }
    if (px >= 0 || px == Constraints.Infinity) return px
    throw IllegalArgumentException("Invalid constraint size: $this")
}
