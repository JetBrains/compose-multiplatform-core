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

package androidx.xr.compose.subspace

import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastRoundToInt
import androidx.xr.compose.subspace.layout.SpatialAlignment
import androidx.xr.compose.subspace.layout.SubspaceMeasurable
import androidx.xr.compose.subspace.layout.SubspaceMeasureResult
import androidx.xr.compose.subspace.layout.SubspaceMeasureScope
import androidx.xr.compose.subspace.layout.SubspacePlaceable
import androidx.xr.compose.unit.IntVolumeSize
import androidx.xr.compose.unit.VolumeConstraints
import androidx.xr.compose.unit.constrainDepth
import androidx.xr.compose.unit.constrainHeight
import androidx.xr.compose.unit.constrainWidth
import androidx.xr.runtime.math.Pose
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * Base class for measure policies of [SpatialRow] and [SpatialColumn]. It encapsulates the common
 * logic for measuring and placing children along a main axis, handling weights, arrangements, and
 * basic alignment.
 *
 * Subclasses must define axis-specific details like main/cross axis sizes and how final poses are
 * computed.
 */
internal abstract class SpatialRowColumnMeasurePolicy {

    /** Returns the size of the placeable along the main layout axis. */
    abstract val SubspacePlaceable.mainAxisSize: Int
    /** Returns the size of the placeable along the cross layout axis. */
    abstract val SubspacePlaceable.crossAxisSize: Int
    /**
     * The target space is the amount of space that the content should take up on the main axis.
     * This is based on the constraints of the container.
     */
    abstract val VolumeConstraints.mainAxisTargetSpace: Int
    /** Returns the minimum constraint for the main axis. */
    abstract val VolumeConstraints.mainAxisMin: Int
    /** Returns the minimum constraint for the cross axis. */
    abstract val VolumeConstraints.crossAxisMin: Int
    /** Returns the maximum constraint for the cross axis. */
    abstract val VolumeConstraints.crossAxisMax: Int

    /**
     * Arranges the children's positions along the main axis based on the total main axis size and
     * the specific arrangement logic provided by concrete implementations (e.g., SpatialRow's
     * horizontal arrangement).
     *
     * @param mainAxisLayoutSize The total size available or occupied along the main axis.
     * @param childrenMainAxisSize An array containing the main axis sizes of each child.
     * @param mainAxisPositions An output array to be populated with the calculated main axis
     *   position for each child.
     * @param subspaceMeasureScope The scope providing layout direction and arrangement helpers.
     */
    abstract fun arrangeMainAxisPositions(
        mainAxisLayoutSize: Int,
        childrenMainAxisSize: IntArray,
        mainAxisPositions: IntArray,
        subspaceMeasureScope: SubspaceMeasureScope,
    )

    /**
     * Calculates the offset required to position the block of content (all children) along the main
     * axis within the container, according to the specified alignment.
     *
     * @param contentSize The total size of the content block.
     * @param containerSize The total size of the container.
     * @return The offset along the main axis.
     */
    abstract fun getMainAxisOffset(contentSize: IntVolumeSize, containerSize: IntVolumeSize): Int

    /**
     * Creates [VolumeConstraints] for a child, given specific constraints for main axis, cross
     * axis, and depth.
     */
    abstract fun buildConstraints(
        mainAxisMin: Int,
        mainAxisMax: Int,
        crossAxisMin: Int,
        crossAxisMax: Int,
        minDepth: Int,
        maxDepth: Int,
    ): VolumeConstraints

    /**
     * Modifies the given [VolumeConstraints] by adding a value to the main axis constraints. This
     * is typically used when measuring children without weight, to subtract the space already
     * occupied by fixed-size children.
     */
    abstract fun VolumeConstraints.plusMainAxis(addToMainAxis: Int): VolumeConstraints

