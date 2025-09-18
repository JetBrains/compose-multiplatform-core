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

package androidx.compose.ui.keyboard

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIKeyboardTypeEmailAddress
import platform.UIKit.UITextContentTypeUsername

internal class ImeOptionsTest {
    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testTextInputSessionDoesNotRestartWithChangedInput() = runUIKitInstrumentedTest {
        val inputState = mutableStateOf("")
        var startInputCount = 0

        val testTextInputSession = object : PlatformTextInputSession {
                override suspend fun startInputMethod(
                    request: PlatformTextInputMethodRequest
                ): Nothing {
                    startInputCount++
                    suspendCancellableCoroutine<Nothing> { }
                }
            }

        setContent {
            var input by rememberSaveable { inputState }

            InterceptPlatformTextInput(
                interceptor = { request, _ ->
                    testTextInputSession.startInputMethod(request)
                },
                content = {
                    TextField(
                        modifier = Modifier.testTag("TextField"),
                        value = input,
                        onValueChange = { input = it },
                        keyboardOptions = KeyboardOptions(platformImeOptions = PlatformImeOptions {
                            keyboardType(UIKeyboardTypeEmailAddress)
                            textContentType(UITextContentTypeUsername)
                        })
                    )
                }
            )
        }

        findNodeWithTag("TextField").tap()

        waitForIdle()

        assertEquals(1, startInputCount)

        var input = ""
        for (i in 1..4) {
            input += "$i"
            inputState.value = input
            waitForIdle()
            assertEquals(1, startInputCount)
        }
    }
}