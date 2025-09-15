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

package androidx.compose.material3

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.internal.ProvideContentColorTextStyle
import androidx.compose.material3.internal.heightOrZero
import androidx.compose.material3.internal.rememberAnimatedShape
import androidx.compose.material3.internal.subtractConstraintSafely
import androidx.compose.material3.internal.widthOrZero
import androidx.compose.material3.tokens.ColorSchemeKeyTokens
import androidx.compose.material3.tokens.ElevationTokens
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.material3.tokens.ShapeKeyTokens
import androidx.compose.material3.tokens.TypographyKeyTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.MultiContentMeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Composable
internal fun ClickableListItem(
    modifier: Modifier = Modifier,

    // slots
    headlineContent: @Composable () -> Unit, // TODO: should this be `content` trailing lambda?
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,

    // interaction
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,

    // styling
    colors: InteractiveListItemColors = InteractiveListItemDefaults.colors(),
    shapes: InteractiveListItemShapes = InteractiveListItemDefaults.shapes(),
    elevation: InteractiveListItemElevation = InteractiveListItemDefaults.elevation(),
    contentPadding: PaddingValues = InteractiveListPadding,
) {
    InteractiveListItem(
        modifier = modifier,
        headlineContent = headlineContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        enabled = enabled,
        selected = false,
        applySemantics = {},
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        interactionSource = interactionSource,
        colors = colors,
        shapes = shapes,
        elevation = elevation,
        contentPadding = contentPadding,
    )
}

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Composable
internal fun SelectableListItem(
    modifier: Modifier = Modifier,

    // slots
    headlineContent: @Composable () -> Unit, // TODO: should this be `content` trailing lambda?
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,

    // interaction
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,

    // styling
    colors: InteractiveListItemColors = InteractiveListItemDefaults.colors(),
    shapes: InteractiveListItemShapes = InteractiveListItemDefaults.shapes(),
    elevation: InteractiveListItemElevation = InteractiveListItemDefaults.elevation(),
    contentPadding: PaddingValues = InteractiveListPadding,
) {
    InteractiveListItem(
        modifier = modifier,
        headlineContent = headlineContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        enabled = enabled,
        selected = selected,
        applySemantics = { this.selected = selected },
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        interactionSource = interactionSource,
        colors = colors,
        shapes = shapes,
        elevation = elevation,
        contentPadding = contentPadding,
    )
}

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Composable
internal fun ToggleableListItem(
    modifier: Modifier = Modifier,

    // slots
    headlineContent: @Composable () -> Unit, // TODO: should this be `content` trailing lambda?
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,

    // interaction
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,

    // styling
    colors: InteractiveListItemColors = InteractiveListItemDefaults.colors(),
    shapes: InteractiveListItemShapes = InteractiveListItemDefaults.shapes(),
    elevation: InteractiveListItemElevation = InteractiveListItemDefaults.elevation(),
    contentPadding: PaddingValues = InteractiveListPadding,
) {
    InteractiveListItem(
        modifier = modifier,
        headlineContent = headlineContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        overlineContent = overlineContent,
        supportingContent = supportingContent,
        enabled = enabled,
        selected = checked,
        applySemantics = { toggleableState = ToggleableState(checked) },
        onClick = { onCheckedChange(!checked) },
        onLongClick = onLongClick,
        onLongClickLabel = onLongClickLabel,
        interactionSource = interactionSource,
        colors = colors,
        shapes = shapes,
        elevation = elevation,
        contentPadding = contentPadding,
    )
}

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Immutable
internal class InteractiveListItemColors(
    // default
    val containerColor: Color,
    val headlineColor: Color,
    val leadingIconColor: Color,
    val trailingIconColor: Color,
    val overlineColor: Color,
    val supportingTextColor: Color,
    // selected
    val selectedContainerColor: Color,
    val selectedHeadlineColor: Color,
    val selectedLeadingIconColor: Color,
    val selectedTrailingIconColor: Color,
    val selectedOverlineColor: Color,
    val selectedSupportingTextColor: Color,
    // disabled
    val disabledContainerColor: Color,
    val disabledHeadlineColor: Color,
    val disabledLeadingIconColor: Color,
    val disabledTrailingIconColor: Color,
    val disabledOverlineColor: Color,
    val disabledSupportingTextColor: Color,
    // dragged
    val draggedContainerColor: Color,
    val draggedHeadlineColor: Color,
    val draggedLeadingIconColor: Color,
    val draggedTrailingIconColor: Color,
    val draggedOverlineColor: Color,
    val draggedSupportingTextColor: Color,
) {
    /** TODO: docs */
    fun containerColor(enabled: Boolean, selected: Boolean, dragged: Boolean): Color =
        when {
            !enabled -> disabledContainerColor
            dragged -> draggedContainerColor
            selected -> selectedContainerColor
            else -> containerColor
        }

    /** TODO: docs */
    fun headlineColor(enabled: Boolean, selected: Boolean, dragged: Boolean): Color =
        when {
            !enabled -> disabledHeadlineColor
            dragged -> draggedHeadlineColor
            selected -> selectedHeadlineColor
            else -> headlineColor
        }

    /** TODO: docs */
    fun leadingIconColor(enabled: Boolean, selected: Boolean, dragged: Boolean): Color =
        when {
            !enabled -> disabledLeadingIconColor
            dragged -> draggedLeadingIconColor
            selected -> selectedLeadingIconColor
            else -> leadingIconColor
        }

    /** TODO: docs */
    fun trailingIconColor(enabled: Boolean, selected: Boolean, dragged: Boolean): Color =
        when {
            !enabled -> disabledTrailingIconColor
            dragged -> draggedTrailingIconColor
            selected -> selectedTrailingIconColor
            else -> trailingIconColor
        }

    /** TODO: docs */
    fun overlineColor(enabled: Boolean, selected: Boolean, dragged: Boolean): Color =
        when {
            !enabled -> disabledOverlineColor
            dragged -> draggedOverlineColor
            selected -> selectedOverlineColor
            else -> overlineColor
        }

    /** TODO: docs */
    fun supportingTextColor(enabled: Boolean, selected: Boolean, dragged: Boolean): Color =
        when {
            !enabled -> disabledSupportingTextColor
            dragged -> draggedSupportingTextColor
            selected -> selectedSupportingTextColor
            else -> supportingTextColor
        }

    /** TODO: docs */
    fun copy(
        containerColor: Color = this.containerColor,
        headlineColor: Color = this.headlineColor,
        leadingIconColor: Color = this.leadingIconColor,
        trailingIconColor: Color = this.trailingIconColor,
        overlineColor: Color = this.overlineColor,
        supportingTextColor: Color = this.supportingTextColor,
        selectedContainerColor: Color = this.selectedContainerColor,
        selectedHeadlineColor: Color = this.selectedHeadlineColor,
        selectedLeadingIconColor: Color = this.selectedLeadingIconColor,
        selectedTrailingIconColor: Color = this.selectedTrailingIconColor,
        selectedOverlineColor: Color = this.selectedOverlineColor,
        selectedSupportingTextColor: Color = this.selectedSupportingTextColor,
        disabledContainerColor: Color = this.disabledContainerColor,
        disabledHeadlineColor: Color = this.disabledHeadlineColor,
        disabledLeadingIconColor: Color = this.disabledLeadingIconColor,
        disabledTrailingIconColor: Color = this.disabledTrailingIconColor,
        disabledOverlineColor: Color = this.disabledOverlineColor,
        disabledSupportingTextColor: Color = this.disabledSupportingTextColor,
        draggedContainerColor: Color = this.draggedContainerColor,
        draggedHeadlineColor: Color = this.draggedHeadlineColor,
        draggedLeadingIconColor: Color = this.draggedLeadingIconColor,
        draggedTrailingIconColor: Color = this.draggedTrailingIconColor,
        draggedOverlineColor: Color = this.draggedOverlineColor,
        draggedSupportingTextColor: Color = this.draggedSupportingTextColor,
    ): InteractiveListItemColors {
        return InteractiveListItemColors(
            containerColor = containerColor.takeOrElse { this.containerColor },
            headlineColor = headlineColor.takeOrElse { this.headlineColor },
            leadingIconColor = leadingIconColor.takeOrElse { this.leadingIconColor },
            trailingIconColor = trailingIconColor.takeOrElse { this.trailingIconColor },
            overlineColor = overlineColor.takeOrElse { this.overlineColor },
            supportingTextColor = supportingTextColor.takeOrElse { this.supportingTextColor },
            selectedContainerColor =
                selectedContainerColor.takeOrElse { this.selectedContainerColor },
            selectedHeadlineColor = selectedHeadlineColor.takeOrElse { this.selectedHeadlineColor },
            selectedLeadingIconColor =
                selectedLeadingIconColor.takeOrElse { this.selectedLeadingIconColor },
            selectedTrailingIconColor =
                selectedTrailingIconColor.takeOrElse { this.selectedTrailingIconColor },
            selectedOverlineColor = selectedOverlineColor.takeOrElse { this.selectedOverlineColor },
            selectedSupportingTextColor =
                selectedSupportingTextColor.takeOrElse { this.selectedSupportingTextColor },
            disabledContainerColor =
                disabledContainerColor.takeOrElse { this.disabledContainerColor },
            disabledHeadlineColor = disabledHeadlineColor.takeOrElse { this.disabledHeadlineColor },
            disabledLeadingIconColor =
                disabledLeadingIconColor.takeOrElse { this.disabledLeadingIconColor },
            disabledTrailingIconColor =
                disabledTrailingIconColor.takeOrElse { this.disabledTrailingIconColor },
            disabledOverlineColor = disabledOverlineColor.takeOrElse { this.disabledOverlineColor },
            disabledSupportingTextColor =
                disabledSupportingTextColor.takeOrElse { this.disabledSupportingTextColor },
            draggedContainerColor = draggedContainerColor.takeOrElse { this.draggedContainerColor },
            draggedHeadlineColor = draggedHeadlineColor.takeOrElse { this.draggedHeadlineColor },
            draggedLeadingIconColor =
                draggedLeadingIconColor.takeOrElse { this.draggedLeadingIconColor },
            draggedTrailingIconColor =
                draggedTrailingIconColor.takeOrElse { this.draggedTrailingIconColor },
            draggedOverlineColor = draggedOverlineColor.takeOrElse { this.draggedOverlineColor },
            draggedSupportingTextColor =
                draggedSupportingTextColor.takeOrElse { this.draggedSupportingTextColor },
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is InteractiveListItemColors) return false

        if (containerColor != other.containerColor) return false
        if (headlineColor != other.headlineColor) return false
        if (leadingIconColor != other.leadingIconColor) return false
        if (trailingIconColor != other.trailingIconColor) return false
        if (overlineColor != other.overlineColor) return false
        if (supportingTextColor != other.supportingTextColor) return false
        if (selectedContainerColor != other.selectedContainerColor) return false
        if (selectedHeadlineColor != other.selectedHeadlineColor) return false
        if (selectedLeadingIconColor != other.selectedLeadingIconColor) return false
        if (selectedTrailingIconColor != other.selectedTrailingIconColor) return false
        if (selectedOverlineColor != other.selectedOverlineColor) return false
        if (selectedSupportingTextColor != other.selectedSupportingTextColor) return false
        if (disabledContainerColor != other.disabledContainerColor) return false
        if (disabledHeadlineColor != other.disabledHeadlineColor) return false
        if (disabledLeadingIconColor != other.disabledLeadingIconColor) return false
        if (disabledTrailingIconColor != other.disabledTrailingIconColor) return false
        if (disabledOverlineColor != other.disabledOverlineColor) return false
        if (disabledSupportingTextColor != other.disabledSupportingTextColor) return false
        if (draggedContainerColor != other.draggedContainerColor) return false
        if (draggedHeadlineColor != other.draggedHeadlineColor) return false
        if (draggedLeadingIconColor != other.draggedLeadingIconColor) return false
        if (draggedTrailingIconColor != other.draggedTrailingIconColor) return false
        if (draggedOverlineColor != other.draggedOverlineColor) return false
        if (draggedSupportingTextColor != other.draggedSupportingTextColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = containerColor.hashCode()
        result = 31 * result + headlineColor.hashCode()
        result = 31 * result + leadingIconColor.hashCode()
        result = 31 * result + trailingIconColor.hashCode()
        result = 31 * result + overlineColor.hashCode()
        result = 31 * result + supportingTextColor.hashCode()
        result = 31 * result + selectedContainerColor.hashCode()
        result = 31 * result + selectedHeadlineColor.hashCode()
        result = 31 * result + selectedLeadingIconColor.hashCode()
        result = 31 * result + selectedTrailingIconColor.hashCode()
        result = 31 * result + selectedOverlineColor.hashCode()
        result = 31 * result + selectedSupportingTextColor.hashCode()
        result = 31 * result + disabledContainerColor.hashCode()
        result = 31 * result + disabledHeadlineColor.hashCode()
        result = 31 * result + disabledLeadingIconColor.hashCode()
        result = 31 * result + disabledTrailingIconColor.hashCode()
        result = 31 * result + disabledOverlineColor.hashCode()
        result = 31 * result + disabledSupportingTextColor.hashCode()
        result = 31 * result + draggedContainerColor.hashCode()
        result = 31 * result + draggedHeadlineColor.hashCode()
        result = 31 * result + draggedLeadingIconColor.hashCode()
        result = 31 * result + draggedTrailingIconColor.hashCode()
        result = 31 * result + draggedOverlineColor.hashCode()
        result = 31 * result + draggedSupportingTextColor.hashCode()
        return result
    }
}

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Immutable
internal class InteractiveListItemShapes(
    val shape: Shape,
    val selectedShape: Shape,
    val pressedShape: Shape,
    val focusedShape: Shape,
    val hoveredShape: Shape,
    val draggedShape: Shape,
) {
    fun copy(
        shape: Shape? = this.shape,
        selectedShape: Shape? = this.selectedShape,
        pressedShape: Shape? = this.pressedShape,
        focusedShape: Shape? = this.focusedShape,
        hoveredShape: Shape? = this.hoveredShape,
        draggedShape: Shape? = this.draggedShape,
    ): InteractiveListItemShapes =
        InteractiveListItemShapes(
            shape = shape.takeOrElse { this.shape },
            selectedShape = selectedShape.takeOrElse { this.selectedShape },
            pressedShape = pressedShape.takeOrElse { this.pressedShape },
            focusedShape = focusedShape.takeOrElse { this.focusedShape },
            hoveredShape = hoveredShape.takeOrElse { this.hoveredShape },
            draggedShape = draggedShape.takeOrElse { this.draggedShape },
        )

    internal fun Shape?.takeOrElse(block: () -> Shape): Shape = this ?: block()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is InteractiveListItemShapes) return false

        if (shape != other.shape) return false
        if (selectedShape != other.selectedShape) return false
        if (pressedShape != other.pressedShape) return false
        if (focusedShape != other.focusedShape) return false
        if (hoveredShape != other.hoveredShape) return false
        if (draggedShape != other.draggedShape) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shape.hashCode()
        result = 31 * result + selectedShape.hashCode()
        result = 31 * result + pressedShape.hashCode()
        result = 31 * result + focusedShape.hashCode()
        result = 31 * result + hoveredShape.hashCode()
        result = 31 * result + draggedShape.hashCode()
        return result
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val InteractiveListItemShapes.hasRoundedCornerShapes: Boolean
    get() =
        shape is RoundedCornerShape &&
            selectedShape is RoundedCornerShape &&
            pressedShape is RoundedCornerShape &&
            focusedShape is RoundedCornerShape &&
            hoveredShape is RoundedCornerShape &&
            draggedShape is RoundedCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InteractiveListItemShapes.shapeForInteraction(
    selected: Boolean,
    pressed: Boolean,
    focused: Boolean,
    hovered: Boolean,
    dragged: Boolean,
    animationSpec: FiniteAnimationSpec<Float>,
): Shape {
    val shape =
        when {
            pressed -> pressedShape
            dragged -> draggedShape
            selected -> selectedShape
            focused -> focusedShape
            hovered -> hoveredShape
            else -> shape
        }

    if (hasRoundedCornerShapes)
        return key(this) { rememberAnimatedShape(shape as RoundedCornerShape, animationSpec) }

    return shape
}

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Immutable
internal class InteractiveListItemElevation(val elevation: Dp, val draggedElevation: Dp) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is InteractiveListItemElevation) return false

        if (elevation != other.elevation) return false
        if (draggedElevation != other.draggedElevation) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elevation.hashCode()
        result = 31 * result + draggedElevation.hashCode()
        return result
    }
}

