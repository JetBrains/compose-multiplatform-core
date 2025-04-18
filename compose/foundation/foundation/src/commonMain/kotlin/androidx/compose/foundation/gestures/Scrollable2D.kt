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

package androidx.compose.foundation.gestures

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Configure touch scrolling and flinging for the UI element in both XY orientations.
 *
 * Users should update their state themselves using default [Scrollable2DState] and its
 * `consumeScrollDelta` callback or by implementing [Scrollable2DState] interface manually and
 * reflect their own state in UI when using this component.
 *
 * If you don't need to have fling or nested scroll support, but want to make component simply
 * draggable, consider using [draggable2D]. If you're only interested in a single direction scroll,
 * consider using [scrollable].
 *
 * This overload provides the access to [OverscrollEffect] that defines the behaviour of the over
 * scrolling logic. Use [androidx.compose.foundation.rememberOverscrollEffect] to create an instance
 * of the current provided overscroll implementation.
 *
 * @param state [Scrollable2DState] state of the scrollable. Defines how scroll events will be
 *   interpreted by the user land logic and contains useful information about on-going events.
 * @param enabled whether or not scrolling is enabled
 * @param overscrollEffect effect to which the deltas will be fed when the scrollable have some
 *   scrolling delta left. Pass `null` for no overscroll. If you pass an effect you should also
 *   apply [androidx.compose.foundation.overscroll] modifier.
 * @param flingBehavior logic describing fling behavior when drag has finished with velocity. If
 *   `null`, default from [ScrollableDefaults.flingBehavior] will be used.
 * @param interactionSource [MutableInteractionSource] that will be used to emit drag events when
 *   this scrollable is being dragged.
 */
@Stable
private fun Modifier.scrollable2D(
    state: Scrollable2DState,
    enabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = null,
    flingBehavior: FlingBehavior? = null,
    interactionSource: MutableInteractionSource? = null
) =
    this then
        Scrollable2DElement(state, overscrollEffect, enabled, flingBehavior, interactionSource)

private class Scrollable2DElement(
    val state: Scrollable2DState,
    val overscrollEffect: OverscrollEffect?,
    val enabled: Boolean,
    val flingBehavior: FlingBehavior?,
    val interactionSource: MutableInteractionSource?
) : ModifierNodeElement<Scrollable2DNode>() {
    override fun create(): Scrollable2DNode {
        return Scrollable2DNode(state, overscrollEffect, flingBehavior, enabled, interactionSource)
    }

    override fun update(node: Scrollable2DNode) {
        node.update(state, overscrollEffect, enabled, flingBehavior, interactionSource)
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + overscrollEffect.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + flingBehavior.hashCode()
        result = 31 * result + interactionSource.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is Scrollable2DElement) return false

        if (state != other.state) return false
        if (overscrollEffect != other.overscrollEffect) return false
        if (enabled != other.enabled) return false
        if (flingBehavior != other.flingBehavior) return false
        if (interactionSource != other.interactionSource) return false

        return true
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "scrollable2D"
        properties["state"] = state
        properties["overscrollEffect"] = overscrollEffect
        properties["enabled"] = enabled
        properties["flingBehavior"] = flingBehavior
        properties["interactionSource"] = interactionSource
    }
}

internal class Scrollable2DNode(
    private var state: Scrollable2DState,
    private var overscrollEffect: OverscrollEffect?,
    private var flingBehavior: FlingBehavior?,
    private var enabled: Boolean,
    private var interactionSource: MutableInteractionSource?
) : Modifier.Node() {

    fun update(
        state: Scrollable2DState,
        overscrollEffect: OverscrollEffect?,
        enabled: Boolean,
        flingBehavior: FlingBehavior?,
        interactionSource: MutableInteractionSource?,
    ) {
        this.state = state
        this.overscrollEffect = overscrollEffect
        this.enabled = enabled
        this.flingBehavior = flingBehavior
        this.interactionSource = interactionSource
    }
}
