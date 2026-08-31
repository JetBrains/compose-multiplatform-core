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

package androidx.compose.ui.text.input

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.NativeTextEditingDelegate
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.NativeTextInputContextMenuCustomAction
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.detachedCopy
import androidx.compose.ui.scene.ComposeSceneFocusManager
import androidx.compose.ui.uikit.density
import androidx.compose.ui.uikit.utils.CMPEditMenuCustomAction
import androidx.compose.ui.unit.toCGRect
import androidx.compose.ui.unit.toDpOffset
import androidx.compose.ui.unit.toDpRect
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.window.ComposeTextInputView
import androidx.compose.ui.window.FocusedViewsList
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth

internal open class ComposeTextInputConnection(
    private var inactiveTextEditingDelegate: NativeTextEditingDelegate,
    updateView: () -> Unit,
    coroutineScope: CoroutineScope,
    viewConfiguration: ViewConfiguration,
    focusedViewsList: FocusedViewsList?,
    focusManager: () -> ComposeSceneFocusManager?
) : TextInputConnection(
    updateView = updateView,
    coroutineScope = coroutineScope,
    focusedViewsList = focusedViewsList,
    focusManager = focusManager
) {
    // Fixes a problem where the menu is shown before the textInputView gets its final layout.
    private var showMenuOrUpdatePosition = {}

    override val isInteractive: Boolean = true

    override val textInputView =
        ComposeTextInputView(
            doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
            initialInput = inactiveTextEditingDelegate,
        ).also {
            it.setAutoresizingMask(
                UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            )
        }

    override val rootView: UIView get() = textInputView

    override fun start(request: PlatformTextInputMethodRequest) {
        textInputView.input = this

        super.start(request)

        onViewGeometryUpdated()
    }

    override fun stop() {
        super.stop()

        textInputView.input = inactiveTextEditingDelegate
        textInputView.updateAvailableSystemActions(null, null, null, null, null)
        showMenuOrUpdatePosition = {}
    }

    override fun dispose() {
        super.dispose()

        // Keep answering for the text that was here, without holding the text field alive.
        inactiveTextEditingDelegate = inactiveTextEditingDelegate.detachedCopy()
        textInputView.input = inactiveTextEditingDelegate
    }

    override fun stateWillChange(textChanged: Boolean, selectionChanged: Boolean) {
        if (textChanged) {
            textInputView.textWillChange()
        }
        if (selectionChanged) {
            textInputView.selectionWillChange()
        }
    }

    override fun stateDidChange(textChanged: Boolean, selectionChanged: Boolean) {
        if (textChanged) {
            textInputView.textDidChange()
        }
        if (selectionChanged) {
            textInputView.selectionDidChange()
        }
    }

    override fun onViewGeometryUpdated() {
        val rect = textFieldRectInRoot ?: return
        val density = textInputView.window?.density ?: return
        textInputView.setFrame(rect.toDpRect(density).toCGRect())
        showMenuOrUpdatePosition()
    }

    override fun setAvailableEditMenuActions(
        copy: (() -> Unit)?,
        paste: (() -> Unit)?,
        cut: (() -> Unit)?,
        selectAll: (() -> Unit)?,
        customActions: List<NativeTextInputContextMenuCustomAction>?
    ) {
        textInputView.updateAvailableSystemActions(
            copyBlock = copy,
            cut = cut,
            paste = paste,
            select = null,
            selectAll = selectAll
        )
    }

    val toolbarStatus: TextToolbarStatus
        get() = if (textInputView.isTextMenuShown()) {
            TextToolbarStatus.Shown
        } else {
            TextToolbarStatus.Hidden
        }

    fun showToolbarMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        customActions: List<NativeTextInputContextMenuCustomAction>?,
    ) {
        showMenuOrUpdatePosition = {
            syncTextFieldValueFromRequestSnapshot()
            val density = rootView.density
            val offset = textInputView.frame.useContents { origin.toDpOffset().toOffset(density) }
            val target = rect.translate(-offset).toDpRect(density).toCGRect()
            textInputView.showEditMenuAtRect(
                targetRect = target,
                copy = onCopyRequested,
                cut = onCutRequested,
                paste = onPasteRequested,
                select = null,
                selectAll = onSelectAllRequested,
                customActions = customActions?.map { CMPEditMenuCustomAction(it.title, it.action) },
            )
            textMenuAppearanceChanged()
        }
        showMenuOrUpdatePosition()
    }

    fun hideToolbar() {
        showMenuOrUpdatePosition = {}
        textInputView.hideTextMenu()
        textMenuAppearanceChanged()
    }
}
