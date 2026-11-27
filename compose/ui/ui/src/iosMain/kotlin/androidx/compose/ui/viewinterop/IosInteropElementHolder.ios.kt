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

package androidx.compose.ui.viewinterop

import androidx.compose.runtime.CompositeKeyHashCode
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.UnplacedAwareModifierNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.scene.attachedCompositionContext
import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.toCGRect
import platform.UIKit.UIView
import platform.UIKit.accessibilityFrame

internal abstract class IosInteropElementHolder<T : InteropView>(
    factory: () -> T,
    interopContainer: InteropContainer,
    private val interopWrappingView: InteropWrappingView,
    properties: UIKitInteropProperties,
    compositeKeyHashCode: CompositeKeyHashCode,
) : TypedInteropViewHolder<T>(
        factory = factory,
        interopContainer = interopContainer,
        group = interopWrappingView,
        compositeKeyHashCode = compositeKeyHashCode,
    ),
    InteropLayoutNodeHolder {
    constructor(
        factory: () -> T,
        interopContainer: InteropContainer,
        properties: UIKitInteropProperties,
        compositeKeyHashCode: CompositeKeyHashCode,
        compositionContext: CompositionContext,
    ) : this(
        factory = factory,
        interopContainer = interopContainer,
        interopWrappingView = InteropWrappingView(
            interactionMode = properties.interactionMode,
            compositionContext = compositionContext
        ),
        properties = properties,
        compositeKeyHashCode = compositeKeyHashCode
    )

    /**
     * The UIView to be embedded in the wrapping view.
     */
    protected abstract val userComponentView: UIView
    private var currentUnclippedRect: IntRect? = null
    private var currentRootSize: IntSize? = null
    private var currentClippedRect: IntRect? = null
    private var currentUserComponentRect: IntRect? = null
    private val screenDensity get() = container.root.density
    private val layout = InteropElementLayout(
        group = interopWrappingView,
        userComponent = userComponentView,
    )
    private val interopMask = InteropMask(
        interopWrappingView = interopWrappingView,
        density = { screenDensity },
        finalFrameUpdateScheduler = interopContainer.createFinalFrameUpdateScheduler(),
    )
    override val measurePolicy: MeasurePolicy get() = layout.measurePolicy

    val placedAsOverlay: Boolean get() = properties.placedAsOverlay

    var properties = properties
        set(value) {
            if (field != value) {
                field = value
                onPropertiesChanged()
            }
        }

    var groupCompositionContext by group::attachedCompositionContext

    init {
        layout.attachUserComponent()
        onPropertiesChanged()
    }

    override fun layoutAccordingTo(layoutCoordinates: LayoutCoordinates) {
        val rootCoordinates = layoutCoordinates.findRootCoordinates()

        val unclippedRect = rootCoordinates
            .localBoundingBoxOf(
                sourceCoordinates = layoutCoordinates,
                clipBounds = false
            ).roundToIntRect()

        val clippedRect = rootCoordinates
            .localBoundingBoxOf(
                sourceCoordinates = layoutCoordinates,
                clipBounds = true
            ).roundToIntRect()

        val rootSize = rootCoordinates.size

        val groupFrame = if (clippedRect != currentClippedRect) {
            clippedRect
        } else {
            null
        }

        // Offset the component inside the wrapping view to preserve its root-space frame when the
        // wrapping view is clipped.
        val userComponentRect = IntRect(
            offset = unclippedRect.topLeft - clippedRect.topLeft,
            size = unclippedRect.size,
        )

        // The user component's local frame changes only when its offset inside the clipping group
        // changes.
        val userComponentFrame = if (userComponentRect != currentUserComponentRect) {
            userComponentRect
        } else {
            null
        }

        if (
            groupFrame != null ||
            userComponentFrame != null ||
            rootSize != currentRootSize ||
            unclippedRect != currentUnclippedRect
        ) {
            container.scheduleFrameSynchronizedUpdate { transactionScope ->
                UIView.performWithoutAnimation {
                    layout.updateFrames(
                        groupFrame = groupFrame?.toCGRect(screenDensity),
                        userComponentFrame = userComponentFrame?.toCGRect(screenDensity),
                    )
                    if (groupFrame != null) {
                        group.accessibilityFrame = unclippedRect.toCGRect(screenDensity)
                    }
                }

                interopMask.update(
                    rootSize = rootSize,
                    unclippedRect = unclippedRect,
                    clippedRect = clippedRect,
                    transactionScope = transactionScope,
                )
            }
        }

        currentUnclippedRect = unclippedRect
        currentRootSize = rootSize
        currentClippedRect = clippedRect
        currentUserComponentRect = userComponentRect
    }

    override fun dispatchToView(pointerEvent: PointerEvent) {
        // No-op, we can't dispatch events to UIView or UIViewController directly, see
        // [InteractionUIView] logic
    }

    /** Applies the opaque regions owned by higher-z scene layers. */
    fun updateSceneLayerMask(occluderRects: List<IntRect>) {
        interopMask.updateSceneLayerMask(occluderRects)
    }

    override fun removeInteropView(root: InteropViewGroup) {
        interopMask.clear()
        super.removeInteropView(root)
    }

    override fun onReuse() {
        super.onReuse()
        // A reused overlay is hidden until its mask is rebuilt. Invalidate the cached layout so
        // the next placement reapplies frames even when the holder returns to the same geometry.
        currentUnclippedRect = null
        currentRootSize = null
        currentClippedRect = null
        currentUserComponentRect = null
        container.scheduleUpdate {
            interopMask.clear()
        }
    }

    override fun onRelease() {
        super.onRelease()
        container.scheduleUpdate {
            interopMask.clear()
        }
    }

    /**
     * This logic is similar for both interop view and view controller holders.
     */
    override fun changeInteropViewIndex(root: InteropViewGroup, index: Int) {
        root.insertSubview(view = group, atIndex = index.toLong())
    }

    private fun Modifier.clearBackgroundIfNeeded(): Modifier =
        if (placedAsOverlay) {
            this
        } else {
            drawBehind {
                drawRect(
                    color = Color.Transparent,
                    blendMode = BlendMode.Clear
                )
            }
        }

    private fun onPropertiesChanged() {
        interopWrappingView.interactionMode = properties.interactionMode
        interopWrappingView.placedAsOverlay = properties.placedAsOverlay
        layout.updateSafeAreaInsetsPolicy(properties.safeAreaInsetsPolicy)

        // A new overlay must resolve its first mask. A switch from overlay to background must
        // remove an installed mask; unmasked overlays need no work because UIKit clipping is
        // restored synchronously by the placedAsOverlay setter.
        if (properties.placedAsOverlay || interopMask.hasInstalledMask) {
            container.scheduleUpdate {
                interopMask.scheduleUpdate()
            }
        }

        platformModifier = Modifier
            .pointerInteropFilter(
                isInteractive = properties.isInteractive,
                this
            )
            .clearBackgroundIfNeeded()
            .nativeAccessibility(
                isEnabled = properties.isNativeAccessibilityEnabled,
                interopWrappingView
            )
            .sceneInteropLayerAware(
                isEnabled = properties.placedAsOverlay,
                this
            )
    }
}

