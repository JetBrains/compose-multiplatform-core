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

package androidx.xr.glimmer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A surface is a fundamental building block in Glimmer. A surface represents a distinct visual area
 * or 'physical' boundary for components such as buttons and cards. A surface is responsible for:
 * 1) Clipping: a surface clips its children to the shape specified by [shape]
 * 2) Border: a surface draws an inner [border] to emphasize the boundary of the component. When
 *    focused, a surface draws a wider border with a focused highlight on top to indicate the focus
 *    state.
 * 3) Background: a surface has a background color of [color].
 * 4) Content color: a surface provides a [contentColor] for text and icons inside the surface. By
 *    default this is calculated from the provided background color.
 *
 * This surface is focusable by default - set [focusable] to false for un-interactive / decorative
 * surfaces.
 *
 * @sample androidx.xr.glimmer.samples.SurfaceSample
 * @param focusable whether this surface is focusable, true by default. Most surfaces should be
 *   focusable to allow navigation between surfaces in a screen. Unfocusable surfaces may be used
 *   for decorative only elements, such as surfaces used in a compound component with a separate
 *   focusable area.
 * @param shape the [Shape] used to clip this surface, and also used to draw the background and
 *   border
 * @param color the background [Color] for this surface
 * @param contentColor the [Color] for content inside this surface
 * @param border an optional inner border for this surface
 * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
 *   emitting [Interaction]s for this surface. Note that if `null` is provided, interactions will
 *   still happen internally.
 */
@Composable
public fun Modifier.surface(
    focusable: Boolean = true,
    shape: Shape = SurfaceDefaults.Shape,
    color: Color = GlimmerTheme.colors.surface,
    contentColor: Color = calculateContentColor(color),
    border: BorderStroke? = SurfaceDefaults.border(),
    interactionSource: MutableInteractionSource? = null,
): Modifier =
    this.clip(shape)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .background(color = color, shape = shape)
        .then(SurfaceNodeElement(contentColor))
        .focusable(enabled = focusable, interactionSource = interactionSource)

/** Default values used for [surface]. */
public object SurfaceDefaults {
    /** The default [Shape] used for a [surface] */
    public val Shape: Shape = RoundedCornerShape(40.dp)

    /**
     * Create the default [BorderStroke] used for a [surface]. Use the other overload in order to
     * change the width or color.
     */
    @Composable
    public fun border(): BorderStroke {
        return GlimmerTheme.LocalGlimmerTheme.current.defaultSurfaceBorder
    }

    /**
     * Create the default [BorderStroke] used for a [surface], with optional overrides for [width]
     * and [color].
     *
     * @param width width of the border in [Dp]. Use [Dp.Hairline] for one-pixel border.
     * @param color color to paint the border with
     */
    @Composable
    public fun border(
        width: Dp = DefaultSurfaceBorderWidth,
        color: Color = GlimmerTheme.colors.outline,
    ): BorderStroke {
        return BorderStroke(width, color)
    }

    /** Returns the default (cached) border for a [surface]. */
    internal val GlimmerTheme.defaultSurfaceBorder: BorderStroke
        get() {
            return defaultSurfaceBorderCached
                ?: BorderStroke(DefaultSurfaceBorderWidth, colors.outline).also {
                    defaultSurfaceBorderCached = it
                }
        }
}

/**
 * Retrieves the preferred content color for text and iconography within a [surface]. Most surfaces
 * should be [Color.Black], so content color is typically [Color.White]. In a few cases where
 * surfaces are filled with a different color, the content color may be [Color.Black] to improve
 * contrast. For cases where higher emphasis is required, content color may be a different color
 * from the theme, such as [Colors.primary].
 *
 * Content color is automatically provided by [surface], and calculated from the provided background
 * color by default. To manually calculate the default content color for a provided background
 * color, use [calculateContentColor].
 */
internal fun DelegatableNode.currentContentColor(): Color {
    var contentColor = Color.White
    traverseAncestors(SurfaceNodeTraverseKey) {
        if (it is SurfaceNode) {
            contentColor = it.contentColor
            // Stop at the nearest descendant surface
            false
        } else {
            // Theoretically someone else could define the same traverse key, so continue just to be
            // safe
            true
        }
    }
    return contentColor
}

private class SurfaceNodeElement(private val contentColor: Color) :
    ModifierNodeElement<SurfaceNode>() {
    override fun create(): SurfaceNode = SurfaceNode(contentColor)

    override fun update(node: SurfaceNode) = node.update(contentColor)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SurfaceNodeElement) return false

        if (contentColor != other.contentColor) return false

        return true
    }

    override fun hashCode(): Int {
        return contentColor.hashCode()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "surface"
        properties["contentColor"] = contentColor
    }
}

private class SurfaceNode(contentColor: Color) :
    SemanticsModifierNode, TraversableNode, Modifier.Node() {
    var contentColor by mutableStateOf(contentColor)
        private set

    override val shouldAutoInvalidate = false

    fun update(contentColor: Color) {
        this.contentColor = contentColor
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        isTraversalGroup = true
    }

    override val traverseKey: String = SurfaceNodeTraverseKey
}

/** Default border width for a [surface]. */
private val DefaultSurfaceBorderWidth = 2.dp

private const val SurfaceNodeTraverseKey = "androidx.xr.glimmer.SurfaceNode"
