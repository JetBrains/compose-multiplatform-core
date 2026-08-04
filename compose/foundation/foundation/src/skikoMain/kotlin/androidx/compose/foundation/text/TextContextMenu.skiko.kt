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

package androidx.compose.foundation.text

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString

/**
 * Composition local that keeps [TextContextMenu].
 */
@ExperimentalFoundationApi
val LocalTextContextMenu:
    ProvidableCompositionLocal<TextContextMenu> = staticCompositionLocalOf { TextContextMenu.Default }

/**
 * Describes how to show the text context menu for selectable texts and text fields.
 */
@ExperimentalFoundationApi
interface TextContextMenu {
    /**
     * Defines an area, that describes how to open and show text context menus.
     * Usually it uses [androidx.compose.foundation.ContextMenuArea] as the implementation.
     * Note that it's up to the [Area] implementation to trigger the opening of the context menu on
     * the appropriate user events (e.g. right-click).
     *
     * @param textManager Provides useful methods and information for text for which we show the
     * text context menu.
     * @param state [ContextMenuState] of menu controlled by this area.
     * @param content The content of the [androidx.compose.foundation.ContextMenuArea].
     */
    @Composable
    fun Area(textManager: TextManager, state: ContextMenuState, content: @Composable () -> Unit)

    /**
     * Provides useful methods and information for text for which we show the text context menu.
     */
    @ExperimentalFoundationApi
    interface TextManager {
        /**
         * The current selected text.
         */
        val selectedText: AnnotatedString

        /**
         * Action for cutting the selected text to the clipboard. Null if there is no text to cut.
         */
        val cut: Action?

        /**
         * Action for copy the selected text to the clipboard. Null if there is no text to copy.
         */
        val copy: Action?

        /**
         * Action for copying the url of the link the context menu was opened on. Null if
         * [selectLinkAtPositionIfAny] did not select a link for this menu.
         */
        val copyLinkUrl: Action?

        /**
         * Action for pasting text from the clipboard. Null if there is no text in the clipboard.
         */
        val paste: Action?

        /**
         * Action for selecting the whole text. Null if the text is already selected.
         */
        val selectAll: Action?

        /**
         * Selects the word at the given [offset], unless the current selection already encompasses
         * that position.
         */
        fun selectWordAtPositionIfNotAlreadySelected(offset: Offset)

        /**
         * If there is a link at the given [offset] that the current selection does not already
         * cover, selects the whole text of that link and offers its url through [copyLinkUrl].
         * Leaves a wider selection around [offset] alone. Returns whether a link was selected.
         */
        fun selectLinkAtPositionIfAny(offset: Offset): Boolean
    }

    @ExperimentalFoundationApi
    class Action(val enabled: Boolean, val execute: () -> Unit)

    companion object {
        /**
         * [TextContextMenu] that is used by default in Compose.
         */
        @ExperimentalFoundationApi
        val Default: TextContextMenu by lazy { platformDefaultTextContextMenu(showDisabledItems = true) }

        /**
         * [TextContextMenu] that doesn't show any disabled items.
         */
        @ExperimentalFoundationApi
        val HideDisabledMenuItems: TextContextMenu by lazy {
            platformDefaultTextContextMenu(showDisabledItems = false)
        }
    }
}

/**
 * The platform's default [TextContextMenu]. Desktop builds the basic menu over the KDT context-menu
 * machinery; platforms without that machinery (web, Darwin) render no menu, and hosts provide their
 * own via [LocalTextContextMenu].
 */
@ExperimentalFoundationApi
internal expect fun platformDefaultTextContextMenu(showDisabledItems: Boolean): TextContextMenu
