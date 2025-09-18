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

@file:Suppress("DEPRECATION")

package androidx.compose.ui.backhandler

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventState
import androidx.navigationevent.NavigationEventSwipeEdge
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@Deprecated("Use NavigationEventHandler instead")
@ExperimentalComposeUiApi
@Composable
actual fun PredictiveBackHandler(
    enabled: Boolean,
    onBack: suspend (progress: Flow<BackEventCompat>) -> Unit
) {
    val owner = LocalNavigationEventDispatcherOwner.current ?: return
    val dispatcher = owner.navigationEventDispatcher
    val coroutineScope = rememberCoroutineScope()

    var progressChannel: Channel<BackEventCompat>? by remember(onBack) {
        mutableStateOf(null)
    }

    fun getActiveProgressChannel(): Channel<BackEventCompat> {
        val currentProgressChannel = progressChannel
        if (currentProgressChannel == null) {
            val progress = Channel<BackEventCompat>()
            progressChannel = progress
            coroutineScope.launch {
                onBack(progress.consumeAsFlow())
            }
            return progress
        } else {
            return currentProgressChannel
        }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            dispatcher.state.filterIsInstance<NavigationEventState.InProgress<*>>().collect {
                val navEvent = it.latestEvent
                val swipeEdge = when (navEvent.swipeEdge) {
                    NavigationEventSwipeEdge.Left -> BackEventCompat.EDGE_LEFT
                    NavigationEventSwipeEdge.Right -> BackEventCompat.EDGE_RIGHT
                    else -> 0
                }
                val event = BackEventCompat(
                    navEvent.touchX, navEvent.touchY, navEvent.progress, swipeEdge
                )
                getActiveProgressChannel().send(event)
            }
        }
    }

    NavigationBackHandler(
        currentInfo = NavigationEventInfo.NotProvided,
        isBackEnabled = enabled,
        onBackCancelled = {
            getActiveProgressChannel().close(CancellationException("Cancelled"))
            progressChannel = null
        },
        onBackCompleted = {
            getActiveProgressChannel().close()
            progressChannel = null
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            progressChannel?.close(CancellationException("Disposed"))
            progressChannel = null
        }
    }
}

@Deprecated("Use NavigationEventHandler instead")
@ExperimentalComposeUiApi
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    NavigationBackHandler(
        currentInfo = NavigationEventInfo.NotProvided,
        isBackEnabled = enabled,
        onBackCancelled = {},
        onBackCompleted = onBack
    )
}