/** TODO: docs */
@ExperimentalMaterial3ExpressiveApi
@Immutable
internal object InteractiveListItemDefaults {
    /** TODO: docs */
    @Composable
    fun colors(): InteractiveListItemColors {
        return MaterialTheme.colorScheme.defaultInteractiveListItemColors
    }

    /** TODO: docs */
    @Composable
    fun colors(
        containerColor: Color = Color.Unspecified,
        headlineColor: Color = Color.Unspecified,
        leadingIconColor: Color = Color.Unspecified,
        trailingIconColor: Color = Color.Unspecified,
        overlineColor: Color = Color.Unspecified,
        supportingTextColor: Color = Color.Unspecified,
        selectedContainerColor: Color = Color.Unspecified,
        selectedHeadlineColor: Color = Color.Unspecified,
        selectedLeadingIconColor: Color = Color.Unspecified,
        selectedTrailingIconColor: Color = Color.Unspecified,
        selectedOverlineColor: Color = Color.Unspecified,
        selectedSupportingTextColor: Color = Color.Unspecified,
        disabledContainerColor: Color = Color.Unspecified,
        disabledHeadlineColor: Color = Color.Unspecified,
        disabledLeadingIconColor: Color = Color.Unspecified,
        disabledTrailingIconColor: Color = Color.Unspecified,
        disabledOverlineColor: Color = Color.Unspecified,
        disabledSupportingTextColor: Color = Color.Unspecified,
        draggedContainerColor: Color = Color.Unspecified,
        draggedHeadlineColor: Color = Color.Unspecified,
        draggedLeadingIconColor: Color = Color.Unspecified,
        draggedTrailingIconColor: Color = Color.Unspecified,
        draggedOverlineColor: Color = Color.Unspecified,
        draggedSupportingTextColor: Color = Color.Unspecified,
    ): InteractiveListItemColors {
        return MaterialTheme.colorScheme.defaultInteractiveListItemColors.copy(
            containerColor = containerColor,
            headlineColor = headlineColor,
            leadingIconColor = leadingIconColor,
            trailingIconColor = trailingIconColor,
            overlineColor = overlineColor,
            supportingTextColor = supportingTextColor,
            selectedContainerColor = selectedContainerColor,
            selectedHeadlineColor = selectedHeadlineColor,
            selectedLeadingIconColor = selectedLeadingIconColor,
            selectedTrailingIconColor = selectedTrailingIconColor,
            selectedOverlineColor = selectedOverlineColor,
            selectedSupportingTextColor = selectedSupportingTextColor,
            disabledContainerColor = disabledContainerColor,
            disabledHeadlineColor = disabledHeadlineColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
            disabledOverlineColor = disabledOverlineColor,
            disabledSupportingTextColor = disabledSupportingTextColor,
            draggedContainerColor = draggedContainerColor,
            draggedHeadlineColor = draggedHeadlineColor,
            draggedLeadingIconColor = draggedLeadingIconColor,
            draggedTrailingIconColor = draggedTrailingIconColor,
            draggedOverlineColor = draggedOverlineColor,
            draggedSupportingTextColor = draggedSupportingTextColor,
        )
    }

