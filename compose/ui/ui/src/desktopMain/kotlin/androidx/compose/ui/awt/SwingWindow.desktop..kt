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

package androidx.compose.ui.awt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.scene.LocalComposeSceneContext
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.componentOrientation

/**
 * Compose [ComposeWindow] obtained from [create]. The [create] block will be called
 * exactly once to obtain the [ComposeWindow] to be composed, and it is also guaranteed to
 * be invoked on the UI thread (Event Dispatch Thread).
 *
 * Once [SwingWindow] leaves the composition, [dispose] will be called to free resources that
 * obtained by the [ComposeWindow].
 *
 * The [update] block can be run multiple times (on the UI thread as well) due to recomposition,
 * and it is the right place to set [ComposeWindow] properties depending on state.
 * When state changes, the block will be re-executed to set the new properties.
 * Note the block will also be run once right after the [create] block completes.
 *
 * [SwingWindow] is needed for creating windows that can't be created with the default Compose
 * function [androidx.compose.ui.window.Window]
 *
 * @param visible Whether the window is visible to the user.
 * If `false`:
 * - internal state of [ComposeWindow] is preserved and will be restored next time the window
 * will be visible;
 * - native resources will not be released. They will be released only when [SwingWindow] leaves
 * the composition.
 * @param onPreviewKeyEvent This callback is invoked when the user interacts with the hardware
 * keyboard. It gives ancestors of a focused component the chance to intercept a [KeyEvent].
 * Return true to stop propagation of this event. If you return false, the key event will be
 * sent to this [onPreviewKeyEvent]'s child. If none of the children consume the event,
 * it will be sent back up to the root using the onKeyEvent callback.
 * @param onKeyEvent This callback is invoked when the user interacts with the hardware
 * keyboard. While implementing this callback, return true to stop propagation of this event.
 * If you return false, the key event will be sent to this [onKeyEvent]'s parent.
 * @param create The block creating the [ComposeWindow] to be composed.
 * @param dispose The block to dispose [ComposeWindow] and free native resources.
 * Usually it is simple `ComposeWindow::dispose`
 * @param update The callback to be invoked to update window properties.
 * @param content Composable content of the window.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SwingWindow(
    visible: Boolean = true,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    create: () -> ComposeWindow,
    dispose: (ComposeWindow) -> Unit,
    update: (ComposeWindow) -> Unit = {},
    content: @Composable FrameWindowScope.() -> Unit
) {
    val compositionLocalContext by rememberUpdatedState(currentCompositionLocalContext)
    val windowExceptionHandlerFactory by rememberUpdatedState(
        LocalWindowExceptionHandlerFactory.current
    )
    val parentPlatformContext = LocalComposeSceneContext.current?.platformContext
    val layoutDirection = LocalLayoutDirection.current
    AwtWindow(
        visible = visible,
        create = {
            create().apply {
                this.rootForTestListener = parentPlatformContext?.rootForTestListener
                this.compositionLocalContext = compositionLocalContext
                this.exceptionHandler = windowExceptionHandlerFactory.exceptionHandler(this)
                setContent(onPreviewKeyEvent, onKeyEvent, content)
            }
        },
        dispose = {
            dispose(it)
        },
        update = {
            it.compositionLocalContext = compositionLocalContext
            it.exceptionHandler = windowExceptionHandlerFactory.exceptionHandler(it)
            it.componentOrientation = layoutDirection.componentOrientation

            val wasDisplayable = it.isDisplayable

            update(it)

            // If displaying for the first time, make sure we draw the first frame before making
            // the window visible, to avoid showing the window background
            // It's the responsibility of setSizeSafely to
            // - Make the window displayable
            // - Size the window and the ComposeLayer correctly, so that we can draw it here
            if (!wasDisplayable && it.isDisplayable) {
                it.contentPane.paint(it.contentPane.graphics)
            }
        },
    )
}
