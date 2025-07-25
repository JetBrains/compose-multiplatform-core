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

@file:OptIn(ExperimentalSharedTransitionApi::class)

package androidx.compose.animation

import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull

internal class SharedElement(val key: Any, val scope: SharedTransitionScopeImpl) {
    fun isAnimating(): Boolean = states.fastAny { it.boundsAnimation.isRunning } && foundMatch

    fun updateMatch() {
        val hasVisibleContent = hasVisibleContent()
        if (states.size > 1 && hasVisibleContent) {
            foundMatch = true
        } else if (scope.isTransitionActive) {
            // Unrecoverable state when the shared element/bound that is becoming visible
            // is removed.
            if (!hasVisibleContent) {
                foundMatch = false
            }
        } else {
            // Transition not active
            foundMatch = false
        }
        if (states.isNotEmpty()) {
            scope.observeReads(this, updateMatch, observingVisibilityChange)
        }
        invalidateTargetBoundsProvider()
    }

    var foundMatch: Boolean by mutableStateOf(false)
        private set

    /** *********** Properties below should only be accessed during placement. ************* */
    internal var targetBounds: Rect?
        get() = if (foundMatch) _targetBoundsWhenMatched else null
        set(value) {
            if (foundMatch) {
                _targetBoundsWhenMatched = value
            }
        }

    private var _targetBoundsWhenMatched: Rect? by mutableStateOf(null)

    // This gets called in approach measurement.
    fun tryInitializingCurrentBounds(): Rect? {
        if (!foundMatch) return null

        updateTargetBoundsProvider()
        if (currentBoundsWhenMatched == null) {
            currentBoundsWhenMatched = obtainBoundsFromLastTarget()
        }
        return currentBoundsWhenMatched
    }

    // This tracks the current visible bounds of shared elements on screen. It is reset when
    // foundMatch == false, NOT when the transition finishes.
    var currentBoundsWhenMatched: Rect? by mutableStateOf(null)

    internal var lastTargetBoundsProvider: BoundsProvider? = null
        private set

    internal var targetBoundsProvider: BoundsProvider? = null
        private set(value) {
            if (field != value) {
                lastTargetBoundsProvider = field
            }
            field = value
        }

    // Use a request id rather than a Boolean to track whether the targetBoundsProvider
    // update request has been handled. The benefit of this is that once we handle the request,
    // we update our bookkeeping (i.e. lastHandled...Id) rather than setting some dirty flag
    // back to false, as resetting the dirty flag (backed by a mutable state) would cause
    // another invalidation.
    private var targetBoundsProviderUpdateRequestId by mutableStateOf(0)

    private var lastHandledTargetProviderUpdateRequestId = 0

    /**
     * During composition foundMatch may change, the visibility of shared element at different call
     * sites may change due to the direction of the animation (e.g. enter -> exit) changing. These
     * changes may happen across different composition/subcompositions, and they may affect which
     * shared element defines the target bounds for the shared element bounds transform.
     *
     * Since the target bounds provider needed in later stages: lookahead place, and approach
     * measure, we follow a pattern of mark the property dirty and update it just-in-time before
     * it's used in lookahead place and approach measure. This allows us to avoid updating the
     * provider and resetting corresponding flags with only partial info, e.g. before all
     * (sub)compositions finish.
     */
    private fun updateTargetBoundsProvider() {
        if (targetBoundsProviderUpdateRequestId != lastHandledTargetProviderUpdateRequestId) {
            val newTargetBoundsProvider =
                (states.fastFirstOrNull { it.target } ?: states.firstOrNull())?.boundsProvider
            if (newTargetBoundsProvider != targetBoundsProvider) {
                lastTargetBoundsProvider = targetBoundsProvider
                targetBoundsProvider = newTargetBoundsProvider
            }
            if (newTargetBoundsProvider == null) targetBoundsProvider = null
            lastHandledTargetProviderUpdateRequestId = targetBoundsProviderUpdateRequestId
        }
    }

    /**
     * This should be called after [updateTargetBoundsProvider] so that [lastTargetBoundsProvider]
     * is up to date. This call will return null if there is no [lastTargetBoundsProvider] recorded
     * or the [lastTargetBoundsProvider] has never been placed.
     */
    private fun obtainBoundsFromLastTarget(): Rect? {
        return if (
            lastTargetBoundsProvider != null &&
                states.fastAny { state -> state.boundsProvider == lastTargetBoundsProvider }
        ) {
            lastTargetBoundsProvider?.lastBoundsInSharedTransitionScope
        } else {
            // Old target never got placed
            null
        }
    }

    fun onLookaheadPlaced(
        placementScope: Placeable.PlacementScope,
        state: SharedElementInternalState
    ) {
        updateTargetBoundsProvider()
        if (foundMatch && state.boundsAnimation.target) {
            with(placementScope) {
                coordinates?.let {
                    val lookaheadSize = it.size.toSize()
                    val topLeft =
                        with(state.sharedElement.scope) {
                            state.sharedElement.scope.lookaheadRoot.localLookaheadPositionOf(it)
                        }
                    // Only update bounds when offset is updated so as to not accidentally fire
                    // up animations, only to interrupt them in the same frame later on.
                    if (targetBounds?.topLeft != topLeft || targetBounds?.size != lookaheadSize) {
                        val target = Rect(topLeft, lookaheadSize)
                        targetBounds = target
                        if (currentBoundsWhenMatched == null) {
                            currentBoundsWhenMatched = obtainBoundsFromLastTarget() ?: target
                        }
                    }
                }
            }
        }
    }

