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

package androidx.compose.ui.window.v2

import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.MeasurableRootContent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpInsets
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.plus
import androidx.compose.ui.unit.requireReal
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.width
import androidx.compose.ui.window.WindowLocationTracker
import androidx.compose.ui.window.density
import androidx.compose.ui.window.roundToDimension
import androidx.compose.ui.window.toDpInsets
import androidx.compose.ui.window.toDpOffset
import java.awt.GraphicsDevice


/**
 * The scope in which [WindowScreenProvider] is evaluated.
 */
@ExperimentalComposeUiApi
class WindowScreenProviderScope internal constructor(
    devices: List<GraphicsDevice>,
    defaultDevice: GraphicsDevice,
) {
    /**
     * The list of screens on which the window can be placed.
     */
    val screens: List<Screen> = devices.map { Screen(it) }

    /**
     * The default screen, on which the window should typically be placed.
     */
    val defaultScreen: Screen = Screen(defaultDevice)
}

/**
 * Provides the screen on which the window will be placed.
 */
@ExperimentalComposeUiApi
fun interface WindowScreenProvider {
    /**
     * Returns the screen on which the window will be placed.
     *
     * When implementing this function, use the given [WindowGeometryProviderScope] to examine the
     * available screens and determine the appropriate one for the window.
     */
    fun WindowScreenProviderScope.getScreen(): Screen

    @ExperimentalComposeUiApi
    companion object {
        /**
         * Returns the default screen for a new window.
         */
        val Default = WindowScreenProvider { defaultScreen }
    }
}

/**
 * The scope in which window geometry providers (e.g. [WindowBoundsProvider]) are evaluated.
 */
@ExperimentalComposeUiApi
class WindowGeometryProviderScope internal constructor(
    window: java.awt.Window,
    private val measurableContentProvider: () -> MeasurableRootContent,
): Density {
    init {
        require(window.isDisplayable) {
            "Window must be displayable before it can be used in WindowGeometryProviderScope"
        }
    }

    /**
     * The screen on which the window will be placed.
     */
    val screen: Screen = Screen(window.graphicsConfiguration.device)

    /**
     * The density of the window.
     */
    private val windowDensity: Density = window.density

    override val density: Float
        get() = windowDensity.density

    override val fontScale: Float
        get() = windowDensity.fontScale

    /**
     * The insets of the window.
     */
    val windowInsets: DpInsets = window.insets.toDpInsets()

    /**
     * Returns the size a window should have, given the size of its content.
     *
     * The content size is expanded by [windowInsets] and then constrained to
     * [Screen.availableBounds].
     */
    fun contentToWindowSize(contentSize: DpSize): DpSize =
        (contentSize + windowInsets).coerceAtMost(screen.availableBounds.size)

    /**
     * Represents the composable content of the window, which can be queried for its preferred size
     * properties.
     */
    val windowContent: MeasurableRootContent
        get() = measurableContentProvider()

    /**
     * Evaluates the given [WindowSizeProvider] in this scope.
     */
    fun WindowSizeProvider.getSize(): DpSize = with(this) {
        this@WindowGeometryProviderScope.getSize()
    }

    /**
     * Evaluates the given [WindowPositionProvider] in this scope.
     */
    fun WindowPositionProvider.getPosition(size: DpSize): DpOffset = with(this) {
        this@WindowGeometryProviderScope.getPosition(size)
    }

    /**
     * Evaluates the given [WindowBoundsProvider] in this scope.
     */
    fun WindowBoundsProvider.getBounds(): DpRect = with(this) {
        this@WindowGeometryProviderScope.getBounds()
    }
}

/**
 * Provides the bounds of the window.
 */
@ExperimentalComposeUiApi
interface WindowBoundsProvider {
    /**
     * Returns the bounds of the window.
     *
     * When implementing this function, use the given [WindowGeometryProviderScope] to examine the
     * geometry of the screen and determine the appropriate bounds for the window.
     *
     * All coordinates in the returned [DpRect] must be [Dp.isSpecified] and [Dp.isFinite].
     */
    fun WindowGeometryProviderScope.getBounds(): DpRect