    // TODO: load tokens from component file
    internal val ColorScheme.defaultInteractiveListItemColors: InteractiveListItemColors
        get() {
            return defaultInteractiveListItemColorsCached
                ?: InteractiveListItemColors(
                        // default
                        containerColor = fromToken(ColorSchemeKeyTokens.SurfaceBright),
                        headlineColor = fromToken(ColorSchemeKeyTokens.OnSurface),
                        leadingIconColor = fromToken(ColorSchemeKeyTokens.OnSurfaceVariant),
                        trailingIconColor = fromToken(ColorSchemeKeyTokens.OnSurfaceVariant),
                        overlineColor = fromToken(ColorSchemeKeyTokens.OnSurfaceVariant),
                        supportingTextColor = fromToken(ColorSchemeKeyTokens.OnSurfaceVariant),
                        // selected
                        selectedContainerColor = fromToken(ColorSchemeKeyTokens.SecondaryContainer),
                        selectedHeadlineColor =
                            fromToken(ColorSchemeKeyTokens.OnSecondaryContainer),
                        selectedLeadingIconColor =
                            fromToken(ColorSchemeKeyTokens.OnSecondaryContainer),
                        selectedTrailingIconColor =
                            fromToken(ColorSchemeKeyTokens.OnSecondaryContainer),
                        selectedOverlineColor =
                            fromToken(ColorSchemeKeyTokens.OnSecondaryContainer),
                        selectedSupportingTextColor =
                            fromToken(ColorSchemeKeyTokens.OnSecondaryContainer),
                        // disabled
                        disabledContainerColor = fromToken(ColorSchemeKeyTokens.SurfaceBright),
                        disabledHeadlineColor =
                            fromToken(ColorSchemeKeyTokens.OnSurface)
                                .copy(alpha = InteractiveListDisabledOpacity),
                        disabledLeadingIconColor =
                            fromToken(ColorSchemeKeyTokens.OnSurface)
                                .copy(alpha = InteractiveListDisabledOpacity),
                        disabledTrailingIconColor =
                            fromToken(ColorSchemeKeyTokens.OnSurface)
                                .copy(alpha = InteractiveListDisabledOpacity),
                        disabledOverlineColor =
                            fromToken(ColorSchemeKeyTokens.OnSurface)
                                .copy(alpha = InteractiveListDisabledOpacity),
                        disabledSupportingTextColor =
                            fromToken(ColorSchemeKeyTokens.OnSurface)
                                .copy(alpha = InteractiveListDisabledOpacity),
                        // dragged
                        draggedContainerColor = fromToken(ColorSchemeKeyTokens.TertiaryContainer),
                        draggedHeadlineColor = fromToken(ColorSchemeKeyTokens.Tertiary),
                        draggedLeadingIconColor = fromToken(ColorSchemeKeyTokens.Tertiary),
                        draggedTrailingIconColor = fromToken(ColorSchemeKeyTokens.Tertiary),
                        draggedOverlineColor = fromToken(ColorSchemeKeyTokens.Tertiary),
                        draggedSupportingTextColor = fromToken(ColorSchemeKeyTokens.Tertiary),
                    )
                    .also { defaultInteractiveListItemColorsCached = it }
        }

