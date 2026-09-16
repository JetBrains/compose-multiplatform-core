/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.compose.ui.graphics.layer

import androidx.annotation.IntRange
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.platform.PlatformGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize

@OptIn(InternalComposeUiApi::class)
actual class GraphicsLayer @InternalComposeUiApi constructor(
    @property:InternalComposeUiApi val platformGraphicsLayer: PlatformGraphicsLayer,
) {
    private var outlineDirty = true
    private var roundRectOutlineTopLeft: Offset = Offset.Zero
    private var roundRectOutlineSize: Size = Size.Unspecified
    private var roundRectCornerRadius: Float = 0f

    private var internalOutline: Outline? = null
    private var outlinePath: Path? = null

    /** Tracks the amount of the parent layers currently drawing this layer as a child. */
    private var parentLayerUsages = 0

    /** Keeps track of the child layers we currently draw into this layer. */
    private val childDependenciesTracker = ChildLayerDependenciesTracker()

    actual var compositingStrategy: CompositingStrategy
        get() = platformGraphicsLayer.compositingStrategy
        set(value) {
            if (platformGraphicsLayer.compositingStrategy != value) {
                platformGraphicsLayer.compositingStrategy = value
            }
        }

    actual var pivotOffset: Offset
        get() = platformGraphicsLayer.pivotOffset
        set(value) {
            if (platformGraphicsLayer.pivotOffset != value) {
                platformGraphicsLayer.pivotOffset = value
            }
        }

    actual var alpha: Float
        get() = platformGraphicsLayer.alpha
        set(value) {
            if (platformGraphicsLayer.alpha != value) {
                platformGraphicsLayer.alpha = value
            }
        }

    actual var scaleX: Float
        get() = platformGraphicsLayer.scaleX
        set(value) {
            if (platformGraphicsLayer.scaleX != value) {
                platformGraphicsLayer.scaleX = value
            }
        }

    actual var scaleY: Float
        get() = platformGraphicsLayer.scaleY
        set(value) {
            if (platformGraphicsLayer.scaleY != value) {
                platformGraphicsLayer.scaleY = value
            }
        }

    actual var translationX: Float
        get() = platformGraphicsLayer.translationX
        set(value) {
            if (platformGraphicsLayer.translationX != value) {
                platformGraphicsLayer.translationX = value
            }
        }

    actual var translationY: Float
        get() = platformGraphicsLayer.translationY
        set(value) {
            if (platformGraphicsLayer.translationY != value) {
                platformGraphicsLayer.translationY = value
            }
        }

    actual var ambientShadowColor: Color
        get() = platformGraphicsLayer.ambientShadowColor
        set(value) {
            if (platformGraphicsLayer.ambientShadowColor != value) {
                platformGraphicsLayer.ambientShadowColor = value
            }
        }

    actual var spotShadowColor: Color
        get() = platformGraphicsLayer.spotShadowColor
        set(value) {
            if (platformGraphicsLayer.spotShadowColor != value) {
                platformGraphicsLayer.spotShadowColor = value
            }
        }

    actual var blendMode: BlendMode
        get() = platformGraphicsLayer.blendMode
        set(value) {
            if (platformGraphicsLayer.blendMode != value) {
                platformGraphicsLayer.blendMode = value
            }
        }

    actual var colorFilter: ColorFilter?
        get() = platformGraphicsLayer.colorFilter
        set(value) {
            if (platformGraphicsLayer.colorFilter != value) {
                platformGraphicsLayer.colorFilter = value
            }
        }

    actual var rotationX: Float
        get() = platformGraphicsLayer.rotationX
        set(value) {
            if (platformGraphicsLayer.rotationX != value) {
                platformGraphicsLayer.rotationX = value
            }
        }

    actual var rotationY: Float
        get() = platformGraphicsLayer.rotationY
        set(value) {
            if (platformGraphicsLayer.rotationY != value) {
                platformGraphicsLayer.rotationY = value
            }
        }

    actual var rotationZ: Float
        get() = platformGraphicsLayer.rotationZ
        set(value) {
            if (platformGraphicsLayer.rotationZ != value) {
                platformGraphicsLayer.rotationZ = value
            }
        }

    actual var cameraDistance: Float
        get() = platformGraphicsLayer.cameraDistance
        set(value) {
            if (platformGraphicsLayer.cameraDistance != value) {
                platformGraphicsLayer.cameraDistance = value
            }
        }

    actual var renderEffect: RenderEffect?
        get() = platformGraphicsLayer.renderEffect
        set(value) {
            if (platformGraphicsLayer.renderEffect != value) {
                platformGraphicsLayer.renderEffect = value
            }
        }

    actual var topLeft: IntOffset = IntOffset.Zero
        set(value) {
            if (field != value) {
                field = value
                platformGraphicsLayer.setBounds(value, size)
            }
        }

    actual var size: IntSize = IntSize.Zero
        private set(value) {
            if (field != value) {
                field = value
                platformGraphicsLayer.setBounds(topLeft, value)
                // the layer size affects an outline which matches the layer size
                if (roundRectOutlineSize.isUnspecified) {
                    outlineDirty = true
                    configureOutlineAndClip()
                }
            }
        }

    actual var shadowElevation: Float
        get() = platformGraphicsLayer.shadowElevation
        set(value) {
            if (platformGraphicsLayer.shadowElevation != value) {
                platformGraphicsLayer.shadowElevation = value
                outlineDirty = true
                configureOutlineAndClip()
            }
        }

    actual var clip: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                outlineDirty = true
                configureOutlineAndClip()
            }
        }

    actual val outline: Outline
        get() {
            val tmpOutline = internalOutline
            val tmpPath = outlinePath
            return if (tmpOutline != null) {
                tmpOutline
            } else if (tmpPath != null) {
                Outline.Generic(tmpPath).also { internalOutline = it }
            } else {
                resolveOutlinePosition { outlineTopLeft, outlineSize ->
                    val left = outlineTopLeft.x
                    val top = outlineTopLeft.y
                    val right = left + outlineSize.width
                    val bottom = top + outlineSize.height
                    val cornerRadius = this.roundRectCornerRadius
                    if (cornerRadius > 0f) {
                        Outline.Rounded(
                            RoundRect(left, top, right, bottom, CornerRadius(cornerRadius))
                        )
                    } else {
                        Outline.Rectangle(Rect(left, top, right, bottom))
                    }
                }.also { internalOutline = it }
            }
        }

    actual fun setPathOutline(path: Path) {
        resetOutlineParams()
        this.outlinePath = path
        configureOutlineAndClip()
    }

    actual fun setRoundRectOutline(topLeft: Offset, size: Size, cornerRadius: Float) {
        if (this.roundRectOutlineTopLeft != topLeft ||
            this.roundRectOutlineSize != size ||
            this.roundRectCornerRadius != cornerRadius ||
            this.outlinePath != null
        ) {
            resetOutlineParams()
            this.roundRectOutlineTopLeft = topLeft
            this.roundRectOutlineSize = size
            this.roundRectCornerRadius = cornerRadius
            configureOutlineAndClip()
        }
    }

    actual fun setRectOutline(topLeft: Offset, size: Size) {
        setRoundRectOutline(topLeft, size, 0f)
    }

    actual var isReleased: Boolean = false
        private set

    actual fun record(
        density: Density,
        layoutDirection: LayoutDirection,
        size: IntSize,
        block: DrawScope.() -> Unit,
    ) {
        this.size = size
        platformGraphicsLayer.record(density, layoutDirection, this) {
            childDependenciesTracker.withTracking(
                onDependencyRemoved = { it.onRemovedFromParentLayer() }
            ) { block() }
        }
    }

    actual suspend fun toImageBitmap(): ImageBitmap {
        check(!isReleased) { "Cannot call toImageBitmap on a released GraphicsLayer" }
        return ImageBitmap(size.width, size.height).apply {
            this@GraphicsLayer.draw(Canvas(this), parentLayer = null)
        }
    }

    internal actual fun draw(canvas: Canvas, parentLayer: GraphicsLayer?) {
        if (isReleased) return
        configureOutlineAndClip()
        parentLayer?.addSubLayer(this)
        platformGraphicsLayer.draw(canvas)
    }

    private fun addSubLayer(graphicsLayer: GraphicsLayer) {
        if (childDependenciesTracker.onDependencyAdded(graphicsLayer)) {
            graphicsLayer.onAddedToParentLayer()
        }
    }

    private fun onAddedToParentLayer() {
        parentLayerUsages++
    }

    private fun onRemovedFromParentLayer() {
        parentLayerUsages--
        discardContentIfReleasedAndHaveNoParentLayerUsages()
    }

    private fun configureOutlineAndClip() {
        if (!outlineDirty) return
        val outlineIsNeeded = clip || platformGraphicsLayer.shadowElevation > 0f
        platformGraphicsLayer.setOutline(if (outlineIsNeeded) outline else null, clip)
        outlineDirty = false
    }

    private fun resetOutlineParams() {
        internalOutline = null
        outlinePath = null
        roundRectOutlineSize = Size.Unspecified
        roundRectOutlineTopLeft = Offset.Zero
        roundRectCornerRadius = 0f
        outlineDirty = true
    }

    private inline fun <T> resolveOutlinePosition(block: (Offset, Size) -> T): T {
        val layerSize = this.size.toSize()
        val rRectTopLeft = roundRectOutlineTopLeft
        val rRectSize = roundRectOutlineSize

        val outlineSize =
            if (rRectSize.isUnspecified) {
                layerSize
            } else {
                rRectSize
            }
        return block(rRectTopLeft, outlineSize)
    }

    internal fun release() {
        if (!isReleased) {
            isReleased = true
            discardContentIfReleasedAndHaveNoParentLayerUsages()
        }
    }

    private fun discardContentIfReleasedAndHaveNoParentLayerUsages() {
        if (isReleased && parentLayerUsages == 0) {
            // discarding means we don't draw children layer anymore and need to remove dependencies:
            childDependenciesTracker.removeDependencies { it.onRemovedFromParentLayer() }
            platformGraphicsLayer.discardDisplayList()
        }
    }

    actual fun setOutsets(
        @IntRange(from = 0) left: Int,
        @IntRange(from = 0) top: Int,
        @IntRange(from = 0) right: Int,
        @IntRange(from = 0) bottom: Int,
    ) {
        platformGraphicsLayer.setOutsets(left, top, right, bottom)
    }
}
