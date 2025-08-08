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

package androidx.compose.ui.test

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.kruth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class SkikoComposeUiTestTest {

    @Test
    fun canSetContent() = runComposeUiTest {
        var set = false
        setContent { set = true }
        assertThat(set).isTrue()
    }

    @Test
    fun canAwaitIdle() = runComposeUiTest {
        var visible by mutableStateOf(false)

        setContent {
            if (visible) {
                Text("Hello")
            }
        }

        awaitIdle()
        onNodeWithText("Hello").assertDoesNotExist()

        visible = true
        awaitIdle()
        onNodeWithText("Hello").assertIsDisplayed()
    }

    @Test
    fun canDriveAnimationsFromTest() = runComposeUiTest(runTestContext = UnconfinedTestDispatcher()) {
        val scrollState = ScrollState(initial = 0)

        setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                Box(modifier = Modifier.height(2000.dp)) {}
            }
        }

        // A specific case with an animated scroll:
        coroutineScope {
            launch {
                // The effect needs its own coroutine so the test can continue running
                scrollState.animateScrollBy(100f)
            }
            awaitIdle()
        }
        assertThat(scrollState.value).isEqualTo(100)


        // A general case using withFrameNanos:
        val lastFrameTime = mainClock.currentTime
        var nextFrameNanos = 0L
        coroutineScope {
            launch {
                withFrameNanos {
                    nextFrameNanos = it
                }
            }
            awaitIdle()
        }
        assertThat(nextFrameNanos / 1_000_000L).isEqualTo(lastFrameTime + 16L)
    }
}