    /**
     * Core measurement logic for children. It resolves measurables, determines content size, and
     * then places children using [placeHelper].
     */
    internal fun measure(
        measurables: List<SubspaceMeasurable>,
        constraints: VolumeConstraints,
        arrangementSpacingInt: Int,
        mainAxisMultiplier: MainAxisMultiplier,
        subspaceMeasureScope: SubspaceMeasureScope,
    ): SubspaceMeasureResult {
        val resolvedMeasurables = measurables.fastMap { ResolvedMeasurable(it) }

        val contentSize =
            measureMainAxisChildrenSize(
                resolvedMeasurables = resolvedMeasurables,
                constraints = constraints,
                arrangementSpacingInt = arrangementSpacingInt,
                subspaceMeasureScope = subspaceMeasureScope,
                mainAxisMultiplier = mainAxisMultiplier,
            )
        val containerSize =
            IntVolumeSize(
                width = constraints.constrainWidth(contentSize.width),
                height = constraints.constrainHeight(contentSize.height),
                depth = constraints.constrainDepth(contentSize.depth),
            )

        return placeHelper(contentSize, containerSize, resolvedMeasurables, subspaceMeasureScope)
    }

    /**
     * Measures the children of a Row or Column.
     *
     * This method will measure the children of a Row or Column in two passes. In the first pass,
     * children with no weight modifier will be measured. In the second pass, children with weight
     * modifiers will be measured.
     *
     * After measuring, the placeable of each child will be set.
     */
    private fun measureMainAxisChildrenSize(
        resolvedMeasurables: List<ResolvedMeasurable>,
        constraints: VolumeConstraints,
        arrangementSpacingInt: Int = 0,
        subspaceMeasureScope: SubspaceMeasureScope,
        mainAxisMultiplier: MainAxisMultiplier,
    ): IntVolumeSize {
        // Space taken up on the main axis by children with no weight modifier.
        var fixedSpace = 0
        // The total amount of weight declared across all children.
        var totalWeight = 0f
        // The total number of weighted children
        var totalWeightedChildren = 0

        val arrangementSpacingPx = arrangementSpacingInt.toLong()
        var spaceAfterLastNoWeight = 0
        val childrenMainAxisSize = IntArray(resolvedMeasurables.size)

        // Content's cross-axis and depth size are the max measured value of all children
        var crossAxisSize = 0
        var depthSize = 0

        // We will measure non-weighted children in this pass.
        resolvedMeasurables.fastForEachIndexed { index, resolvedMeasurable ->
            if (resolvedMeasurable.weightInfo.weight > 0f) {
                // Children with weight will be measured after all others.
                totalWeight += resolvedMeasurable.weightInfo.weight
                totalWeightedChildren++
            } else {
                // Children without weight will be measured now.
                val remaining = constraints.mainAxisTargetSpace - fixedSpace
                resolvedMeasurable.placeable =
                    resolvedMeasurable.measurable
                        .measure(constraints.plusMainAxis(-fixedSpace))
                        .also {
                            childrenMainAxisSize[index] = it.mainAxisSize
                            spaceAfterLastNoWeight =
                                min(
                                    arrangementSpacingInt,
                                    (remaining - it.mainAxisSize).coerceAtLeast(0),
                                )
                            fixedSpace += it.mainAxisSize + spaceAfterLastNoWeight
                        }
                crossAxisSize = maxOf(crossAxisSize, resolvedMeasurable.placeable!!.crossAxisSize)
                depthSize = maxOf(depthSize, resolvedMeasurable.placeable!!.measuredDepth)
            }
        }

        // Now we can measure the weighted children (if any).
        var weightedSpace = 0
        if (totalWeightedChildren == 0) {
            fixedSpace -= spaceAfterLastNoWeight
        } else {
            // Amount of space this Row/Column wants to fill up.
            val targetSpace = constraints.mainAxisTargetSpace
            val arrangementSpacingTotal = arrangementSpacingPx * (totalWeightedChildren - 1)
            // Amount of space left (after the non-weighted children were measured).
            val remainingToTarget =
                (targetSpace - fixedSpace - arrangementSpacingTotal).coerceAtLeast(0)
            // Amount of space that would be given to a weighted child with `.weight(1f)`.
            val weightUnitSpace = if (totalWeight > 0) remainingToTarget / totalWeight else 0f

            // Distribute rounding errors for weighted children
            // Due to rounding, we may over/underfill the container.
            //
            // For example, if we have a 200dp row with 7 children, each with a weight of 1f:
            // 200/7 ~= 28.57..., which gets rounded to 29. But 29*7 = 203, so we've overfilled our
            // 200dp row by 3dp.
            //
            // The fix is to track this remainder N, and adjust the first N children by 1dp or -1dp
            // so that the row/column will be the exact size we need.
            var remainder = remainingToTarget
            resolvedMeasurables.fastForEach { resolvedMeasurable ->
                if (resolvedMeasurable.weightInfo.weight > 0f) {
                    remainder -=
                        (resolvedMeasurable.weightInfo.weight * weightUnitSpace).fastRoundToInt()
                }
            }

            resolvedMeasurables.fastForEachIndexed { index, resolvedMeasurable ->
                if (resolvedMeasurable.weightInfo.weight <= 0f) return@fastForEachIndexed
                val childMainAxisSize = run {
                    val remainderUnit = remainder.sign
                    remainder -= remainderUnit
                    val weightedSize = resolvedMeasurable.weightInfo.weight * weightUnitSpace
                    weightedSize.fastRoundToInt() + remainderUnit
                }
                val childConstraints =
                    buildConstraints(
                        mainAxisMin =
                            if (resolvedMeasurable.weightInfo.fill) childMainAxisSize else 0,
                        mainAxisMax = childMainAxisSize,
                        crossAxisMin = constraints.crossAxisMin,
                        crossAxisMax = constraints.crossAxisMax,
                        minDepth = constraints.minDepth,
                        maxDepth = constraints.maxDepth,
                    )
                resolvedMeasurable.placeable =
                    resolvedMeasurable.measurable.measure(childConstraints).also {
                        childrenMainAxisSize[index] = it.mainAxisSize
                        weightedSpace += it.mainAxisSize
                    }
                crossAxisSize = maxOf(crossAxisSize, resolvedMeasurable.placeable!!.crossAxisSize)
                depthSize = maxOf(depthSize, resolvedMeasurable.placeable!!.measuredDepth)
            }
            weightedSpace =
                (weightedSpace + arrangementSpacingTotal)
                    .toInt()
                    .coerceIn(0, constraints.mainAxisTargetSpace - fixedSpace)
        }
        val mainAxisSize = (fixedSpace + weightedSpace).coerceAtLeast(0)
        val mainAxisLayoutSize = max(mainAxisSize, constraints.mainAxisMin)
        // Get children position on main axis based on arrangement
        val mainAxisPositions = IntArray(resolvedMeasurables.size)
        arrangeMainAxisPositions(
            mainAxisLayoutSize,
            childrenMainAxisSize,
            mainAxisPositions,
            subspaceMeasureScope,
        )
        // Populate the main axis positions for each placeable
        resolvedMeasurables.populateMainAxisPositions(mainAxisPositions, mainAxisMultiplier)

        return contentSize(mainAxisSize, crossAxisSize, depthSize)
    }

