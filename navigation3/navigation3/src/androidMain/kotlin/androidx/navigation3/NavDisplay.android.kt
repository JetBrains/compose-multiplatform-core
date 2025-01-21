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

package androidx.navigation3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog

/** Object that indicates the features that can be handled by the [NavDisplay] */
public object NavDisplay {
    /**
     * Function to be called on the [NavRecord.featureMap] to notify the [NavDisplay] that the
     * content should be displayed inside of a [Dialog]
     */
    public fun isDialog(boolean: Boolean): Map<String, Any> =
        if (!boolean) emptyMap() else mapOf(DIALOG_KEY to true)

    internal const val DIALOG_KEY = "dialog"
}

/**
 * Simple display for Composable content that displays a single pane of content at a time.
 *
 * The NavDisplay displays the content associated with the last key on the back stack in most
 * circumstances. If that content wants to be displayed as a dialog, as communicated by adding
 * [NavDisplay.isDialog] to a [NavRecord.featureMap], then the last key's content is a dialog and
 * the second to last key is a displayed in the background.
 *
 * @param modifier the modifier to be applied to the layout.
 * @param backstack the collection of keys that represents the state that needs to be handled
 * @param wrapperManager the manager that combines all of the [NavContentWrapper]s
 * @param onBack a callback for handling system back presses
 * @param recordProvider lambda used to construct each possible [NavRecord]
 * @sample androidx.navigation3.samples.BasicNav
 */
@Composable
public fun <T : Any> NavDisplay(
    backstack: List<T>,
    wrapperManager: NavWrapperManager,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    recordProvider: (key: T) -> NavRecord<out T>
) {
    BackHandler(backstack.size > 1, onBack)
    wrapperManager.PrepareBackStack(backStack = backstack)
    val key = backstack.last()
    val record = recordProvider.invoke(key)
    if (record.featureMap[NavDisplay.DIALOG_KEY] == true) {
        if (backstack.size > 1) {
            val previousKey = backstack[backstack.size - 2]
            val lastRecord = recordProvider.invoke(previousKey)
            Box(modifier = modifier) { wrapperManager.ContentForRecord(lastRecord) }
        }
        Dialog(onBack) { wrapperManager.ContentForRecord(record) }
    } else {
        Box(modifier = modifier) { wrapperManager.ContentForRecord(record) }
    }
}
