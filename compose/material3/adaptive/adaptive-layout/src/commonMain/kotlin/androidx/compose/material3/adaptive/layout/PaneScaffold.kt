/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.material3.adaptive.layout

import androidx.annotation.FloatRange
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMaxOfOrNull

/**
 * Extended scope for the panes of pane scaffolds. All pane scaffolds will implement this interface
 * to provide necessary info for panes to correctly render their content, motion, etc.
 *
 * @param Role The type of roles that denotes panes in the associated pane scaffold.
 * @param ScaffoldValue The type of scaffold values that denotes the [PaneAdaptedValue]s in the
 *   associated pane scaffold.
 * @see ThreePaneScaffoldPaneScope
 * @see PaneScaffoldScope
 * @see PaneScaffoldTransitionScope
 * @see PaneScaffoldPaneScope
 * @see LookaheadScope
 */
@ExperimentalMaterial3AdaptiveApi
sealed interface ExtendedPaneScaffoldPaneScope<Role, ScaffoldValue : PaneScaffoldValue<Role>> :
    ExtendedPaneScaffoldScope<Role, ScaffoldValue>, PaneScaffoldPaneScope<Role>

/**
 * Extended scope for pane scaffolds. All pane scaffolds will implement this interface to provide
 * necessary info for its sub-composables to correctly render their content, motion, etc.
 *
 * @param Role The type of roles that denotes panes in the associated pane scaffold.
 * @param ScaffoldValue The type of scaffold values that denotes the [PaneAdaptedValue]s in the
 *   associated pane scaffold.
 * @see ThreePaneScaffoldScope
 * @see PaneScaffoldScope
 * @see PaneScaffoldTransitionScope
 * @see LookaheadScope
 */
@ExperimentalMaterial3AdaptiveApi
sealed interface ExtendedPaneScaffoldScope<Role, ScaffoldValue : PaneScaffoldValue<Role>> :
    PaneScaffoldScope, PaneScaffoldTransitionScope<Role, ScaffoldValue>, LookaheadScope

/**
 * The base scope of pane scaffolds, which provides scoped functions that supported by pane
 * scaffolds.
 */
sealed interface PaneScaffoldScope {
    /**
     * This modifier specifies the preferred width for a pane, and the pane scaffold implementation
     * will try its best to respect this width when the associated pane is rendered as a fixed pane,
     * i.e., a pane that are not stretching to fill the remaining spaces. In case the modifier is
     * not set or set to [Dp.Unspecified], the default preferred widths provided by
     * [PaneScaffoldDirective] are supposed to be used.
     *
     * @see PaneScaffoldDirective.defaultPanePreferredWidth
     */
    fun Modifier.preferredWidth(width: Dp): Modifier

    /**
     * The modifier that should be applied on a drag handle composable so the drag handle can be
     * dragged and operate on the provided [PaneExpansionState] properly. This modifier also sets up
     * other behaviors a pane expansion drag handle is supposed to perform, like excluding system
     * gestures and ensuring minimum touch target size.
     *
     * See usage samples at:
     *
     * @sample androidx.compose.material3.adaptive.samples.PaneExpansionDragHandleSample
     */
    @ExperimentalMaterial3AdaptiveApi
    fun Modifier.paneExpansionDraggable(
        state: PaneExpansionState,
        minTouchTargetSize: Dp,
        interactionSource: MutableInteractionSource
    ): Modifier
}

/**
 * The transition scope of pane scaffold implementations, which provides the current transition info
 * of the associated pane scaffold.
 */
@ExperimentalMaterial3AdaptiveApi
sealed interface PaneScaffoldTransitionScope<Role, ScaffoldValue : PaneScaffoldValue<Role>> {
    /** The current scaffold state transition between [PaneScaffoldValue]s. */
    val scaffoldStateTransition: Transition<ScaffoldValue>

    /** The current motion progress. */
    @get:FloatRange(from = 0.0, to = 1.0) val motionProgress: Float

    /**
     * Provides measurement and other data required in motion calculation like the size and offset
     * of each pane before and after the motion.
     *
     * Note that the data provided are supposed to be only read proactively by the motion logic
     * "on-the-fly" when the scaffold motion is happening. Using them elsewhere may cause unexpected
     * behavior.
     */
    val motionDataProvider: PaneScaffoldMotionDataProvider<Role>

    /**
     * A convenient function to get the given [PaneMotion]'s [EnterTransition] under the context of
     * the current [PaneScaffoldTransitionScope].
     *
     * @see [PaneMotion.enterTransition]
     */
    val PaneMotion.enterTransition
        get() = with(this) { motionDataProvider.enterTransition }