    /**
     * Each state comes from a call site of sharedElement/sharedBounds of the same key. In most
     * cases there will be 1 (i.e. no match) or 2 (i.e. match found) states. In the interrupted
     * cases, there may be multiple scenes showing simultaneously, resulting in more than 2 shared
     * element states for the same key to be present. In those cases, we expect there to be only 1
     * state that is becoming visible, which we will use to derive target bounds. If none is
     * becoming visible, then we consider this an error case for the lack of target, and
     * consequently animate none of them.
     */
    val states = mutableStateListOf<SharedElementInternalState>()

    private fun hasVisibleContent(): Boolean = states.fastAny { it.boundsAnimation.target }

    /**
     * This gets called to update the target bounds. The 3 scenarios where
     * [invalidateTargetBoundsProvider] is needed are: when a shared element is 1) added, 2)
     * removed, or 3) getting a target state change.
     *
     * This is always called from an effect. Assume all compositional changes have been made in this
     * call.
     */
    fun invalidateTargetBoundsProvider() {
        val target = states.firstOrNull { it.target }
        if (target == null && targetBoundsProvider == null) return
        if (target?.boundsProvider == targetBoundsProvider) return

        // Do a round of filter before setting this, to reduce unnecessary churns.
        // Note: requestId is set to handled id + 1 no matter how much times it's requested
        // before it's handled. This avoids changing the mutable state many times, causing
        // more than 1 invalidations.
        targetBoundsProviderUpdateRequestId = lastHandledTargetProviderUpdateRequestId + 1
    }

    fun onSharedTransitionFinished() {
        foundMatch = states.size > 1 && hasVisibleContent()
        lastTargetBoundsProvider = null
        _targetBoundsWhenMatched = null
    }

    private val updateMatch: (SharedElement) -> Unit = { updateMatch() }

    private val observingVisibilityChange: () -> Unit = { hasVisibleContent() }

    fun addState(sharedElementState: SharedElementInternalState) {
        states.add(sharedElementState)
        scope.observeReads(this, updateMatch, observingVisibilityChange)
    }

    fun removeState(sharedElementState: SharedElementInternalState) {
        states.remove(sharedElementState)
        if (states.isEmpty()) {
            updateMatch()
            scope.clearObservation(scope = this)
        } else {
            scope.observeReads(scope = this, updateMatch, observingVisibilityChange)
        }
    }
}

internal class SharedElementInternalState(
    sharedElement: SharedElement,
    boundsAnimation: BoundsAnimation,
    placeHolderSize: SharedTransitionScope.PlaceHolderSize,
    renderOnlyWhenVisible: Boolean,
    overlayClip: SharedTransitionScope.OverlayClip,
    renderInOverlayDuringTransition: Boolean,
    userState: SharedTransitionScope.SharedContentState,
    zIndex: Float
) : LayerRenderer, RememberObserver {

    override var zIndex: Float by mutableFloatStateOf(zIndex)

    var renderInOverlayDuringTransition: Boolean by mutableStateOf(renderInOverlayDuringTransition)
    var sharedElement: SharedElement by mutableStateOf(sharedElement)
    var boundsAnimation: BoundsAnimation by mutableStateOf(boundsAnimation)
    var placeHolderSize: SharedTransitionScope.PlaceHolderSize by mutableStateOf(placeHolderSize)
    var renderOnlyWhenVisible: Boolean by mutableStateOf(renderOnlyWhenVisible)
    var overlayClip: SharedTransitionScope.OverlayClip by mutableStateOf(overlayClip)
    var userState: SharedTransitionScope.SharedContentState by mutableStateOf(userState)

    internal var clipPathInOverlay: Path? = null

    override fun drawInOverlay(drawScope: DrawScope) {
        val layer = layer ?: return
        // If currentBoundsWhenMatched == null, it means the shared element has not been properly
        // placed since foundMatch is set. This could be due to some nodes being composed but
        // not measured or laid out e.g. precompose. Such a node would not be rendered in place
        // either. Hence skip rendering in overlay.
        if (shouldRenderInOverlay && sharedElement.currentBoundsWhenMatched != null) {
            with(drawScope) {
                val (x, y) = sharedElement.currentBoundsWhenMatched?.topLeft!!
                clipPathInOverlay?.let { clipPath(it) { translate(x, y) { drawLayer(layer) } } }
                    ?: translate(x, y) { drawLayer(layer) }
            }
        }
    }

    override var parentState: SharedElementInternalState? = null

    val target: Boolean
        get() = boundsAnimation.target

    var boundsProvider: BoundsProvider? = null

    // Delegate the property to a mutable state, so that when layer is updated, the rendering
    // gets invalidated.
    var layer: GraphicsLayer? by mutableStateOf(null)

    private val shouldRenderBasedOnTarget: Boolean
        get() = sharedElement.targetBoundsProvider == this.boundsProvider || !renderOnlyWhenVisible

    internal val shouldRenderInOverlay: Boolean
        get() =
            shouldRenderBasedOnTarget &&
                sharedElement.foundMatch &&
                // Render in overlay during transition only takes effect during transition (i.e.
                // when transition is active)
                renderInOverlayDuringTransition &&
                sharedElement.scope.isTransitionActive

    val shouldRenderInPlace: Boolean
        get() = !sharedElement.foundMatch || (!shouldRenderInOverlay && shouldRenderBasedOnTarget)

    override fun onRemembered() {
        sharedElement.scope.onStateAdded(this)
        sharedElement.invalidateTargetBoundsProvider()
    }

    override fun onForgotten() {
        sharedElement.scope.onStateRemoved(this)
        sharedElement.invalidateTargetBoundsProvider()
    }

    override fun onAbandoned() {}
}

internal interface BoundsProvider {
    val lastBoundsInSharedTransitionScope: Rect?
}