    /** Populates the `mainAxisPosition` for each [ResolvedMeasurable]. */
    private fun List<ResolvedMeasurable>.populateMainAxisPositions(
        mainAxisPositions: IntArray,
        mainAxisMultiplier: MainAxisMultiplier,
    ) {
        this.fastForEachIndexed { index, resolvedMeasurable ->
            resolvedMeasurable.mainAxisPosition =
                mainAxisPositions[index] * mainAxisMultiplier.value
        }
    }

    /** Helper function to perform layout after content size and main axis offset are known. */
    private fun placeHelper(
        contentSize: IntVolumeSize,
        containerSize: IntVolumeSize,
        resolvedMeasurables: List<ResolvedMeasurable>,
        subspaceMeasureScope: SubspaceMeasureScope,
    ): SubspaceMeasureResult {
        val mainAxisOffset = getMainAxisOffset(contentSize, containerSize)

        return with(subspaceMeasureScope) {
            layout(containerSize.width, containerSize.height, containerSize.depth) {
                resolvedMeasurables.fastForEach { resolvedMeasurable ->
                    val placeable = resolvedMeasurable.placeable!!
                    placeable.place(getPose(resolvedMeasurable, containerSize, mainAxisOffset))
                }
            }
        }
    }

    /** Returns the total size of the content (sum of children sizes and spacing). */
    abstract fun contentSize(
        mainAxisLayoutSize: Int,
        crossAxisSize: Int,
        depthSize: Int,
    ): IntVolumeSize

