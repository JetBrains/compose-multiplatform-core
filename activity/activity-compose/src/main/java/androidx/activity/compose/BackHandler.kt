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

package androidx.activity.compose

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView

/**
 * Provides a [OnBackPressedDispatcher] that can be used by Composables hosted in a
 * [androidx.activity.ComponentActivity].
 */
public object LocalOnBackPressedDispatcherOwner {
    private val LocalOnBackPressedDispatcherOwner =
        compositionLocalOf<OnBackPressedDispatcherOwner?> { null }

    /**
     * Returns current composition local value for the owner or `null` if one has not been provided,
     * one has not been set via [androidx.activity.setViewTreeOnBackPressedDispatcherOwner], nor is
     * one available by looking at the [LocalContext].
     */
    public val current: OnBackPressedDispatcherOwner?
        @Composable
        get() =
            LocalOnBackPressedDispatcherOwner.current
                ?: LocalView.current.findViewTreeOnBackPressedDispatcherOwner()
                ?: findOwner<OnBackPressedDispatcherOwner>(LocalContext.current)

    /**
     * Associates a [LocalOnBackPressedDispatcherOwner] key to a value in a call to
     * [CompositionLocalProvider].
     */
    public infix fun provides(
        dispatcherOwner: OnBackPressedDispatcherOwner
    ): ProvidedValue<OnBackPressedDispatcherOwner?> {
        return LocalOnBackPressedDispatcherOwner.provides(dispatcherOwner)
    }
}

/**
 * An effect for handling presses of the system back button.
 *
 * This effect registers a callback to be invoked when the system back button is pressed.
 *
 * The [onBack] will be invoked when the system back button is pressed (i.e., `onCompleted`).
 *
 * ## Precedence
 * If multiple [BackHandler] are present in the composition, the one that is composed **last** among
 * all enabled handlers will be invoked.
 *
 * ## Usage
 * It is important to call this composable **unconditionally**. Use the `enabled` parameter to
 * control whether the handler is active. This is preferable to conditionally calling [BackHandler]
 * (e.g., inside an `if` block), as conditional calls can change the order of composition, leading
 * to unpredictable behavior where different handlers are invoked after recomposition.
 *
 * @sample androidx.activity.compose.samples.BackHandler
 * @param enabled If `true`, this handler will be enabled and eligible to handle the back press.
 * @param onBack The action to be invoked when the system back button is pressed.
 */
@SuppressWarnings("MissingJvmstatic")
@Composable
public fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    // Safely update the current `onBack` lambda when a new one is provided
    val currentOnBack by rememberUpdatedState(onBack)
    // Remember in Composition a back callback that calls the `onBack` lambda
    val backCallback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() {
                currentOnBack()
            }
        }
    }
    // On every successful composition, update the callback with the `enabled` value
    SideEffect { backCallback.isEnabled = enabled }
    val backDispatcher =
        checkNotNull(LocalOnBackPressedDispatcherOwner.current) {
                "No OnBackPressedDispatcherOwner was provided via LocalOnBackPressedDispatcherOwner"
            }
            .onBackPressedDispatcher
    @Suppress("deprecation", "KotlinRedundantDiagnosticSuppress") // TODO b/330570365
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, backDispatcher) {
        // Add callback to the backDispatcher
        backDispatcher.addCallback(lifecycleOwner, backCallback)
        // When the effect leaves the Composition, remove the callback
        onDispose { backCallback.remove() }
    }
}
