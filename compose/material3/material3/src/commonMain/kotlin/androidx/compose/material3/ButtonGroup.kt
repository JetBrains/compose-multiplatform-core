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

package androidx.compose.material3

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.tokens.ButtonGroupSmallTokens
import androidx.compose.material3.tokens.ConnectedButtonGroupSmallTokens
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.material3.tokens.ShapeTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.fastRoundToInt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// TODO link to mio page when available.
// TODO link to an image when available
/**
 * A layout composable that places its children in a horizontal sequence. When a child uses
 * [Modifier.interactionSourceData] with a relevant [MutableInteractionSource], this button group
 * can listen to the interactions and expand the width of the pressed child element as well as
 * compress the neighboring child elements. Material3 components already use
 * [Modifier.interactionSourceData] and will behave as expected.
 *
 * @sample androidx.compose.material3.samples.ButtonGroupSample
 *
 * A connected button group is a variant of a button group that have leading and trailing buttons
 * that are asymmetric in shape and are used to make a selection.
 *
 * @sample androidx.compose.material3.samples.MultiSelectConnectedButtonGroupSample
 * @sample androidx.compose.material3.samples.SingleSelectConnectedButtonGroupSample
 * @param modifier the [Modifier] to be applied to the button group.
 * @param expandedRatio the percentage, represented by a float, of the width of the interacted child
 *   element that will be used to expand the interacted child element as well as compress the
 *   neighboring children. By Default, standard button group will expand the interacted child
 *   element by [ButtonGroupDefaults.ExpandedRatio] of its width and this will be propagated to its
 *   neighbors. If 0f is passed into this slot, then the interacted child element will not expand at
 *   all and the neighboring elements will not compress. If 1f is passed into this slot, then the
 *   interacted child element will expand to 200% of its default width when pressed.
 * @param horizontalArrangement The horizontal arrangement of the button group's children.
 * @param content the content displayed in the button group, expected to use a Material3 component
 *   or a composable that is tagged with [Modifier.interactionSourceData].
 */
@Composable
@ExperimentalMaterial3ExpressiveApi
fun ButtonGroup(
    modifier: Modifier = Modifier,
    @FloatRange(0.0) expandedRatio: Float = ButtonGroupDefaults.ExpandedRatio,
    horizontalArrangement: Arrangement.Horizontal = ButtonGroupDefaults.HorizontalArrangement,
    content: @Composable ButtonGroupScope.() -> Unit
) {
    // TODO Load the motionScheme tokens from the component tokens file
    val defaultAnimationSpec = MotionSchemeKeyTokens.FastSpatial.value<Float>()
    val scope = remember {
        object : ButtonGroupScope {
            @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
            override fun Modifier.weight(weight: Float, fill: Boolean): Modifier =
                this.weight(weight)

            override fun Modifier.weight(weight: Float): Modifier {
                require(weight > 0.0) { "invalid weight $weight; must be greater than zero" }
                return this.then(
                    ButtonGroupElement(
                        // Coerce Float.POSITIVE_INFINITY to Float.MAX_VALUE to avoid errors
                        weight = weight.coerceAtMost(Float.MAX_VALUE)
                    )
                )
            }

            override fun Modifier.animateWidth(interactionSource: InteractionSource): Modifier =
                this.then(
                    EnlargeOnPressElement(
                        interactionSource = interactionSource,
                        animationSpec = defaultAnimationSpec
                    )
                )
        }
    }

    val measurePolicy =
        remember(horizontalArrangement) {
            ButtonGroupMeasurePolicy(
                horizontalArrangement = horizontalArrangement,
                expandedRatio = expandedRatio
            )
        }

    Layout(measurePolicy = measurePolicy, modifier = modifier, content = { scope.content() })
}

/** Default values used by [ButtonGroup] */
@ExperimentalMaterial3ExpressiveApi
object ButtonGroupDefaults {
    /**
     * The default percentage, represented as a float, of the width of the interacted child element
     * that will be used to expand the interacted child element as well as compress the neighboring
     * children. By Default, standard button group will expand the interacted child element by 15%
     * of its width and this will be propagated to its neighbors.
     */
    val ExpandedRatio = 0.15f

    /** The default Arrangement used between children for standard button group. */
    val HorizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(ButtonGroupSmallTokens.BetweenSpace)

    /** The default spacing used between children for connected button group */
    val ConnectedSpaceBetween: Dp = ConnectedButtonGroupSmallTokens.BetweenSpace