    /** TODO: docs */
    // TODO: account for first/last item in list shape changing
    @Composable
    fun shapes(
        shape: Shape? = null,
        selectedShape: Shape? = null,
        pressedShape: Shape? = null,
        focusedShape: Shape? = null,
        hoveredShape: Shape? = null,
        draggedShape: Shape? = null,
    ): InteractiveListItemShapes =
        MaterialTheme.shapes.defaultInteractiveListItemShapes.copy(
            shape = shape,
            selectedShape = selectedShape,
            pressedShape = pressedShape,
            focusedShape = focusedShape,
            hoveredShape = hoveredShape,
            draggedShape = draggedShape,
        )

    // TODO: load tokens from component file
    internal val Shapes.defaultInteractiveListItemShapes: InteractiveListItemShapes
        get() {
            return defaultInteractiveListItemShapesCached
                ?: InteractiveListItemShapes(
                        shape = fromToken(ShapeKeyTokens.CornerExtraSmall),
                        selectedShape = fromToken(ShapeKeyTokens.CornerLarge),
                        pressedShape = fromToken(ShapeKeyTokens.CornerLarge),
                        focusedShape = fromToken(ShapeKeyTokens.CornerLarge),
                        hoveredShape = fromToken(ShapeKeyTokens.CornerLarge),
                        draggedShape = fromToken(ShapeKeyTokens.CornerLarge),
                    )
                    .also { defaultInteractiveListItemShapesCached = it }
        }

