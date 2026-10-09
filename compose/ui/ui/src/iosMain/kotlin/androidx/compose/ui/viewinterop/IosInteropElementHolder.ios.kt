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
import androidx.compose.ui.scene.attachedCompositionContext
import androidx.compose.ui.uikit.density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.toCGRect
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toRect
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
    private var currentClippedRect: IntRect? = null
    private var currentUserComponentRect: IntRect? = null
    private val layout = InteropElementLayout(
        interopWrappingView = interopWrappingView,
        userComponent = userComponentView,
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
        val screenDensity = container.root.density

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

        if (currentUnclippedRect == unclippedRect && currentClippedRect == clippedRect) {
            return
        }

        val groupFrame = if (clippedRect != currentClippedRect) {
            clippedRect
                .toRect()
                .toDpRect(screenDensity)
                .toCGRect()
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
            currentUserComponentRect = userComponentRect

            userComponentRect
                .toRect()
                .toDpRect(screenDensity)
                .toCGRect()
        } else {
            null
        }

        if (groupFrame != null || userComponentFrame != null) {
            container.scheduleUpdate {
                UIView.performWithoutAnimation {
                    layout.updateFrames(
                        groupFrame = groupFrame,
                        userComponentFrame = userComponentFrame,
                    )
                    if (groupFrame != null) {
                        group.accessibilityFrame = unclippedRect
                            .toRect()
                            .toDpRect(screenDensity)
                            .toCGRect()
                    }
                }
            }
        }

        currentUnclippedRect = unclippedRect
        currentClippedRect = clippedRect

    }

    override fun dispatchToView(pointerEvent: PointerEvent) {
        // No-op, we can't dispatch events to UIView or UIViewController directly, see
        // [InteractionUIView] logic
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
        layout.updateSafeAreaInsetsPolicy(properties.safeAreaInsetsPolicy)
        // required to properly clip the content of the wrapping view in case interop unclipped
        // bounds are larger than clipped bounds
        interopWrappingView.clipsToBounds = !properties.placedAsOverlay

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
    }
}