private fun Modifier.sceneInteropLayerAware(
    isEnabled: Boolean,
    holder: IosInteropElementHolder<*>
): Modifier =
    if (isEnabled) this then SceneInteropLayerAwareModifierElement(holder) else this

private data class SceneInteropLayerAwareModifierElement(
    val holder: IosInteropElementHolder<*>,
) : ModifierNodeElement<SceneInteropLayerAwareModifierNode>() {
    override fun create() = SceneInteropLayerAwareModifierNode(holder)

    override fun update(node: SceneInteropLayerAwareModifierNode) {}
}

private class SceneInteropLayerAwareModifierNode(
    private val holder: IosInteropElementHolder<*>,
) : Modifier.Node(),
    LayoutAwareModifierNode,
    UnplacedAwareModifierNode,
    GlobalPositionAwareModifierNode {
    private var isPlaced = false
    private var layerNode: SceneInteropLayerModifierNode? = null

    override fun onPlaced(coordinates: LayoutCoordinates) {
        val currentLayerNode =
            findNearestAncestor(SceneInteropLayerNodeKey) as? SceneInteropLayerModifierNode
        if (currentLayerNode != layerNode) {
            layerNode?.unregisterHolder(holder)
            layerNode = currentLayerNode
        }
        currentLayerNode?.registerHolder(holder)

        isPlaced = true
    }

    override fun onUnplaced() {
        layerNode?.unregisterHolder(holder)
        layerNode = null
        isPlaced = false
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        // Global-position callbacks can arrive before the placement callback. Register the layer
        // then so the first layout can already update the interop mask.
        if (!isPlaced) {
            onPlaced(coordinates)
        }
    }

    override fun onDetach() {
        if (isPlaced) {
            onUnplaced()
        }
        layerNode = null
        super.onDetach()
    }
}