    /** TODO: docs */
    // TODO: load tokens from component file
    fun elevation(
        elevation: Dp = ElevationTokens.Level0,
        draggedElevation: Dp = ElevationTokens.Level4,
    ): InteractiveListItemElevation =
        InteractiveListItemElevation(elevation = elevation, draggedElevation = draggedElevation)
}

@Composable
private fun LeadingDecorator(
    color: Color,
    textStyle: TypographyKeyTokens,
    content: (@Composable () -> Unit)?,
) {
    if (content != null) {
        Box(Modifier.padding(end = InteractiveListInternalSpacing)) {
            // TODO: perhaps also turn off MICS enforcement
            ProvideContentColorTextStyle(
                contentColor = color,
                textStyle = textStyle.value,
                content = content,
            )
        }
    }
}

@Composable
private fun TrailingDecorator(
    color: Color,
    textStyle: TypographyKeyTokens,
    content: (@Composable () -> Unit)?,
) {
    if (content != null) {
        Box(Modifier.padding(start = InteractiveListInternalSpacing)) {
            // TODO: perhaps also turn off MICS enforcement
            ProvideContentColorTextStyle(
                contentColor = color,
                textStyle = textStyle.value,
                content = content,
            )
        }
    }
}

@Composable
private fun OverlineDecorator(
    color: Color,
    textStyle: TypographyKeyTokens,
    content: (@Composable () -> Unit)?,
) {
    if (content != null) {
        Box {
            ProvideContentColorTextStyle(
                contentColor = color,
                textStyle = textStyle.value,
                content = content,
            )
        }
    }
}

@Composable
private fun SupportingDecorator(
    color: Color,
    textStyle: TypographyKeyTokens,
    content: (@Composable () -> Unit)?,
) {
    if (content != null) {
        Box {
            ProvideContentColorTextStyle(
                contentColor = color,
                textStyle = textStyle.value,
                content = content,
            )
        }
    }
}

@Composable
private fun HeadlineDecorator(
    color: Color,
    textStyle: TypographyKeyTokens,
    content: @Composable () -> Unit,
) {
    Box {
        ProvideContentColorTextStyle(
            contentColor = color,
            textStyle = textStyle.value,
            content = content,
        )
    }
}