    @ExperimentalComposeUiApi
    companion object {
        /**
         * Returns the default position and size for a new window.
         */
        val Default = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Default,
            positionProvider = WindowPositionProvider.Default
        )

        /**
         * Aligns the window within the screen according to [alignment] and [offset].
         *
         * @param alignment The alignment of the window relative to the screen.
         * @param offset An additional absolute offset added after aligning.
         * @param sizeProvider Provides the size of the window.
         */
        fun AlignedToScreen(
            alignment: Alignment,
            offset: DpOffset = DpOffset.Zero,
            sizeProvider: WindowSizeProvider = WindowSizeProvider.Default
        ): WindowBoundsProvider = WindowBoundsProvider {
            val size = sizeProvider.getSize().requireReal()
            val availableBounds = screen.availableBounds

            val position = alignment.align(
                size = size.roundToIntSize(),
                space = availableBounds.size.roundToIntSize(),
                layoutDirection = LayoutDirection.Ltr
            )
            val left = availableBounds.left + position.x.dp + offset.x
            val top = availableBounds.top + position.y.dp + offset.y
            DpRect(
                left = left,
                top = top,
                right = left + size.width,
                bottom = top + size.height
            )
        }

        /**
         * Positions the window at the given [bounds].
         *
         * @param bounds The bounds of the window.
         *
         */
        fun Absolute(bounds: DpRect): WindowBoundsProvider {
            bounds.requireReal()
            return WindowBoundsProvider { bounds }
        }
    }
}

/**
 * Creates a [WindowBoundsProvider] from the given [bounds] function.
 */
@ExperimentalComposeUiApi
fun WindowBoundsProvider(
    bounds: WindowGeometryProviderScope.() -> DpRect,
) = object : WindowBoundsProvider {
    override fun WindowGeometryProviderScope.getBounds() = bounds()
}

/**
 * Combines a [WindowSizeProvider] and [WindowPositionProvider] into a [WindowBoundsProvider].
 */
@ExperimentalComposeUiApi
fun WindowBoundsProvider(
    sizeProvider: WindowSizeProvider = WindowSizeProvider.Default,
    positionProvider: WindowPositionProvider = WindowPositionProvider.Default,
): WindowBoundsProvider = WindowBoundsProvider {
    val size = sizeProvider.getSize().requireReal()
    val position = positionProvider.getPosition(size)
    DpRect(position, size)
}

/**
 * Provides the position of the window.
 *
 * Use this in conjunction with a [WindowSizeProvider] to construct a [WindowBoundsProvider].
 */
@ExperimentalComposeUiApi
fun interface WindowPositionProvider {
    /**
     * Returns the position of the window.
     *
     * When implementing this function, use the given [WindowGeometryProviderScope] to examine the
     * geometry of the screen and determine the appropriate position for the window.
     *
     * All coordinates in the returned [DpOffset] must be [Dp.isSpecified] and [Dp.isFinite].
     * The [DpOffset] itself must also be [DpOffset.isSpecified].
     */
    fun WindowGeometryProviderScope.getPosition(size: DpSize): DpOffset

    @ExperimentalComposeUiApi
    companion object {
        /**
         * Returns the default position for a new window.
         */
        val Default = WindowPositionProvider { size ->
            WindowLocationTracker.getCascadeLocationFor(
                graphicsDevice = screen.device,
                windowSize = size.roundToDimension()
            ).toDpOffset()
        }

        /**
         * Positions the window at the given [position].
         *
         * @param position The position of the window.
         */
        fun Absolute(position: DpOffset): WindowPositionProvider {
            position.requireReal()
            return WindowPositionProvider { position }
        }
    }
}

/**
 * Provides the size of the window.
 *
 * Use this in conjunction with a [WindowPositionProvider] to construct a [WindowBoundsProvider].
 */
