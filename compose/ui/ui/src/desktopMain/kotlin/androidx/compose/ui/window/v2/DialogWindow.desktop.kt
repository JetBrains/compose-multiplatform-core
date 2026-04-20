/*
 * Copyright 2021 The Android Open Source Project
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
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.v2.SwingDialog
import androidx.compose.ui.awt.toAwtModalityType
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.DialogModalityType
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.WindowDecoration
import java.awt.Window

/**
 * Composes platform dialog in the current composition. When [DialogWindow] enters the composition,
 * a new platform dialog will be created and receive focus. When [DialogWindow] leaves the
 * composition, the dialog will be disposed and closed.
 *
 * Dialog is a modal window. It means it blocks the parent [Window] / [DialogWindow] in whose
 * composition context it was created.
 *
 * Usage:
 * ```
 * @Composable
 * fun main() = application {
 *     var isDialogOpen by remember { mutableStateOf(true) }
 *     if (isDialogOpen) {
 *         DialogWindow(onCloseRequest = { isDialogOpen = false }) {}
 *     }
 * }
 * ```
 * @param onCloseRequest Callback that will be called when the user closes the dialog.
 * Usually in this callback we need to manually tell Compose what to do:
 * - change `isOpen` state of the dialog (which is manually defined)
 * - close the whole application (`onCloseRequest = ::exitApplication` in [ApplicationScope])
 * - don't close the dialog on close request (`onCloseRequest = {}`)
 * @param state The state object to be used to control or observe the dialog's state
 * When size/position is changed by the user, state will be updated.
 * When size/position of the dialog is changed by the application (changing state),
 * the native dialog will update its corresponding properties.
 * If [DialogState.position] is not [WindowPosition.isSpecified], then after the first show on the
 * screen [DialogState.position] will be set to the absolute values.
 * @param visible Whether the dialog is visible to the user.
 * If `false`:
 * - internal state of [DialogWindow] is preserved and will be restored next time the dialog
 * will be visible;
 * - native resources will not be released. They will be released only when [DialogWindow]
 * will leave the composition.
 * @param title Title in the title bar of the dialog
 * @param icon Icon in the title bar of the window (for platforms that support this).
 * On macOs individual windows can't have a separate icon. To change the icon in the Dock,
 * set it via `iconFile` in build.gradle
 * (https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html#platform-specific-options)
 * @param decoration Specifies the decoration for this dialog.
 * @param transparent Disables or enables window transparency. Transparency may be set only if the
 * dialog is undecorated, otherwise an exception will be thrown.
 * @param resizable Whether the dialog can be resized by the user (application still can resize the
 * dialog by changing [state]).
 * @param enabled Whether the dialog reacts to input events.
 * @param focusable Whether the dialog can receive focus.
 * @param alwaysOnTop whether the dialog will always be on top of other windows and dialogs in the
 * application.
 * @param modalityType Modality type for the dialog.
 * @param onPreviewKeyEvent This callback is invoked when the user interacts with the hardware
 * keyboard. It gives ancestors of a focused component the chance to intercept a [KeyEvent].
 * Return true to stop propagation of this event. If you return false, the key event will be
 * sent to this [onPreviewKeyEvent]'s child. If none of the children consume the event,
 * it will be sent back up to the root using the [onKeyEvent] callback.
 * @param onKeyEvent This callback is invoked when the user interacts with the hardware
 * keyboard. While implementing this callback, return true to stop propagation of this event.
 * If you return false, the key event will be sent to this [onKeyEvent]'s parent.
 * @param content Composable content of the dialog.
 */
@ExperimentalComposeUiApi
@Composable
@ComposableOpenTarget(-1)
fun DialogWindow(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    visible: Boolean = true,
    title: String = "Untitled",
    icon: Painter? = null,
    decoration: WindowDecoration = WindowDecoration.SystemDefault,
    transparent: Boolean = false,
    resizable: Boolean = true,
    enabled: Boolean = true,
    focusable: Boolean = true,
    alwaysOnTop: Boolean = false,
    modalityType: DialogModalityType = DialogModalityType.DocumentModal,
    onPreviewKeyEvent: ((KeyEvent) -> Boolean) = { false },
    onKeyEvent: ((KeyEvent) -> Boolean) = { false },
    content: @Composable DialogWindowScope.() -> Unit
) {
    SwingDialog(
        onCloseRequest = onCloseRequest,
        state = state,
        visible = visible,
        title = title,
        icon = icon,
        decoration = decoration,
        transparent = transparent,
        resizable = resizable,
        enabled = enabled,
        focusable = focusable,
        alwaysOnTop = alwaysOnTop,
        modalityType = modalityType.toAwtModalityType(),
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
        init = { },
        content = content,
    )
}
