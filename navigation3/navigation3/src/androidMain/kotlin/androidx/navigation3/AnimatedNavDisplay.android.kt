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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog

/** Object that indicates the features that can be handled by the [AnimatedNavDisplay] */
public object AnimatedNavDisplay {
    /**
     * Function to be called on the [NavRecord.featureMap] to notify the [AnimatedNavDisplay] that
     * the content should be animated using the provided transitions.
     */
    public fun transition(enter: EnterTransition?, exit: ExitTransition?): Map<String, Any> =
        if (enter == null || exit == null) emptyMap()
        else mapOf(ENTER_TRANSITION_KEY to enter, EXIT_TRANSITION_KEY to exit)

    /**
     * Function to be called on the [NavRecord.featureMap] to notify the [NavDisplay] that the
     * content should be displayed inside of a [Dialog]
     */
    public fun isDialog(boolean: Boolean): Map<String, Any> =
        if (!boolean) emptyMap() else mapOf(DIALOG_KEY to true)

    internal const val ENTER_TRANSITION_KEY = "enterTransition"
    internal const val EXIT_TRANSITION_KEY = "exitTransition"
    internal const val DIALOG_KEY = "dialog"
}

/**
 * Display for Composable content that displays a single pane of content at a time, but can move
 * that content in and out with customized transitions.
 *
 * The AnimatedNavDisplay displays the content associated with the last key on the back stack in
 * most circumstances. If that content wants to be displayed as a dialog, as communicated by adding
 * [NavDisplay.isDialog] to a [NavRecord.featureMap], then the last key's content is a dialog and
 * the second to last key is a displayed in the background.
 *
 * @param backstack the collection of keys that represents the state that needs to be handled
 * @param wrapperManager the manager that combines all of the [NavContentWrapper]s
 * @param modifier the modifier to be applied to the layout.
 * @param contentAlignment The [Alignment] of the [AnimatedContent]
 * @param onBack a callback for handling system back presses
 * @param recordProvider lambda used to construct each possible [NavRecord]
 * @sample androidx.navigation3.samples.AnimatedNav
 */
@Composable
public fun <T : Any> AnimatedNavDisplay(
    backstack: List<T>,
    wrapperManager: NavWrapperManager,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    sizeTransform: SizeTransform? = null,
    onBack: () -> Unit = {},
    recordProvider: (key: T) -> NavRecord<out T>
) {
    BackHandler(backstack.size > 1, onBack)
    wrapperManager.PrepareBackStack(backStack = backstack)
    val key = backstack.last()
    val record = recordProvider.invoke(key)

    // Incoming record defines transitions, otherwise it defaults to a fade
    val enterTransition =
        record.featureMap[AnimatedNavDisplay.ENTER_TRANSITION_KEY] as? EnterTransition
            ?: fadeIn(animationSpec = tween(700))
    val exitTransition =
        record.featureMap[AnimatedNavDisplay.EXIT_TRANSITION_KEY] as? ExitTransition
            ?: fadeOut(animationSpec = tween(700))

    // if there is a dialog, we should create a transition with the next to last entry instead.
    val transition =
        if (record.featureMap[AnimatedNavDisplay.DIALOG_KEY] == true) {
            if (backstack.size > 1) {
                val previousKey = backstack[backstack.size - 2]
                updateTransition(targetState = previousKey, label = previousKey.toString())
            } else {
                null
            }
        } else {
            updateTransition(targetState = key, label = key.toString())
        }

    transition?.AnimatedContent(
        modifier = modifier,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = enterTransition,
                initialContentExit = exitTransition,
                sizeTransform = sizeTransform
            )
        },
        contentAlignment = contentAlignment
    ) { innerKey ->
        wrapperManager.ContentForRecord(recordProvider.invoke(innerKey))
    }

    if (record.featureMap[AnimatedNavDisplay.DIALOG_KEY] == true) {
        Dialog(onBack) { wrapperManager.ContentForRecord(record) }
    }
}