@ExperimentalComposeUiApi
fun interface WindowSizeProvider {
    /**
     * Returns the size of the window.
     *
     * When implementing this function, use the given [WindowGeometryProviderScope] to examine the
     * geometry of the screen and the size properties of the window's content to determine the
     * appropriate size for the window.
     *
     * All coordinates in the returned [DpSize] must be [Dp.isSpecified] and [Dp.isFinite].
     * The [DpSize] itself must also be [DpSize.isSpecified].
     */
    fun WindowGeometryProviderScope.getSize(): DpSize

    @ExperimentalComposeUiApi
    companion object {
        /**
         * Sets the size of the window to the default one.
         */
        val Default = Fixed(DpSize(800.dp, 600.dp))

        /**
         * Sets the size of the window to the given [size].
         *
         * @param size The size of the window.
         */
        fun Fixed(size: DpSize): WindowSizeProvider {
            size.requireReal()
            return WindowSizeProvider { size }
        }

        /**
         * Sets the size of the window to the given [width] and [height].
         *
         * @param width The width of the window.
         * @param height The height of the window.
         */
        fun Fixed(width: Dp, height: Dp) = Fixed(DpSize(width, height))

        /**
         * Sets the size of the window to its preferred size, constrained only by the size of the
         * screen.
         *
         * The preferred size is computed by measuring the content with infinite
         * [Constraints], and adding the window's insets to that.
         */
        val Unconstrained = WindowSizeProvider {
            windowContent.measuringIn(Constraints()) {
                contentToWindowSize(
                    DpSize(
                        width = it.measuredWidth.toDp(),
                        height = it.measuredHeight.toDp()
                    )
                )
            }
        }

        /**
         * Sets one dimension of the window to its intrinsic size at the given [otherDimensionSize]
         * on the other dimension.
         */
        private fun IntrinsicDimension(
            isWidth: Boolean,
            intrinsicSize: WindowIntrinsicSize,
            otherDimensionSize: Dp,
            otherDimensionName: String,
        ): WindowSizeProvider {
            otherDimensionSize.requireReal(otherDimensionName)
            return WindowSizeProvider {
                val otherDimensionPx = otherDimensionSize.roundToPx()
                val width: Dp
                val height: Dp
                if (isWidth) {
                    width = intrinsicSize.widthOf(windowContent, otherDimensionPx).toDp()
                    height = otherDimensionSize
                } else {
                    width = otherDimensionSize
                    height = intrinsicSize.heightOf(windowContent, otherDimensionPx).toDp()
                }
                contentToWindowSize(
                    DpSize(
                        width = width,
                        height = height
                    )
                )
            }
        }

        /**
         * Sets the width of the window to its minimum intrinsic width at the given [height].
         *
         * The height of the window is set to [height].
         *
         * @param height The height of the window.
         *
         * @see [IntrinsicMeasurable.minIntrinsicWidth]
         */
        fun MinIntrinsicWidth(height: Dp) = IntrinsicDimension(
            isWidth = true,
            intrinsicSize = WindowIntrinsicSize.Min,
            otherDimensionSize = height,
            otherDimensionName = "height"
        )

        /**
         * Sets the width of the window to its maximum intrinsic width at the given [height].
         *
         * The height of the window is set to [height].
         *
         * @param height The height of the window.
         *
         * @see [IntrinsicMeasurable.maxIntrinsicWidth]
         */
        fun MaxIntrinsicWidth(height: Dp) = IntrinsicDimension(
            isWidth = true,
            intrinsicSize = WindowIntrinsicSize.Max,
            otherDimensionSize = height,
            otherDimensionName = "height"
        )

        /**
         * Sets the height of the window to its minimum intrinsic height at the given [width].
         *
         * The width of the window is set to [width].
         *
         * @param width The width of the window.
         *
         * @see [IntrinsicMeasurable.minIntrinsicHeight]
         */
        fun MinIntrinsicHeight(width: Dp) = IntrinsicDimension(
            isWidth = false,
            intrinsicSize = WindowIntrinsicSize.Min,
            otherDimensionSize = width,
            otherDimensionName = "width"
        )

        /**
         * Sets the height of the window to its maximum intrinsic height at the given [width].
         *
         * The width of the window is set to [width].
         *
         * @param width The width of the window.
         *
         * @see [IntrinsicMeasurable.maxIntrinsicHeight]
         */
        fun MaxIntrinsicHeight(width: Dp) = IntrinsicDimension(
            isWidth = false,
            intrinsicSize = WindowIntrinsicSize.Max,
            otherDimensionSize = width,
            otherDimensionName = "width"
        )

        /**
         * Sets the primary dimension of the window to its intrinsic size, unconstrained at the
         * secondary dimension, and the secondary dimension to its intrinsic size at the size of
         * the primary dimension.
         *
         * This is useful for cases where the window is fixed on one dimension, but the one is
         * flexible.
         *
         * @param isWidth Whether the primary dimension is width.
         * @param intrinsicPrimary The intrinsic width to measure.
         * @param intrinsicSecondary The intrinsic height to measure.
         */
        private fun IntrinsicDimensionWithMatchingOtherDimension(
            isWidth: Boolean,
            intrinsicPrimary: WindowIntrinsicSize,
            intrinsicSecondary: WindowIntrinsicSize,
        ) = WindowSizeProvider {
            val availableScreenBounds = screen.availableBounds
            val width: Int
            val height: Int
            if (isWidth) {
                width = intrinsicPrimary.widthOf(windowContent, availableScreenBounds.height.roundToPx())
                height = intrinsicSecondary.heightOf(windowContent, width)
            } else {
                height = intrinsicPrimary.heightOf(windowContent, availableScreenBounds.width.roundToPx())
                width = intrinsicSecondary.widthOf(windowContent, height)
            }
            contentToWindowSize(
                DpSize(
                    width = width.toDp(),
                    height = height.toDp()
                )
            )
        }

        /**
         * Sets the width of the window to its intrinsic width at unconstrained height, and
         * the height of the window to its intrinsic height at that width.
         *
         * This is useful for cases where the window has a fixed width, but the height is flexible.
         *
         * @param intrinsicWidth The intrinsic width to measure.
         * @param intrinsicHeight The intrinsic height to measure.
         */
        fun IntrinsicWidthWithMatchingIntrinsicHeight(
            intrinsicWidth: WindowIntrinsicSize,
            intrinsicHeight: WindowIntrinsicSize,
        ): WindowSizeProvider = IntrinsicDimensionWithMatchingOtherDimension(
            isWidth = true,
            intrinsicPrimary = intrinsicWidth,
            intrinsicSecondary = intrinsicHeight,
        )

        /**
         * Sets the height of the window to its intrinsic height at unconstrained width, and
         * the width of the window to its intrinsic width at that height.
         *
         * This is useful for cases where the window has a fixed height, but the width is flexible.
         *
         * @param intrinsicWidth The intrinsic width to measure.
         * @param intrinsicHeight The intrinsic height to measure.
         */
        fun IntrinsicHeightWithMatchingIntrinsicWidth(
            intrinsicHeight: WindowIntrinsicSize,
            intrinsicWidth: WindowIntrinsicSize,
        ) = IntrinsicDimensionWithMatchingOtherDimension(
            isWidth = false,
            intrinsicPrimary = intrinsicHeight,
            intrinsicSecondary = intrinsicWidth,
        )
    }
}


