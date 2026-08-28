/*
 * Copyright 2024 The Android Open Source Project
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.animation.withAnimationProgress
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.PlatformInsets
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrDefault
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun OffsetToFocusedRect(
    insets: PlatformInsets,
    getFocusedRect: () -> Rect?,
    size: IntSize?,
    animationDuration: Duration,
    animationCompletion: () -> Unit,
    content: @Composable () -> Unit,
) = Layout(
    modifier = Modifier.then(
        OffsetToFocusedRectElement(
            insets = insets,
            getFocusedRect = getFocusedRect,
            size = size,
            animationDuration = animationDuration,
            animationCompletion = animationCompletion,
        )
    ),
    content = content,
    measurePolicy = { measurables, constraints ->
        val placeables = measurables.fastMap { it.measure(constraints) }
        layout(
            placeables.fastMaxOfOrDefault(constraints.minWidth) { it.width },
            placeables.fastMaxOfOrDefault(constraints.minHeight) { it.height }
        ) {
            placeables.fastForEach {
                it.place(IntOffset.Zero)
            }
        }
    }
)

internal fun adjustedToFocusedRectOffset(
    insets: PlatformInsets,
    focusedRect: Rect?,
    size: IntSize?
): IntOffset {
    focusedRect ?: return IntOffset.Zero
    size ?: return IntOffset.Zero
    if (insets == PlatformInsets.Zero) {
        return IntOffset.Zero
    }

    val visibleAfterOffset = Rect(offset = Offset.Zero, size = size.toSize())
        .intersect(focusedRect)
        .isEmpty
        .not()

    return if (visibleAfterOffset) {
        IntOffset(
            x = directionalFocusOffset(
                contentSize = size.width.toFloat(),
                contentInsetStart = insets.left.toFloat(),
                contentInsetEnd = insets.right.toFloat(),
                focusStart = focusedRect.left,
                focusEnd = focusedRect.right
            ),
            y = directionalFocusOffset(
                contentSize = size.height.toFloat(),
                contentInsetStart = insets.top.toFloat(),
                contentInsetEnd = insets.bottom.toFloat(),
                focusStart = focusedRect.top,
                focusEnd = focusedRect.bottom,
            )
        )
    } else {
        IntOffset.Zero
    }
}

private fun directionalFocusOffset(
    contentSize: Float,
    contentInsetStart: Float,
    contentInsetEnd: Float,
    focusStart: Float,
    focusEnd: Float
): Int {
    val hiddenFromPart = contentInsetStart - max(focusStart, 0f)
    val hiddenToPart = contentInsetEnd - contentSize + min(focusEnd, contentSize)

    return if (hiddenFromPart >= 0 && hiddenToPart >= 0) {
        0
    } else if (hiddenToPart < 0) {
        max(0f, min(hiddenFromPart, -hiddenToPart)).roundToInt()
    } else {
        min(0f, max(hiddenFromPart, -hiddenToPart)).roundToInt()
    }
}

private data class OffsetToFocusedRectElement(
    val insets: PlatformInsets,
    val getFocusedRect: () -> Rect?,
    val size: IntSize?,
    val animationDuration: Duration,
    val animationCompletion: () -> Unit,
) : ModifierNodeElement<OffsetToFocusedRectNode>() {
    override fun create() = OffsetToFocusedRectNode(
        insets = insets,
        getFocusedRect = getFocusedRect,
        size = size,
        animationDuration = animationDuration,
        animationCompletion = animationCompletion,
    )

    override fun update(node: OffsetToFocusedRectNode) {
        node.update(
            insets = insets,
            getFocusedRect = getFocusedRect,
            size = size,
            animationDuration = animationDuration,
            animationCompletion = animationCompletion,
        )
    }
}

private class OffsetToFocusedRectNode(
    private var insets: PlatformInsets,
    private var getFocusedRect: () -> Rect?,
    private var size: IntSize?,
    private var animationDuration: Duration,
    private var animationCompletion: () -> Unit,
) : Modifier.Node(), GlobalPositionAwareModifierNode, LayoutModifierNode {
    private var currentOffset = IntOffset.Zero
    private var startOffset = IntOffset.Zero
    private var offsetProgress = 1f
    private var animationJob: Job? = null

    override val shouldAutoInvalidate: Boolean = false

    fun update(
        insets: PlatformInsets,
        getFocusedRect: () -> Rect?,
        size: IntSize?,
        animationDuration: Duration,
        animationCompletion: () -> Unit,
    ) {
        val animationInputsChanged =
            this.insets != insets || this.animationDuration != animationDuration
        val needsRemeasure =
            animationInputsChanged || this.getFocusedRect !== getFocusedRect || this.size != size

        this.insets = insets
        this.getFocusedRect = getFocusedRect
        this.size = size
        this.animationDuration = animationDuration
        this.animationCompletion = animationCompletion

        if (!isAttached) return
        if (animationInputsChanged) {
            restartAnimation()
        } else if (needsRemeasure) {
            invalidateMeasurement()
        }
    }

    override fun onAttach() {
        restartAnimation()
    }

    override fun onDetach() {
        animationJob?.cancel()
        animationJob = null
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        currentOffset = calculatedOffset()
        return layout(placeable.width, placeable.height) {
            placeable.place(currentOffset)
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // The first point at which the focus rect contains both the new offset and a  simultaneous
        // child-layout change (e.g. imePadding). Settle only when that changes the offset.
        val settledOffset = calculatedOffset()
        if (settledOffset != currentOffset) {
            currentOffset = settledOffset
            invalidateMeasurement()
        }
    }

    private fun restartAnimation() {
        animationJob?.cancel()
        startOffset = currentOffset

        if (!animationDuration.isPositive()) {
            offsetProgress = 1f
            invalidateMeasurement()
            return
        }

        if (startOffset == targetOffset()) {
            offsetProgress = 1f
            animationCompletion()
            invalidateMeasurement()
            return
        }

        animationJob = coroutineScope.launch {
            withAnimationProgress(animationDuration) { progress ->
                offsetProgress = progress
                invalidateMeasurement()
            }
            animationCompletion()
        }
    }

    private fun calculatedOffset(): IntOffset =
        startOffset + (targetOffset() - startOffset) * offsetProgress

    private fun targetOffset(): IntOffset {
        // The current implementation of OffsetToFocusedRect assumes that the focus rectangle is
        // either static or only changes during the animation.
        // The [Snapshot.withoutReadObservation] function is used here to avoid side effects caused
        // by the internal implementation of the [getFocusedRect] function.
        val focusedRect = Snapshot.withoutReadObservation { getFocusedRect() }
            ?.translate(-currentOffset.toOffset())

        return adjustedToFocusedRectOffset(
            insets = insets,
            focusedRect = focusedRect,
            size = size,
        )
    }
}
