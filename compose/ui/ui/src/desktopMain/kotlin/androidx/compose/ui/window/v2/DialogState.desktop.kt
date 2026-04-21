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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.toAwtRectangleRounded
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.requireReal
import androidx.compose.ui.window.v2.DialogState.Companion.Saver
import java.awt.Rectangle
import kotlinx.coroutines.channels.Channel


/**
 * Creates a [DialogState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialPosition The initial position of the dialog; default if `null`. All the
 * coordinates must be [Dp.isSpecified] and [Dp.isFinite], and the [DpOffset] object itself must be
 * [DpOffset.isSpecified].
 * @param initialSize The initial size of the dialog; default if `null`. All the
 * coordinates must be [Dp.isSpecified] and [Dp.isFinite], and the [DpSize] object itself must be
 * [DpOffset.isSpecified].
 */
@ExperimentalComposeUiApi
@Composable
fun rememberDialogStateWithBounds(
    initialPosition: DpOffset? = null,
    initialSize: DpSize? = null,
): DialogState = rememberSaveable(saver = DialogState.Saver) {
    DialogStateWithBounds(
        initialPosition = initialPosition,
        initialSize = initialSize,
    )
}

/**
 * Creates a [DialogState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialBoundsProvider Provides the initial bounds of the dialog.
 */
@ExperimentalComposeUiApi
@Composable
fun rememberDialogState(
    initialBoundsProvider: WindowBoundsProvider = WindowBoundsProvider.Default,
): DialogState = rememberSaveable(saver = DialogState.Saver) {
    DialogState(
        initialBoundsProvider = initialBoundsProvider,
    )
}


/**
 * Creates a [DialogState] with the specified initial values.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialSize The initial size of the dialog; default if `null`. All the
 * coordinates must be [Dp.isSpecified] and [Dp.isFinite], and the [DpSize] object itself must be
 * [DpOffset.isSpecified].
 * @param initialPosition The initial position of the dialog; default if `null`. All the
 * coordinates must be [Dp.isSpecified] and [Dp.isFinite], and the [DpOffset] object itself must be
 * [DpOffset.isSpecified].
 */
@ExperimentalComposeUiApi
fun DialogStateWithBounds(
    initialSize: DpSize? = null,
    initialPosition: DpOffset? = null,
): DialogState {
    val sizeProvider =
        initialSize?.let { WindowSizeProvider.Fixed(it) } ?: WindowSizeProvider.Default
    val positionProvider =
        initialPosition?.let { WindowPositionProvider.Absolute(it) } ?: WindowPositionProvider.Default
    return DialogState(
        initialBoundsProvider = WindowBoundsProvider(sizeProvider, positionProvider),
    )
}

/**
 * Creates a [DialogState] with the specified initial bounds provider.
 *
 * @param initialBoundsProvider Provides the initial bounds of the dialog.
 */
@ExperimentalComposeUiApi
fun DialogState(
    initialBoundsProvider: WindowBoundsProvider,
): DialogState = DialogState().apply {
    requestBounds(initialBoundsProvider)
}

/**
 * A state object that can be hoisted to control and observe dialog attributes
 * (size, position).
 */
