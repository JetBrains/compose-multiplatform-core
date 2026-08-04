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

/**
 * Darwin targets have no Compose context-menu machinery; the default menu renders nothing and
 * hosts provide their own [TextContextMenu] via [LocalTextContextMenu].
 */
@ExperimentalFoundationApi
internal actual fun platformDefaultTextContextMenu(showDisabledItems: Boolean): TextContextMenu =
    NoOpTextContextMenu

@OptIn(ExperimentalFoundationApi::class)
private object NoOpTextContextMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit,
    ) {
        content()
    }
}
