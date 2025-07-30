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

package androidx.pdf.selection

import android.graphics.drawable.Drawable
import androidx.annotation.RestrictTo

/** The `object`s used as keys for the default selection menu items. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object PdfSelectionMenuKeys {
    public object CopyKey

    public object SelectAllKey
}

/**
 * An abstract base class for components that can be displayed in a context menu.
 *
 * @param key A unique key that identifies this component within the context menu.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class ContextMenuComponent internal constructor(public val key: Any)

/**
 * A [SelectionMenuComponent] that represents a clickable item with a label in a selection menu.
 *
 * @param key A unique key that identifies this component
 * @param label The label text to be shown in the selection menu
 * @param onClick A lambda function to be invoked when this menu item is clicked. This is where the
 *   item's action is defined.
 * @param contentDescription An optional string for accessibility services, providing a description
 *   of the item for screen readers.
 * @param leadingIcon (internal param) An optional drawable resource to be displayed as an icon at
 *   the start of the menu item. This will be shown for [android.R.id.textAssist] menu item.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SelectionMenuComponent
internal constructor(
    key: Any,
    public val label: String,
    public val contentDescription: String? = null,
    internal val leadingIcon: Drawable? = null,
    public val onClick: SelectionMenuSession.() -> Unit,
) : ContextMenuComponent(key) {

    @JvmOverloads
    public constructor(
        key: Any,
        label: String,
        contentDescription: String? = null,
        onClick: SelectionMenuSession.() -> Unit,
    ) : this(
        key = key,
        label = label,
        contentDescription = contentDescription,
        onClick = onClick,
        leadingIcon = null,
    )
}

/** A session for an open selection menu that can be used to close the menu */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface SelectionMenuSession {
    /** Closes the text context menu */
    public fun close()
}