    /** Default shape for the leading button in a connected button group */
    val connectedLeadingButtonShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = ShapeDefaults.CornerFull,
                bottomStart = ShapeDefaults.CornerFull,
                topEnd = ConnectedButtonGroupSmallTokens.InnerCornerCornerSize,
                bottomEnd = ConnectedButtonGroupSmallTokens.InnerCornerCornerSize
            )

    /** Default shape for the pressed state for the leading button in a connected button group. */
    val connectedLeadingButtonPressShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topStart = ShapeDefaults.CornerFull,
                bottomStart = ShapeDefaults.CornerFull,
                topEnd = ConnectedButtonGroupSmallTokens.PressedInnerCornerCornerSize,
                bottomEnd = ConnectedButtonGroupSmallTokens.PressedInnerCornerCornerSize
            )

    /** Default shape for the trailing button in a connected button group */
    val connectedTrailingButtonShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topEnd = ShapeDefaults.CornerFull,
                bottomEnd = ShapeDefaults.CornerFull,
                topStart = ConnectedButtonGroupSmallTokens.InnerCornerCornerSize,
                bottomStart = ConnectedButtonGroupSmallTokens.InnerCornerCornerSize
            )

    /** Default shape for the pressed state for the trailing button in a connected button group. */
    val connectedTrailingButtonPressShape: Shape
        @Composable
        get() =
            RoundedCornerShape(
                topEnd = ShapeDefaults.CornerFull,
                bottomEnd = ShapeDefaults.CornerFull,
                topStart = ConnectedButtonGroupSmallTokens.PressedInnerCornerCornerSize,
                bottomStart = ConnectedButtonGroupSmallTokens.PressedInnerCornerCornerSize
            )

    /** Default shape for the checked state for the buttons in a connected button group */
    val connectedButtonCheckedShape = ShapeTokens.CornerFull

    /** Default shape for the pressed state for the middle buttons in a connected button group. */
    val connectedMiddleButtonPressShape: Shape
        @Composable
        get() = RoundedCornerShape(ConnectedButtonGroupSmallTokens.PressedInnerCornerCornerSize)

    /** Defaults button shapes for the start button in a [ConnectedButtonGroup] */
    @Composable
    fun connectedLeadingButtonShapes(
        shape: Shape = connectedLeadingButtonShape,
        pressedShape: Shape = connectedLeadingButtonPressShape,
        checkedShape: Shape = connectedButtonCheckedShape
    ): ToggleButtonShapes =
        ToggleButtonShapes(shape = shape, pressedShape = pressedShape, checkedShape = checkedShape)

    /**
     * Defaults button shapes for a middle button in a [ConnectedButtonGroup]. A middle button is a
     * button that's not the start / end button in the button group.
     */
    @Composable
    fun connectedMiddleButtonShapes(
        shape: Shape = ShapeDefaults.Small,
        pressedShape: Shape = connectedMiddleButtonPressShape,
        checkedShape: Shape = connectedButtonCheckedShape
    ): ToggleButtonShapes =
        ToggleButtonShapes(shape = shape, pressedShape = pressedShape, checkedShape = checkedShape)

    /** Defaults button shapes for the end button in a [ConnectedButtonGroup]. */
    @Composable
    fun connectedTrailingButtonShapes(
        shape: Shape = connectedTrailingButtonShape,
        pressedShape: Shape = connectedTrailingButtonPressShape,
        checkedShape: Shape = connectedButtonCheckedShape
    ): ToggleButtonShapes =
        ToggleButtonShapes(shape = shape, pressedShape = pressedShape, checkedShape = checkedShape)
}

