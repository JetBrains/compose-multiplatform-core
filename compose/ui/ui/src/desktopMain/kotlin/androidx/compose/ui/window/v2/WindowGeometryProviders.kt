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
import androidx.compose.ui.scene.MeasuredSceneContent
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
import androidx.compose.ui.window.roundToDimension
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
    /**
     * The screen on which the window will be placed.
     */
    val screen: Screen,

    /**
     * Returns the insets of the window.
     */
    windowInsets: () -> DpInsets,

    /**
     * Allows measuring the window's composable content.
     */
    private val measuringContentWithConstraints: (Constraints, Density.(MeasuredSceneContent) -> Any) -> Any,
) {
    /**
     * The insets of the window.
     */
    val windowInsets: DpInsets by lazy(windowInsets)

    /**
     * Returns the size a window should have given the content size.
     *
     * The content size is expanded by [windowInsets] and then constrained to
     * [Screen.availableBounds].
     */
    fun contentToWindowSize(contentSize: DpSize): DpSize =
        (contentSize + windowInsets).coerceAtMost(screen.availableBounds.size)

    internal fun <T> measuringContentWithConstraints(
        constraints: Constraints,
        block: Density.(MeasuredSceneContent) -> T): T {
        @Suppress("UNCHECKED_CAST")
        return measuringContentWithConstraints.invoke(
            constraints,
            block as Density.(MeasuredSceneContent) -> Any
        ) as T
    }


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
     * geometry of the screen and determine the appropriate position for the window.
     *
     * All coordinates in the returned [DpSize] must be [Dp.isSpecified] and [Dp.isFinite].
     * The [DpSize] itself must also be [DpSize.isSpecified].
     */
    fun WindowGeometryProviderScope.getSize(): DpSize

    @ExperimentalComposeUiApi
    companion object {
        /**
         * Returns the default size for a new window.
         */
        val Default = Exact(DpSize(800.dp, 600.dp))

        /**
         * Sets the size of the window to the given [size].
         *
         * @param size The size of the window.
         */
        fun Exact(size: DpSize): WindowSizeProvider {
            size.requireReal()
            return WindowSizeProvider { size }
        }

        /**
         * Sets the size of the window to the given [width] and [height].
         *
         * @param width The width of the window.
         * @param height The height of the window.
         */
        fun Exact(width: Dp, height: Dp) = Exact(DpSize(width, height))

        /**
         * Sets the size of the window to its preferred size, constrained only by the size of the
         * screen.
         *
         * The preferred size is computed by measuring the content with infinite
         * [Constraints], and adding the window's insets to that.
         */
        val Unconstrained = WindowSizeProvider {
            measuringContentWithConstraints(Constraints()) {
                contentToWindowSize(
                    DpSize(
                        width = it.measuredWidth.toDp(),
                        height = it.measuredHeight.toDp()
                    )
                )
            }
        }

        /**
         * Sets the width of the window to its minimum intrinsic width at the given [height].
         *
         * The height of the window is set to [height].
         *
         * @see [IntrinsicMeasurable.minIntrinsicWidth]
         */
        fun MinIntrinsicWidth(height: Dp): WindowSizeProvider {
            height.requireReal("height")
            return WindowSizeProvider {
                measuringContentWithConstraints(Constraints()) {
                    contentToWindowSize(
                        DpSize(
                            width = it.minIntrinsicWidth(height.roundToPx()).toDp(),
                            height = height
                        )
                    )
                }
            }
        }

        /**
         * Sets the height of the window to its minimum intrinsic height at the given [width].
         *
         * The width of the window is set to [width].
         *
         * @see [IntrinsicMeasurable.minIntrinsicHeight]
         */
        fun MinIntrinsicHeight(width: Dp): WindowSizeProvider {
            width.requireReal("width")
            return WindowSizeProvider {
                measuringContentWithConstraints(Constraints()) {
                    contentToWindowSize(
                        DpSize(
                            width = width,
                            height = it.minIntrinsicHeight(width.roundToPx()).toDp(),
                        )
                    )
                }
            }
        }

        /**
         * Sets the width of the window to its minimum intrinsic width at unconstrained height, and
         * the height of the window to its minimum intrinsic height at that width.
         *
         * This is useful for cases where the window has a fixed minimum width, but the height is
         * flexible.
         */
        val MinWidthMatchingHeight = WindowSizeProvider {
            measuringContentWithConstraints(Constraints()) {
                val width = it.minIntrinsicWidth(screen.availableBounds.height.roundToPx())
                val height = it.minIntrinsicHeight(width)
                contentToWindowSize(
                    DpSize(width.toDp(), height.toDp())
                )
            }
        }

        /**
         * Sets the height of the window to its minimum intrinsic height at unconstrained width, and
         * the width of the window to its minimum intrinsic width at that height.
         *
         * This is useful for cases where the window has a fixed minimum height, but the width is
         * flexible.
         */
        val MinHeightMatchingWidth = WindowSizeProvider {
            measuringContentWithConstraints(Constraints()) {
                val height = it.minIntrinsicHeight(screen.availableBounds.width.roundToPx())
                val width = it.minIntrinsicWidth(height)
                contentToWindowSize(
                    DpSize(width.toDp(), height.toDp())
                )
            }
        }
    }
}
