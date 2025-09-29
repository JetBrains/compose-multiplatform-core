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

package androidx.navigationevent.compose

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState

/**
 * A simple [androidx.navigationevent.NavigationEventHandler] that delegates its methods to lambda
 * functions.
 */
internal class ComposeNavigationEventHandler<T : NavigationEventInfo>(
    initialInfo: T,
    private val onTransitionStateChanged: (NavigationEventTransitionState) -> Unit = {},
) :
    NavigationEventHandler<T>(
        initialInfo = initialInfo,
        isBackEnabled = false,
        isForwardEnabled = false,
    ) {

    var currentOnForwardCancelled: () -> Unit = {}
    var currentOnForwardCompleted: () -> Unit = {}
    var currentOnBackCancelled: () -> Unit = {}
    var currentOnBackCompleted: () -> Unit = {}

    override fun onForwardStarted(event: NavigationEvent) {
        onTransitionStateChanged(transitionState)
    }

    override fun onForwardProgressed(event: NavigationEvent) {
        onTransitionStateChanged(transitionState)
    }

    override fun onForwardCancelled() {
        onTransitionStateChanged(transitionState)
        currentOnForwardCancelled.invoke()
    }

    override fun onForwardCompleted() {
        onTransitionStateChanged(transitionState)
        currentOnForwardCompleted.invoke()
    }

    override fun onBackStarted(event: NavigationEvent) {
        onTransitionStateChanged(transitionState)
    }

    override fun onBackProgressed(event: NavigationEvent) {
        onTransitionStateChanged(transitionState)
    }

    override fun onBackCancelled() {
        onTransitionStateChanged(transitionState)
        currentOnBackCancelled.invoke()
    }

    override fun onBackCompleted() {
        onTransitionStateChanged(transitionState)
        currentOnBackCompleted.invoke()
    }
}
