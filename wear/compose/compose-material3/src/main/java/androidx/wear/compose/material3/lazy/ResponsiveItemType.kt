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
package androidx.wear.compose.material3.lazy

import androidx.compose.runtime.Immutable

/**
 * Represents the semantic type of an item in a [ResponsiveTransformingLazyColumn].
 *
 * This type is used to automatically calculate the correct top and bottom padding for the list
 * according to the Wear OS Material3 Design guidelines.
 */
@Immutable
@JvmInline
public value class ResponsiveItemType internal constructor(internal val value: Int) {
    public companion object {
        /**
         * An item that does not trigger any specific responsive padding logic. The list will use
         * the minimum content padding.
         */
        public val Default: ResponsiveItemType = ResponsiveItemType(0)

        /** An item that is a standard [androidx.wear.compose.material3.Button]. */
        public val Button: ResponsiveItemType = ResponsiveItemType(1)

        /** An item that is a [androidx.wear.compose.material3.CompactButton]. */
        public val CompactButton: ResponsiveItemType = ResponsiveItemType(2)

        /** An item that is a [androidx.wear.compose.material3.ButtonGroup]. */
        public val ButtonGroup: ResponsiveItemType = ResponsiveItemType(3)

        /** An item that is a `Card`. */
        public val Card: ResponsiveItemType = ResponsiveItemType(4)

        /** An item that is a [androidx.wear.compose.material3.ListHeader]. */
        public val ListHeader: ResponsiveItemType = ResponsiveItemType(5)

        /**
         * An item that contains a [androidx.wear.compose.material3.Text] composable.
         *
         * Unlike Cards or Buttons which handle rounded corners gracefully, rectangular blocks of
         * text can look awkward when clipped by the screen edge. This type applies specific
         * responsive padding (13% top, 23% bottom) to ensure the text remains legible and visually
         * pleasing at the start and end of the list.
         */
        public val Text: ResponsiveItemType = ResponsiveItemType(6)

        /**
         * An item that is an [androidx.wear.compose.material3.IconButton] or
         * [androidx.wear.compose.material3.IconToggleButton].
         *
         * These circular buttons receive reduced responsive padding (13%) to avoid being clipped by
         * the screen edge while maintaining a balanced look.
         */
        public val IconButton: ResponsiveItemType = ResponsiveItemType(7)

        /**
         * An item that is a [androidx.wear.compose.material3.TextButton] or
         * [androidx.wear.compose.material3.TextToggleButton].
         *
         * Like [IconButton], these are circular components that require specific responsive padding
         * (13%).
         */
        public val TextButton: ResponsiveItemType = ResponsiveItemType(8)
    }

    override fun toString(): String =
        when (this) {
            Default -> "Default"
            Button -> "Button"
            CompactButton -> "CompactButton"
            ButtonGroup -> "ButtonGroup"
            Card -> "Card"
            ListHeader -> "ListHeader"
            Text -> "Text"
            IconButton -> "IconButton"
            TextButton -> "TextButton"
            else -> "Unknown"
        }
}