private class ButtonGroupMeasurePolicy(
    val horizontalArrangement: Arrangement.Horizontal,
    val expandedRatio: Float
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints
    ): MeasureResult {
        val arrangementSpacingInt = horizontalArrangement.spacing.roundToPx()
        val arrangementSpacingPx = arrangementSpacingInt.toLong()
        val size = measurables.size
        var totalWeight = 0f
        var fixedSpace = 0
        var weightChildrenCount = 0
        val placeables: List<Placeable>
        val childrenMainAxisSize = IntArray(size)
        val childrenConstraints: Array<Constraints?> = arrayOfNulls(size)
        val configs =
            Array(measurables.size) {
                measurables[it].parentData as? ButtonGroupParentData ?: ButtonGroupParentData()
            }
        val animatables = Array(measurables.size) { configs[it].pressedAnimatable }

        val mainAxisMin = constraints.minWidth
        val mainAxisMax = constraints.maxWidth

        // First obtain constraints of children with zero weight
        var spaceAfterLastNoWeight = 0
        for (i in 0 until size) {
            val child = measurables[i]
            val parentData = child.buttonGroupParentData
            val weight = parentData.weight

            if (weight > 0f) {
                totalWeight += weight
                ++weightChildrenCount
            } else {
                val remaining = mainAxisMax - fixedSpace
                val desiredWidth = child.maxIntrinsicWidth(constraints.maxHeight)
                childrenConstraints[i] =
                    constraints.copy(
                        minWidth = 0,
                        maxWidth =
                            if (mainAxisMax == Constraints.Infinity) {
                                Constraints.Infinity
                            } else {
                                desiredWidth.coerceAtLeast(0)
                            }
                    )

                childrenMainAxisSize[i] = desiredWidth

                spaceAfterLastNoWeight =
                    min(arrangementSpacingInt, (remaining - desiredWidth).coerceAtLeast(0))

                fixedSpace += desiredWidth + spaceAfterLastNoWeight
            }
        }

        var weightedSpace = 0
        if (weightChildrenCount == 0) {
            // fixedSpace contains an extra spacing after the last non-weight child.
            fixedSpace -= spaceAfterLastNoWeight
        } else {
            // obtain the constraints of the rest according to their weights.
            val targetSpace =
                if (mainAxisMax != Constraints.Infinity) {
                    mainAxisMax
                } else {
                    mainAxisMin
                }

            val arrangementSpacingTotal = arrangementSpacingPx * (weightChildrenCount - 1)
            val remainingToTarget =
                (targetSpace - fixedSpace - arrangementSpacingTotal).coerceAtLeast(0)

            val weightUnitSpace = remainingToTarget / totalWeight
            var remainder = remainingToTarget
            for (i in 0 until size) {
                val measurable = measurables[i]
                val itemWeight = measurable.buttonGroupParentData.weight
                val weightedSize = (weightUnitSpace * itemWeight)
                remainder -= weightedSize.fastRoundToInt()
            }

            for (i in 0 until size) {
                if (childrenConstraints[i] == null) {
                    val child = measurables[i]
                    val parentData = child.buttonGroupParentData
                    val weight = parentData.weight

                    // After the weightUnitSpace rounding, the total space going to be occupied
                    // can be smaller or larger than remainingToTarget. Here we distribute the
                    // loss or gain remainder evenly to the first children.
                    val remainderUnit = remainder.sign
                    remainder -= remainderUnit
                    val weightedSize = (weightUnitSpace * weight)

                    val childMainAxisSize = max(0, weightedSize.fastRoundToInt() + remainderUnit)

                    childrenConstraints[i] =
                        constraints.copy(
                            minWidth =
                                if (childMainAxisSize != Constraints.Infinity) {
                                    childMainAxisSize
                                } else {
                                    0
                                },
                            maxWidth = childMainAxisSize
                        )

                    childrenMainAxisSize[i] = childMainAxisSize
                    weightedSpace += childMainAxisSize
                }
                weightedSpace =
                    (weightedSpace + arrangementSpacingTotal)
                        .toInt()
                        .coerceIn(0, mainAxisMax - fixedSpace)
            }
        }

        val widths =
            IntArray(measurables.size) { (childrenConstraints[it] ?: constraints).maxWidth }

        if (measurables.size > 1) {
            for (index in measurables.indices) {
                val growth = animatables[index].value * expandedRatio * widths[index]
                if (index in 1 until measurables.lastIndex) {
                    widths[index - 1] -= (growth / 2f).roundToInt()
                    widths[index + 1] -= (growth / 2).roundToInt()
                } else {
                    if (index == 0) {
                        widths[index + 1] -= growth.roundToInt()
                    } else {
                        widths[index - 1] -= growth.roundToInt()
                    }
                }

                widths[index] += growth.roundToInt()
            }
        }

        placeables =
            measurables.fastMapIndexed { index, placeable ->
                placeable.measure(
                    constraints.copy(minWidth = widths[index], maxWidth = widths[index])
                )
            }

        // Compute the row size and position the children.
        val mainAxisLayoutSize = max((fixedSpace + weightedSpace).coerceAtLeast(0), mainAxisMin)
        val mainAxisPositions = IntArray(size)
        val measureScope = this
        with(horizontalArrangement) {
            measureScope.arrange(
                mainAxisLayoutSize,
                childrenMainAxisSize,
                measureScope.layoutDirection,
                mainAxisPositions
            )
        }

        val height = placeables.fastMaxBy { it.height }?.height ?: constraints.minHeight
        return layout(mainAxisLayoutSize, height) {
            var currentX = 0
            placeables.fastForEach {
                it.place(currentX, 0)
                currentX += it.width + arrangementSpacingInt
            }
        }
    }
}

