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

package androidx.pdf.content

import android.graphics.drawable.Drawable
import androidx.annotation.RestrictTo

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
 * @param leadingIcon An optional drawable resource to be displayed as an icon at the start of the
 *   menu item.
 * @param onClick A lambda function to be invoked when this menu item is clicked. This is where the
 *   item's action is defined.
 * @param contentDescription An optional string for accessibility services, providing a description
 *   of the item for screen readers.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SelectionMenuComponent(
    key: Any,
    public val label: String,
    public val leadingIcon: Drawable? = null,
    public val onClick: () -> Unit,
    public val contentDescription: String? = null,
) : ContextMenuComponent(key)
