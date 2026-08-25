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

package androidx.compose.ui.test.junit4.v2

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.DesktopComposeTestRule
import androidx.compose.ui.test.v2.ComposeTestConfig
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalTestApi::class, InternalTestApi::class)
actual fun createComposeRule(effectContext: CoroutineContext): ComposeContentTestRule =
    DesktopComposeTestRule(
        DesktopComposeUiTest(
            effectContext = effectContext,
            useStandardTestDispatcherForComposition = true
        )
    )

@OptIn(ExperimentalTestApi::class, InternalTestApi::class)
actual fun createComposeRule(config: ComposeTestConfig): ComposeContentTestRule =
    // TODO(Merge) Implement after merging 0221de5bb907a9b49017d40a2c8507bac7ad3a0b
    DesktopComposeTestRule(
        DesktopComposeUiTest(
            effectContext = config.effectContext,
            runTestContext = config.runTestContext,
            testTimeout = config.testTimeout,
            useStandardTestDispatcherForComposition = true,
        )
    )