/**
 * Equivalent to [collectIsPressedAsState], [collectIsFocusedAsState], etc. but only uses one
 * [LaunchedEffect]. The [MutableState] parameters, if provided, will be set to the corresponding
 * state value.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InteractionSource.CollectInteractionsAsState(
    pressedState: MutableState<Boolean>? = null,
    focusedState: MutableState<Boolean>? = null,
    hoveredState: MutableState<Boolean>? = null,
    draggedState: MutableState<Boolean>? = null,
) {
    LaunchedEffect(this) {
        val pressInteractions = pressedState?.let { mutableListOf<PressInteraction.Press>() }
        val focusInteractions = focusedState?.let { mutableListOf<FocusInteraction.Focus>() }
        val hoverInteractions = hoveredState?.let { mutableListOf<HoverInteraction.Enter>() }
        val dragInteractions = draggedState?.let { mutableListOf<DragInteraction.Start>() }

        interactions.collect { interaction ->
            when (interaction) {
                // press
                is PressInteraction.Press -> pressInteractions?.add(interaction)
                is PressInteraction.Release -> pressInteractions?.remove(interaction.press)
                is PressInteraction.Cancel -> pressInteractions?.remove(interaction.press)
                // focus
                is FocusInteraction.Focus -> focusInteractions?.add(interaction)
                is FocusInteraction.Unfocus -> focusInteractions?.remove(interaction.focus)
                // hover
                is HoverInteraction.Enter -> hoverInteractions?.add(interaction)
                is HoverInteraction.Exit -> hoverInteractions?.remove(interaction.enter)
                // drag
                is DragInteraction.Start -> dragInteractions?.add(interaction)
                is DragInteraction.Stop -> dragInteractions?.remove(interaction.start)
                is DragInteraction.Cancel -> dragInteractions?.remove(interaction.start)
            }
            if (pressedState != null && pressInteractions != null) {
                pressedState.value = pressInteractions.isNotEmpty()
            }
            if (focusedState != null && focusInteractions != null) {
                focusedState.value = focusInteractions.isNotEmpty()
            }
            if (hoveredState != null && hoverInteractions != null) {
                hoveredState.value = hoverInteractions.isNotEmpty()
            }
            if (draggedState != null && dragInteractions != null) {
                draggedState.value = dragInteractions.isNotEmpty()
            }
        }
    }
}

private data class InteractiveListColorState(
    val enabled: Boolean,
    val selected: Boolean,
    val dragged: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InteractiveListItem(
    modifier: Modifier,
    headlineContent: @Composable () -> Unit,
    leadingContent: @Composable (() -> Unit)?,
    trailingContent: @Composable (() -> Unit)?,
    overlineContent: @Composable (() -> Unit)?,
    supportingContent: @Composable (() -> Unit)?,
    enabled: Boolean,
    selected: Boolean,
    applySemantics: SemanticsPropertyReceiver.() -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onLongClickLabel: String?,
    interactionSource: MutableInteractionSource?,
    colors: InteractiveListItemColors,
    shapes: InteractiveListItemShapes,
    elevation: InteractiveListItemElevation,
    contentPadding: PaddingValues,
) {
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    val pressed = remember { mutableStateOf(false) }
    val focused = remember { mutableStateOf(false) }
    val hovered = remember { mutableStateOf(false) }
    val dragged = remember { mutableStateOf(false) }

    interactionSource.CollectInteractionsAsState(
        pressedState = pressed,
        focusedState = focused,
        hoveredState = hovered,
        draggedState = dragged,
    )

    // TODO: Load the motionScheme tokens from the component tokens file
    val colorAnimationSpec = MotionSchemeKeyTokens.DefaultEffects.value<Color>()
    val shapeAnimationSpec = MotionSchemeKeyTokens.FastSpatial.value<Float>()
    val elevationAnimationSpec = MotionSchemeKeyTokens.FastSpatial.value<Dp>()

    val shape =
        shapes.shapeForInteraction(
            selected = selected,
            pressed = pressed.value,
            focused = focused.value,
            hovered = hovered.value,
            dragged = dragged.value,
            animationSpec = shapeAnimationSpec,
        )

    val colorState = InteractiveListColorState(enabled, selected, dragged.value)
    val transition = updateTransition(colorState, "ListColor")

    val containerColor by
        transition.animateColor(transitionSpec = { colorAnimationSpec }) { state ->
            colors.containerColor(
                enabled = state.enabled,
                selected = state.selected,
                dragged = state.dragged,
            )
        }
    val contentColor by
        transition.animateColor(transitionSpec = { colorAnimationSpec }) { state ->
            colors.headlineColor(
                enabled = state.enabled,
                selected = state.selected,
                dragged = state.dragged,
            )
        }
    val leadingColor by
        transition.animateColor(transitionSpec = { colorAnimationSpec }) { state ->
            colors.leadingIconColor(
                enabled = state.enabled,
                selected = state.selected,
                dragged = state.dragged,
            )
        }
    val trailingColor by
        transition.animateColor(transitionSpec = { colorAnimationSpec }) { state ->
            colors.trailingIconColor(
                enabled = state.enabled,
                selected = state.selected,
                dragged = state.dragged,
            )
        }
    val overlineColor by
        transition.animateColor(transitionSpec = { colorAnimationSpec }) { state ->
            colors.overlineColor(
                enabled = state.enabled,
                selected = state.selected,
                dragged = state.dragged,
            )
        }
    val supportingColor by
        transition.animateColor(transitionSpec = { colorAnimationSpec }) { state ->
            colors.supportingTextColor(
                enabled = state.enabled,
                selected = state.selected,
                dragged = state.dragged,
            )
        }

    // TODO: load tokens from component tokens file
    val leadingTextStyle = TypographyKeyTokens.TitleMedium
    val trailingTextStyle = TypographyKeyTokens.LabelSmall
    val overlineTextStyle = TypographyKeyTokens.LabelMedium
    val supportingTextStyle = TypographyKeyTokens.BodyMedium
    val contentTextStyle = TypographyKeyTokens.BodyLarge

    val targetElevation = if (dragged.value) elevation.draggedElevation else elevation.elevation
    val shadowElevation = animateDpAsState(targetElevation, elevationAnimationSpec)

    Surface(
        modifier =
            modifier
                .semantics(mergeDescendants = true, properties = applySemantics)
                .minimumInteractiveComponentSize()
                // FIXME: we need to clip the ripple without clipping away the shadow applied by
                //   Surface
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    enabled = enabled,
                    onLongClick = onLongClick,
                    onLongClickLabel = onLongClickLabel,
                    onClick = onClick,
                ),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation.value,
    ) {
        val alignmentBreakpoint =
            (InteractiveListVerticalAlignmentBreakpoint -
                    contentPadding.calculateTopPadding() -
                    contentPadding.calculateBottomPadding())
                .coerceAtLeast(0.dp)
        InteractiveListItemLayout(
            modifier = Modifier.padding(contentPadding),
            alignmentBreakpoint = alignmentBreakpoint,
            leading = {
                LeadingDecorator(
                    color = leadingColor,
                    textStyle = leadingTextStyle,
                    content = leadingContent,
                )
            },
            trailing = {
                TrailingDecorator(
                    color = trailingColor,
                    textStyle = trailingTextStyle,
                    content = trailingContent,
                )
            },
            overline = {
                OverlineDecorator(
                    color = overlineColor,
                    textStyle = overlineTextStyle,
                    content = overlineContent,
                )
            },
            supporting = {
                SupportingDecorator(
                    color = supportingColor,
                    textStyle = supportingTextStyle,
                    content = supportingContent,
                )
            },
            headline = {
                HeadlineDecorator(
                    color = contentColor,
                    textStyle = contentTextStyle,
                    content = headlineContent,
                )
            },
        )
    }
}

@Composable
private fun InteractiveListItemLayout(
    modifier: Modifier,
    alignmentBreakpoint: Dp,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    overline: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    headline: @Composable () -> Unit,
) {
    val measurePolicy =
        remember(alignmentBreakpoint) { InteractiveListItemMeasurePolicy(alignmentBreakpoint) }
    Layout(
        modifier = modifier,
        contents = listOf(leading, trailing, overline, supporting, headline),
        measurePolicy = measurePolicy,
    )
}

private class InteractiveListItemMeasurePolicy(val alignmentBreakpoint: Dp) :
    MultiContentMeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<List<Measurable>>,
        constraints: Constraints,
    ): MeasureResult {
        val (
            leadingMeasurable,
            trailingMeasurable,
            overlineMeasurable,
            supportingMeasurable,
            headlineMeasurable,
        ) = measurables

        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        var constraintOffsetX = 0
        var constraintOffsetY = 0

        val leadingPlaceable = leadingMeasurable.firstOrNull()?.measure(looseConstraints)
        constraintOffsetX += leadingPlaceable.widthOrZero

        val trailingPlaceable =
            trailingMeasurable
                .firstOrNull()
                ?.measure(looseConstraints.offset(horizontal = -constraintOffsetX))
        constraintOffsetX += trailingPlaceable.widthOrZero

        val overlinePlaceable =
            overlineMeasurable
                .firstOrNull()
                ?.measure(looseConstraints.offset(horizontal = -constraintOffsetX))
        constraintOffsetY += overlinePlaceable.heightOrZero

        val headlinePlaceable =
            headlineMeasurable
                .firstOrNull()
                ?.measure(
                    looseConstraints.offset(
                        horizontal = -constraintOffsetX,
                        vertical = -constraintOffsetY,
                    )
                )
        constraintOffsetY += headlinePlaceable.heightOrZero

        val supportingPlaceable =
            supportingMeasurable
                .firstOrNull()
                ?.measure(
                    looseConstraints.offset(
                        horizontal = -constraintOffsetX,
                        vertical = -constraintOffsetY,
                    )
                )

        val width =
            calculateWidth(
                leadingWidth = leadingPlaceable.widthOrZero,
                trailingWidth = trailingPlaceable.widthOrZero,
                overlineWidth = overlinePlaceable.widthOrZero,
                supportingWidth = supportingPlaceable.widthOrZero,
                headlineWidth = headlinePlaceable.widthOrZero,
                constraints = constraints,
            )
        val height =
            calculateHeight(
                leadingHeight = leadingPlaceable.heightOrZero,
                trailingHeight = trailingPlaceable.heightOrZero,
                overlineHeight = overlinePlaceable.heightOrZero,
                supportingHeight = supportingPlaceable.heightOrZero,
                headlineHeight = headlinePlaceable.heightOrZero,
                constraints = constraints,
            )

        return place(
            width = width,
            height = height,
            leadingPlaceable = leadingPlaceable,
            trailingPlaceable = trailingPlaceable,
            headlinePlaceable = headlinePlaceable,
            overlinePlaceable = overlinePlaceable,
            supportingPlaceable = supportingPlaceable,
        )
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = calculateIntrinsicHeight(measurables, width, IntrinsicMeasurable::maxIntrinsicHeight)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = calculateIntrinsicWidth(measurables, height, IntrinsicMeasurable::maxIntrinsicWidth)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
    ): Int = calculateIntrinsicHeight(measurables, width, IntrinsicMeasurable::minIntrinsicHeight)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
    ): Int = calculateIntrinsicWidth(measurables, height, IntrinsicMeasurable::minIntrinsicWidth)

    private fun calculateIntrinsicWidth(
        measurables: List<List<IntrinsicMeasurable>>,
        height: Int,
        intrinsicMeasure: IntrinsicMeasurable.(height: Int) -> Int,
    ): Int {
        val (
            leadingMeasurable,
            trailingMeasurable,
            overlineMeasurable,
            supportingMeasurable,
            headlineMeasurable,
        ) = measurables

        return calculateWidth(
            leadingWidth = leadingMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            trailingWidth = trailingMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            overlineWidth = overlineMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            supportingWidth = supportingMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            headlineWidth = headlineMeasurable.firstOrNull()?.intrinsicMeasure(height) ?: 0,
            constraints = Constraints(),
        )
    }

    private fun calculateIntrinsicHeight(
        measurables: List<List<IntrinsicMeasurable>>,
        width: Int,
        intrinsicMeasure: IntrinsicMeasurable.(width: Int) -> Int,
    ): Int {
        val (
            leadingMeasurable,
            trailingMeasurable,
            overlineMeasurable,
            supportingMeasurable,
            headlineMeasurable,
        ) = measurables

        var remainingWidth = width

        val leadingHeight =
            leadingMeasurable.firstOrNull()?.let {
                val height = it.intrinsicMeasure(remainingWidth)
                remainingWidth =
                    remainingWidth.subtractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity)
                    )
                height
            } ?: 0
        val trailingHeight =
            trailingMeasurable.firstOrNull()?.let {
                val height = it.intrinsicMeasure(remainingWidth)
                remainingWidth =
                    remainingWidth.subtractConstraintSafely(
                        it.maxIntrinsicWidth(Constraints.Infinity)
                    )
                height
            } ?: 0
        val overlineHeight = overlineMeasurable.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val supportingHeight =
            supportingMeasurable.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0
        val headlineHeight = headlineMeasurable.firstOrNull()?.intrinsicMeasure(remainingWidth) ?: 0

        return calculateHeight(
            leadingHeight = leadingHeight,
            trailingHeight = trailingHeight,
            overlineHeight = overlineHeight,
            supportingHeight = supportingHeight,
            headlineHeight = headlineHeight,
            constraints = Constraints(),
        )
    }

    private fun MeasureScope.place(
        width: Int,
        height: Int,
        leadingPlaceable: Placeable?,
        trailingPlaceable: Placeable?,
        headlinePlaceable: Placeable?,
        overlinePlaceable: Placeable?,
        supportingPlaceable: Placeable?,
    ): MeasureResult {
        return layout(width, height) {
            val verticalAlignment =
                if (height > alignmentBreakpoint.roundToPx()) {
                    Alignment.Top
                } else {
                    Alignment.CenterVertically
                }

            leadingPlaceable?.placeRelative(
                x = 0,
                y = verticalAlignment.align(leadingPlaceable.height, height),
            )

            val mainContentX = leadingPlaceable.widthOrZero
            val mainContentTotalHeight =
                headlinePlaceable.heightOrZero +
                    overlinePlaceable.heightOrZero +
                    supportingPlaceable.heightOrZero
            val mainContentY = verticalAlignment.align(mainContentTotalHeight, height)
            var currY = mainContentY

            overlinePlaceable?.placeRelative(mainContentX, currY)
            currY += overlinePlaceable.heightOrZero

            headlinePlaceable?.placeRelative(mainContentX, currY)
            currY += headlinePlaceable.heightOrZero

            supportingPlaceable?.placeRelative(mainContentX, currY)

            trailingPlaceable?.placeRelative(
                x = width - trailingPlaceable.width,
                y = verticalAlignment.align(trailingPlaceable.height, height),
            )
        }
    }

    private fun calculateWidth(
        leadingWidth: Int,
        trailingWidth: Int,
        overlineWidth: Int,
        supportingWidth: Int,
        headlineWidth: Int,
        constraints: Constraints,
    ): Int {
        if (constraints.hasBoundedWidth) {
            return constraints.maxWidth
        }
        // Fallback behavior if width constraints are infinite
        val mainContentWidth = maxOf(headlineWidth, overlineWidth, supportingWidth)
        return leadingWidth + mainContentWidth + trailingWidth
    }

    private fun calculateHeight(
        leadingHeight: Int,
        trailingHeight: Int,
        overlineHeight: Int,
        supportingHeight: Int,
        headlineHeight: Int,
        constraints: Constraints,
    ): Int {
        val mainContentHeight = headlineHeight + overlineHeight + supportingHeight

        return constraints.constrainHeight(maxOf(leadingHeight, mainContentHeight, trailingHeight))
    }
}

// TODO: replace with tokens
internal val InteractiveListStartPadding = 16.dp
internal val InteractiveListEndPadding = 16.dp
internal val InteractiveListTopPadding = 12.dp
internal val InteractiveListBottomPadding = 12.dp
internal val InteractiveListInternalSpacing = 12.dp
internal val InteractiveListDisabledOpacity = 0.38f
/**
 * How tall a list item needs to be before internal content is top-aligned instead of
 * center-aligned.
 */
internal val InteractiveListVerticalAlignmentBreakpoint = 80.dp

// TODO: move to defaults
private val InteractiveListPadding =
    PaddingValues(
        start = InteractiveListStartPadding,
        end = InteractiveListEndPadding,
        top = InteractiveListTopPadding,
        bottom = InteractiveListBottomPadding,
    )
