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

package androidx.compose.ui.test

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InputSynchronizationTest {

    @Test
    fun draggableReceivesDeltaWithoutExplicitWaitForIdle() = runComposeUiTest {
        val deltas = mutableListOf<Float>()
        setContent {
            val state = rememberDraggableState { deltas.add(it) }
            Box(Modifier.fillMaxSize().draggable(state, Orientation.Horizontal))
        }

        onRoot().performTouchInput {
            down(Offset(50f, 50f))
            moveTo(Offset(150f, 50f))
        }

        assertTrue(deltas.isNotEmpty())
    }

    @Test
    fun scrollableReceivesDeltaWithoutExplicitWaitForIdle() = runComposeUiTest {
        val deltas = mutableListOf<Float>()
        setContent {
            val state = rememberScrollableState { delta ->
                deltas.add(delta)
                delta
            }
            Box(Modifier.fillMaxSize().scrollable(state, Orientation.Vertical))
        }

        onRoot().performTouchInput {
            down(Offset(50f, 150f))
            moveTo(Offset(50f, 50f))
        }

        assertTrue(deltas.isNotEmpty())
    }
}