/**
 * The kinds of intrinsic sizes that can be used with [WindowSizeProvider].
 */
@ExperimentalComposeUiApi
abstract class WindowIntrinsicSize internal constructor() {

    /**
     * Returns the intrinsic width (min or max) of the given [measurable] at the given [height].
     */
    abstract fun widthOf(measurable: IntrinsicMeasurable, height: Int): Int

    /**
     * Returns the intrinsic height (min or max) of the given [measurable] at the given [width].
     */
    abstract fun heightOf(measurable: IntrinsicMeasurable, width: Int): Int

    /**
     * Measures minimum intrinsic size.
     */
    @ExperimentalComposeUiApi
    data object Min: WindowIntrinsicSize() {
        override fun widthOf(measurable: IntrinsicMeasurable, height: Int): Int {
            return measurable.minIntrinsicWidth(height)
        }

        override fun heightOf(measurable: IntrinsicMeasurable, width: Int): Int {
            return measurable.minIntrinsicHeight(width)
        }
    }

    /**
     * Measures maximum intrinsic size.
     */
    @ExperimentalComposeUiApi
    data object Max: WindowIntrinsicSize() {
        override fun widthOf(measurable: IntrinsicMeasurable, height: Int): Int {
            return measurable.maxIntrinsicWidth(height)
        }

        override fun heightOf(measurable: IntrinsicMeasurable, width: Int): Int {
            return measurable.maxIntrinsicHeight(width)
        }
    }
}