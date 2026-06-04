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

package androidx.compose.ui.platform

import androidx.compose.ui.awt.DebouncingEdtExecutor
import kotlin.coroutines.EmptyCoroutineContext
import org.jetbrains.skiko.MainUIDispatcher

internal class DesktopFrameRecomposer {
    private val composeInvalidationExecutor = DebouncingEdtExecutor()
    val frameRecomposer = FrameRecomposer(
        coroutineContext = EmptyCoroutineContext + MainUIDispatcher,
        invalidate = ::scheduleNextFrame,
    )

    fun scheduleNextFrame() {
        composeInvalidationExecutor.runOrScheduleDebounced {
            frameRecomposer.performFrame(System.nanoTime())
        }
    }
}
