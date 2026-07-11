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

package androidx.compose.ui.window

import androidx.compose.ui.events.EventTargetListener
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.browser.window
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.Element
import org.w3c.dom.MediaQueryListEvent

internal interface ComposeWindowState {
    fun init() {}
    fun sizeFlow(): Flow<IntSize>

    val globalEvents: EventTargetListener

    fun dispose() {
        globalEvents.dispose()
    }
}

internal class DefaultWindowState(private val viewportContainer: Element) : ComposeWindowState {
    private val channel = Channel<IntSize>(Channel.CONFLATED)

    override val globalEvents = EventTargetListener(window)

    override fun init() {

        globalEvents.addDisposableEvent("resize") {
            channel.trySend(getParentContainerBox())
        }

        channel.trySend(getParentContainerBox())
    }

    private fun getParentContainerBox(): IntSize {
        return IntSize(viewportContainer.clientWidth, viewportContainer.clientHeight)
    }

    override fun sizeFlow() = channel.receiveAsFlow()
}