@Stable
@ExperimentalComposeUiApi
class DialogState private constructor(
    isInitialized: Boolean,
    bounds: DpRect?,
) {
    /**
     * Creates a new [DialogState] that is not yet initialized.
     */
    constructor() : this(
        isInitialized = false,
        bounds = null
    )

    /**
     * Creates a new [DialogState] that is initialized with the specified values.
     */
    internal constructor(
        bounds: DpRect,
    ): this(
        isInitialized = true,
        bounds = bounds,
    )

    init {
        bounds?.requireReal()
    }

    /**
     * Whether the dialog associated with this state has become visible at least once.
     */
    var isInitialized: Boolean by mutableStateOf(isInitialized)
        internal set

    /**
     * The current bounds of the dialog; `null` if the dialog is not yet [isInitialized].
     */
    @Suppress("PropertyName")
    internal var _bounds: DpRect? by mutableStateOf(bounds)

    /**
     * The current bounds of the dialog; throws [IllegalStateException] if the dialog is not yet
     * [isInitialized].
     */
    val bounds: DpRect
        get() = _bounds ?: dialogNotInitializedError("bounds")

    internal val boundsRequests = Channel<WindowBoundsProvider>(Channel.CONFLATED)

    /**
     * Requests to set the bounds of the dialog via a [WindowBoundsProvider].
     *
     * Note that the actual bounds are set asynchronously and may be different from the requested
     * ones (e.g., if the window manager can't position as requested).
     *
     * @param boundsProvider Provides the bounds to apply to the window.
     */
    fun requestBounds(boundsProvider: WindowBoundsProvider) {
        boundsRequests.trySend(boundsProvider)
    }

    /**
     * Requests to set the bounds of the dialog via a function that returns a [DpRect].
     *
     * Note that the actual bounds are set asynchronously and may be different from the requested
     * ones (e.g., if the window manager can't position as requested).
     *
     * @param boundsProvider Returns the bounds to apply to the window.
     */
    fun requestBounds(boundsProvider: WindowGeometryProviderScope.() -> DpRect) {
        boundsRequests.trySend(WindowBoundsProvider(boundsProvider))
    }

    /**
     * Requests to set the bounds of the dialog.
     *
     * Note that the actual bounds are set asynchronously and may be different from the requested
     * ones (e.g., if the window manager can't position as requested).
     *
     * @param bounds The bounds to apply to the window. All the coordinates must be [Dp.isSpecified]
     * and [Dp.isFinite].
     */
    fun requestBounds(bounds: DpRect) {
        boundsRequests.trySend(
            WindowBoundsProvider.Absolute(bounds)
        )
    }

    /**
     * Requests to set the position of the dialog via a [WindowPositionProvider].
     *
     * Note that the actual position is set asynchronously and may be different from the requested
     * one (e.g., if the window manager can't position as requested).
     *
     * @param positionProvider Provides the position to apply to the dialog.
     */
    fun requestPosition(positionProvider: WindowPositionProvider) {
        boundsRequests.trySend(
            WindowBoundsProvider(
                positionProvider = positionProvider,
            )
        )
    }

    /**
     * Requests to set the position of the dialog.
     *
     * Note that the actual position is set asynchronously and may be different from the requested
     * one (e.g., if the window manager can't position as requested).
     *
     * @param position The position to apply to the dialog. The value must be [DpOffset.isSpecified]
     * and all the coordinates must be [Dp.isSpecified] and [Dp.isFinite].
     */
    fun requestPosition(position: DpOffset) {
        boundsRequests.trySend(
            WindowBoundsProvider(
                positionProvider = WindowPositionProvider.Absolute(position),
            )
        )
    }

    /**
     * Requests to set the size of the dialog via a [WindowSizeProvider].
     *
     * Note that the actual size is set asynchronously and may be different from the requested
     * one (e.g., if the window manager can't size as requested).
     *
     * @param sizeProvider Provides the size to apply to the dialog.
     */
    fun requestSize(sizeProvider: WindowSizeProvider) {
        boundsRequests.trySend(
            WindowBoundsProvider(
                sizeProvider = sizeProvider,
            )
        )
    }

    /**
     * Requests to set the size of the dialog.
     *
     * Note that the actual size is set asynchronously and may be different from the requested
     * one (e.g., if the window manager can't size as requested).
     *
     * @param size The position to apply to the dialog. The value must be [DpSize.isSpecified]
     * and all the coordinates must be [Dp.isSpecified] and [Dp.isFinite].
     */
    fun requestSize(size: DpSize) {
        boundsRequests.trySend(
            WindowBoundsProvider(
                sizeProvider = WindowSizeProvider.Fixed(size),
            )
        )
    }

    @ExperimentalComposeUiApi
    companion object {
        /**
         * A [Saver] implementation for [DialogState].
         */
        val Saver: Saver<DialogState, Any> = listSaver(
            save = {
                if (!it.isInitialized) return@listSaver emptyList()
                val bounds = it.bounds
                arrayListOf(
                    bounds.top.value,
                    bounds.left.value,
                    bounds.right.value,
                    bounds.bottom.value,
                )
            },
            restore = { state ->
                if (state.isEmpty()) return@listSaver null
                DialogState(
                    bounds = DpRect(
                        top = Dp(state[3] as Float),
                        left = Dp(state[4] as Float),
                        right = Dp(state[5] as Float),
                        bottom = Dp(state[6] as Float)
                    )
                )
            }
        )
    }
}

/**
 * Returns the bounds of the dialog, as an AWT [Rectangle]; throws [IllegalStateException] if the
 * window is not yet [isInitialized].
 */
@ExperimentalComposeUiApi
val DialogState.awtBounds: Rectangle
    get() = bounds.toAwtRectangleRounded()

private fun dialogNotInitializedError(propertyName: String): Nothing =
    throw IllegalStateException("Can't read $propertyName before the dialog has been made visible;" +
        " use isInitialized to check.")