    /**
     * Returns the pose of the child in the container space.
     *
     * The pose is based on the child's main-axis position, cross-axis position, and depth offset,
     * taking into account the alignment of the child. For [SpatialRow], this may also adjust for
     * curvature if a `curveRadius` is specified.
     */
    abstract fun Density.getPose(
        resolvedMeasurable: ResolvedMeasurable,
        containerSize: IntVolumeSize,
        mainAxisOffset: Int,
    ): Pose
}

/**
 * A [SubspaceMeasurable] and its associated layout data computed during the measure pass of
 * [SpatialRowColumnMeasurePolicy]. This includes weight, alignment overrides, the resulting
 * [SubspacePlaceable], and its calculated main axis position.
 */
internal class ResolvedMeasurable(val measurable: SubspaceMeasurable) {
    /** Parent data related to layout weight, extracted from modifiers. */
    val weightInfo: RowColumnParentData = RowColumnParentData().also { measurable.adjustParams(it) }

    /** Parent data for overriding spatial alignment, extracted from modifiers. */
    val alignment: RowColumnSpatialAlignmentParentData =
        RowColumnSpatialAlignmentParentData().also { measurable.adjustParams(it) }

    /** The [SubspacePlaceable] resulting from measuring the [measurable]. Null until measured. */
    var placeable: SubspacePlaceable? = null

    /**
     * The calculated position of this child along the main axis of the parent. Null until
     * positioned.
     */
    var mainAxisPosition: Int? = null

    /**
     * Calculates the horizontal offset, considering local alignment override or falling back to
     * parent alignment.
     */
    fun horizontalOffset(width: Int, space: Int, parentSpatialAlignment: SpatialAlignment): Int =
        alignment.horizontalSpatialAlignment?.offset(width, space)
            ?: parentSpatialAlignment.horizontalOffset(width, space)

    /**
     * Calculates the vertical offset, considering local alignment override or falling back to
     * parent alignment.
     */
    fun verticalOffset(height: Int, space: Int, parentSpatialAlignment: SpatialAlignment): Int =
        alignment.verticalSpatialAlignment?.offset(height, space)
            ?: parentSpatialAlignment.verticalOffset(height, space)

    /**
     * Calculates the depth offset, considering local alignment override or falling back to parent
     * alignment.
     */
    fun depthOffset(depth: Int, space: Int, parentSpatialAlignment: SpatialAlignment): Int =
        alignment.depthSpatialAlignment?.offset(depth, space)
            ?: parentSpatialAlignment.depthOffset(depth, space)

    override fun toString(): String {
        return measurable.toString()
    }
}

/**
 * Specifies the direction multiplier for the main axis. [HorizontalAxisMultiplier] is typically
 * used for horizontal layouts (e.g., SpatialRow), and [VerticalAxisMultiplier] for vertical layouts
 * (e.g., SpatialColumn).
 */
internal enum class MainAxisMultiplier(val value: Int) {
    HorizontalAxisMultiplier(1),
    VerticalAxisMultiplier(-1),
}