    /**
     * A convenient function to get the given [PaneMotion]'s [EnterTransition] under the context of
     * the current [PaneScaffoldTransitionScope].
     *
     * @see [PaneMotion.exitTransition]
     */
    val PaneMotion.exitTransition
        get() = with(this) { motionDataProvider.exitTransition }
}

/**
 * The pane scope of the current pane under the scope, which provides the pane relevant info like
 * its role and [PaneMotion].
 */
@ExperimentalMaterial3AdaptiveApi
sealed interface PaneScaffoldPaneScope<Role> {
    /** The role of the current pane in the scope. */
    val paneRole: Role

    /** The specified pane motion of the current pane in the scope. */
    val paneMotion: PaneMotion
}

internal abstract class PaneScaffoldScopeImpl : PaneScaffoldScope {
    override fun Modifier.preferredWidth(width: Dp): Modifier {
        require(width == Dp.Unspecified || width > 0.dp) { "invalid width" }
        return this.then(PreferredWidthElement(width))
    }
}

private class PreferredWidthElement(
    private val width: Dp,
) : ModifierNodeElement<PreferredWidthNode>() {
    private val inspectorInfo = debugInspectorInfo {
        name = "preferredWidth"
        value = width
    }

    override fun create(): PreferredWidthNode {
        return PreferredWidthNode(width)
    }

    override fun update(node: PreferredWidthNode) {
        node.width = width
    }

    override fun InspectorInfo.inspectableProperties() {
        inspectorInfo()
    }

    override fun hashCode(): Int {
        return width.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        val otherModifier = other as? PreferredWidthElement ?: return false
        return width == otherModifier.width
    }
}

private class PreferredWidthNode(var width: Dp) : ParentDataModifierNode, Modifier.Node() {
    override fun Density.modifyParentData(parentData: Any?) =
        ((parentData as? PaneScaffoldParentDataImpl) ?: PaneScaffoldParentDataImpl()).also {
            it.preferredWidth = with(this) { width.toPx() }
        }
}

internal fun Modifier.animatedPane(): Modifier {
    return this.then(AnimatedPaneElement)
}

private object AnimatedPaneElement : ModifierNodeElement<AnimatedPaneNode>() {
    private val inspectorInfo = debugInspectorInfo {
        name = "isPaneComposable"
        value = true
    }

    override fun create(): AnimatedPaneNode {
        return AnimatedPaneNode()
    }

    override fun update(node: AnimatedPaneNode) {}

    override fun InspectorInfo.inspectableProperties() {
        inspectorInfo()
    }

    override fun hashCode(): Int {
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return (other is AnimatedPaneElement)
    }
}

private class AnimatedPaneNode : ParentDataModifierNode, Modifier.Node() {
    override fun Density.modifyParentData(parentData: Any?) =
        ((parentData as? PaneScaffoldParentDataImpl) ?: PaneScaffoldParentDataImpl()).also {
            it.isAnimatedPane = true
        }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal val List<Measurable>.minTouchTargetSize: Dp
    get() =
        fastMaxOfOrNull {
            val size =
                (it.parentData as? PaneScaffoldParentData)?.minTouchTargetSize ?: Dp.Unspecified
            if (size == Dp.Unspecified) {
                0.dp
            } else {
                size
            }
        } ?: 0.dp

/**
 * The parent data passed to pane scaffolds by their contents like panes and drag handles.
 *
 * @see PaneScaffoldScope.preferredWidth
 */
@ExperimentalMaterial3AdaptiveApi
sealed interface PaneScaffoldParentData {
    /**
     * The preferred width of the child, which is supposed to be set via
     * [PaneScaffoldScope.preferredWidth] on a pane composable, like [AnimatedPane].
     */
    val preferredWidth: Float

    /** `true` to indicate that the child is an [AnimatedPane]; otherwise `false`. */
    val isAnimatedPane: Boolean

    /**
     * The minimum touch target size of the child, which is supposed to be set via
     * [PaneScaffoldScope.paneExpansionDraggable] on a drag handle component.
     */
    val minTouchTargetSize: Dp
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal data class PaneScaffoldParentDataImpl(
    override var preferredWidth: Float = Float.NaN,
    var paneMargins: PaneMargins = PaneMargins.Unspecified,
    override var isAnimatedPane: Boolean = false,
    override var minTouchTargetSize: Dp = Dp.Unspecified
) : PaneScaffoldParentData
