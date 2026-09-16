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

import androidx.compose.ui.test.ComposeUiTestConfig
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.DesktopComposeTestRule
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalTestApi::class, InternalTestApi::class)
actual fun createComposeRule(effectContext: CoroutineContext): ComposeContentTestRule =
    DesktopComposeTestRule(
        DesktopComposeUiTest(
            effectContext = effectContext,
            useStandardTestDispatcherForComposition = true
        )
    )

@OptIn(ExperimentalTestApi::class, InternalTestApi::class)
actual fun createComposeRule(config: ComposeUiTestConfig): ComposeContentTestRule {
    config.checkSupported()
    return DesktopComposeTestRule(
        DesktopComposeUiTest(
            effectContext = config.effectContext,
            runTestContext = config.runTestContext,
            testTimeout = config.testTimeout,
            useStandardTestDispatcherForComposition = true,
        )
    )
}

actual fun createComposeRule(): ComposeContentTestRule =
    createComposeRule(effectContext = EmptyCoroutineContext)

private val defaultComposeUiTestConfig = ComposeUiTestConfig()

private fun ComposeUiTestConfig.checkFieldIsNotSet(
    name: String,
    getFieldValue: ComposeUiTestConfig.() -> Any
) {
    if (getFieldValue() != defaultComposeUiTestConfig.getFieldValue()) {
        println("ComposeUiTestConfig: setting $name is not supported in Compose Multiplatform")
    }
}

private fun ComposeUiTestConfig.checkSupported() {
    // TODO https://youtrack.jetbrains.com/issue/CMP-10712/Support-ComposeUiTestConfiginputMode
    checkFieldIsNotSet("inputMode", ComposeUiTestConfig::inputMode)
    // TODO https://youtrack.jetbrains.com/issue/CMP-10711/Support-ComposeUiTestConfigfailurePolicy
    checkFieldIsNotSet("failurePolicy", ComposeUiTestConfig::failurePolicy)
}