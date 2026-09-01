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

package androidx.compose.ui.integrations

import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.center
import kotlin.test.Test
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake

class ComposeSceneMediatorTest {
    @Test
    fun testDisposedViewControllerTapNoCrash() = runUIKitInstrumentedTest {
        setContent {}

        stopComposeScene()

        tap(screenSize.center)

        waitForIdle()
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testDisposedViewControllerResizeNoCrash() = runUIKitInstrumentedTest {
        setContent {}

        stopComposeScene()

        viewController.view.setFrame(CGRectMake(0.0, 0.0, 100.0, 100.0))
        viewController.view.layoutIfNeeded()

        waitForIdle()
    }
}
