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

package androidx.compose.foundation.text.contextmenu.data

import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.ui.util.fastJoinToString

/**
 * A list of components to be displayed in the context menu.
 *
 * @param components the list of components to be rendered in the context menu.
 */
// TODO(grantapher-cm-api-publicize) Make class public
internal class TextContextMenuData
internal constructor(val components: List<TextContextMenuComponent>) {
    override fun toString(): String {
        val componentsStr =
            components.fastJoinToString(prefix = "[\n\t", separator = "\n\t", postfix = "\n]")
        return "TextContextMenuData(components=$componentsStr)"
    }

    internal companion object {
        val Empty = TextContextMenuData(emptyList())
    }
}

/**
 * A single component of a text context menu.
 *
 * @param key A unique key that identifies this component, mainly for use in filtering a component
 *   in [Modifier.filterTextContextMenuComponents][filterTextContextMenuComponents]. It is advisable
 *   to use a `data object` as a key here.
 */
// TODO(grantapher-cm-api-publicize) Make abstract class public
internal abstract class TextContextMenuComponent internal constructor(val key: Any)

/** A [TextContextMenuComponent] separator in a text context menu. */
// TODO(grantapher-cm-api-publicize) Make object public
internal data object TextContextMenuSeparator : TextContextMenuComponent(TextContextMenuSeparator)

/** A session for an open text context menu that can be used to close the context menu. */
@Suppress("NotCloseable") // AutoCloseable not available in common.
// TODO(grantapher-cm-api-publicize) Make interface public
internal interface TextContextMenuSession {
    /** Closes the text context menu. */
    fun close()
}

/** Contains the `object`s used as keys for the compose provided context menu items. */
// TODO(grantapher-cm-api-publicize) Make object public
internal object TextContextMenuKeys {
    /** Key for the context menu "Cut" item. */
    data object CutKey

    /** Key for the context menu "Copy" item. */
    data object CopyKey

    /** Key for the context menu "Paste" item. */
    data object PasteKey

    /** Key for the context menu "Select All" item. */
    data object SelectAllKey

    /** Key for the context menu "Autofill" item. */
    data object AutofillKey
}
