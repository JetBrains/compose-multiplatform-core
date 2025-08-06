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

package androidx.compose.ui.backhandler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import noria.NoriaContext

/**
 * Provides a [BackGestureDispatcher] that can be used by Composables to handle back events.
 */
@ExperimentalComposeUiApi
val LocalBackGestureDispatcher = staticCompositionLocalOf<BackGestureDispatcher?> { null }

@Composable
@ExperimentalComposeUiApi
actual fun NoriaContext.PredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (progress: Flow<BackEventCompat>) -> Unit
) {
    val backGestureDispatcher = LocalBackGestureDispatcher.current ?: return
    val onBackScope = rememberCoroutineScope()

    val currentOnBack by rememberUpdatedState(onBack)
    val currentDispatcher by rememberUpdatedState(backGestureDispatcher)

    val listener = remember {
        BackGestureListenerImpl(
            scope = onBackScope,
            onBack = { currentOnBack(it) },
            onReadyStateChanged = { currentDispatcher.activeListenerChanged() }
        )
    }

    LaunchedEffect(enabled) { listener.enabled = enabled }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            listener.active = (state == Lifecycle.State.STARTED || state == Lifecycle.State.RESUMED)
        }
    }

    DisposableEffect(backGestureDispatcher) {
        backGestureDispatcher.addListener(listener)
        onDispose { backGestureDispatcher.removeListener(listener) }
    }
}

@Composable
@ExperimentalComposeUiApi
actual fun NoriaContext.BackHandler(enabled: Boolean, onBack: () -> Unit) {
    PredictiveBackHandler(enabled) { progress ->
        try {
            progress.collect { /*ignore*/ }
            onBack()
        } catch (e: CancellationException) {
            //ignore
        }
    }
}
