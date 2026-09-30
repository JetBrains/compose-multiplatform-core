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

package androidx.compose.ui.node

import androidx.compose.ui.platform.LocalOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.runApplicationTest
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.WindowState
import kotlin.test.Test
import kotlin.test.assertTrue

class OutOfFrameExecutorTest {
    @Test
    fun workIsExecuted() = runApplicationTest {
        var outOfFrameExecutor: OutOfFrameExecutor? = null
        launchTestWindowV2Application(
            WindowState(
                initialBoundsProvider = WindowBoundsProvider(
                    WindowSizeProvider.Fixed(200.dp, 200.dp)
                )
            )
        ) {
            outOfFrameExecutor = LocalOwner.current.outOfFrameExecutor
        }

        awaitIdle()
        if (outOfFrameExecutor == null) return@runApplicationTest

        var workExecuted = false
        outOfFrameExecutor!!.schedule {
            workExecuted = true
        }

        awaitIdle()

        assertTrue(workExecuted)
    }
}