/** Button group scope used to indicate a [Modifier.weight] of a child element. */
@ExperimentalMaterial3ExpressiveApi
interface ButtonGroupScope {
    /**
     * Size the element's width proportional to its [weight] relative to other weighted sibling
     * elements in the [ButtonGroup]. The parent will divide the horizontal space remaining after
     * measuring unweighted child elements and distribute it according to this weight. When [fill]
     * is true, the element will be forced to occupy the whole width allocated to it. Otherwise, the
     * element is allowed to be smaller - this will result in [ButtonGroup] being smaller, as the
     * unused allocated width will not be redistributed to other siblings.
     *
     * @param weight The proportional width to give to this element, as related to the total of all
     *   weighted siblings. Must be positive.
     * @param fill When `true`, the element will occupy the whole width allocated.
     */
    @Deprecated("For binary compatibility", level = DeprecationLevel.HIDDEN)
    fun Modifier.weight(
        @FloatRange(from = 0.0, fromInclusive = false) weight: Float,
        fill: Boolean = true
    ): Modifier

    /**
     * Size the element's width proportional to its [weight] relative to other weighted sibling
     * elements in the [ButtonGroup]. The parent will divide the horizontal space remaining after
     * measuring unweighted child elements and distribute it according to this weight.
     *
     * @param weight The proportional width to give to this element, as related to the total of all
     *   weighted siblings. Must be positive.
     */
    fun Modifier.weight(@FloatRange(from = 0.0, fromInclusive = false) weight: Float): Modifier

    /**
     * Specifies the interaction source to use with this item. This is used to listen to events and
     * animate growing the pressed button and shrink the neighbor(s).
     *
     * @param interactionSource the [InteractionSource] that button group will observe.
     */
    fun Modifier.animateWidth(interactionSource: InteractionSource): Modifier
}

internal val IntrinsicMeasurable.buttonGroupParentData: ButtonGroupParentData?
    get() = parentData as? ButtonGroupParentData

internal val ButtonGroupParentData?.weight: Float
    get() = this?.weight ?: 0f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal data class ButtonGroupParentData(
    var weight: Float = 0f,
    var pressedAnimatable: Animatable<Float, AnimationVector1D> = Animatable(0f)
)

internal class ButtonGroupElement(val weight: Float = 0f) : ModifierNodeElement<ButtonGroupNode>() {
    override fun create(): ButtonGroupNode {
        return ButtonGroupNode(weight)
    }

    override fun update(node: ButtonGroupNode) {
        node.weight = weight
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "weight"
        value = weight
        properties["weight"] = weight
    }

    override fun hashCode(): Int {
        return weight.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? ButtonGroupElement ?: return false
        return weight == otherModifier.weight
    }
}

internal class ButtonGroupNode(var weight: Float) : ParentDataModifierNode, Modifier.Node() {
    override fun Density.modifyParentData(parentData: Any?) =
        ((parentData as? ButtonGroupParentData) ?: ButtonGroupParentData()).also {
            it.weight = weight
        }
}

internal class EnlargeOnPressElement(
    val interactionSource: InteractionSource,
    val animationSpec: AnimationSpec<Float>,
) : ModifierNodeElement<EnlargeOnPressNode>() {

    override fun create(): EnlargeOnPressNode {
        return EnlargeOnPressNode(interactionSource, animationSpec)
    }

    override fun update(node: EnlargeOnPressNode) {
        if (node.interactionSource != interactionSource) {
            node.interactionSource = interactionSource
            node.launchCollectionJob()
        }
        node.animationSpec = animationSpec
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "EnlargeOnPressElement"
        properties["interactionSource"] = interactionSource
        properties["animationSpec"] = animationSpec
    }

    override fun hashCode() = interactionSource.hashCode() * 31 + animationSpec.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? EnlargeOnPressNode ?: return false
        return interactionSource == otherModifier.interactionSource &&
            animationSpec == otherModifier.animationSpec
    }
}

internal class EnlargeOnPressNode(
    var interactionSource: InteractionSource,
    var animationSpec: AnimationSpec<Float>
) : ParentDataModifierNode, Modifier.Node() {
    private val pressedAnimatable: Animatable<Float, AnimationVector1D> = Animatable(0f)

    private var collectionJob: Job? = null

    override fun onAttach() {
        super.onAttach()

        launchCollectionJob()
    }

    override fun onDetach() {
        super.onDetach()
        collectionJob = null
    }

    internal fun launchCollectionJob() {
        collectionJob?.cancel()
        collectionJob =
            coroutineScope.launch {
                launch {
                    // Use collect here to ensure we don't lose any events.
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                coroutineScope.launch {
                                    pressedAnimatable.animateTo(1f, animationSpec)
                                }
                            }
                            is PressInteraction.Release,
                            is PressInteraction.Cancel -> {
                                coroutineScope.launch {
                                    pressedAnimatable.animateTo(0f, animationSpec)
                                }
                            }
                        }
                    }
                }
            }
    }

    override fun Density.modifyParentData(parentData: Any?) =
        (parentData as? ButtonGroupParentData).let { prev ->
            ButtonGroupParentData(prev.weight, pressedAnimatable)
        }
}
