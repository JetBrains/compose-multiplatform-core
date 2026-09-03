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

package androidx.compose.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findAllUITextInputViews
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UITextContentType
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername
import platform.UIKit.UITextField
import platform.UIKit.UITextInputProtocol
import platform.UIKit.UITextInputTraitsProtocol
import platform.UIKit.UIView

internal class PasswordAutofillTest {
    companion object {
        private const val USERNAME = "user@example.com"
        private const val PASSWORD = "hunter2"
    }

    private data class Config(
        val useNativeTextInput: Boolean,
        val useBTF2: Boolean,
    ) {
        override fun toString(): String {
            val backend = if (useNativeTextInput) "NativeTextInput" else "ComposeTextInput"
            val generation = if (useBTF2) "BasicTextField2" else "BasicTextField"
            return "$backend/$generation"
        }
    }

    private val configurations = listOf(
        Config(useNativeTextInput = false, useBTF2 = false),
        Config(useNativeTextInput = false, useBTF2 = true),
        Config(useNativeTextInput = true, useBTF2 = false),
        Config(useNativeTextInput = true, useBTF2 = true),
    )

    @Test
    fun testBothCredentialInputViewsAreAttachedWhileOnlyOneIsFocused() =
        runUIKitInstrumentedTest(params = configurations) { config ->
            setCredentialsContent(config)

            val contentTypes = findAllUITextInputViews().map {
                (it as UITextInputTraitsProtocol).textContentType
            }

            assertEquals(
                setOf(UITextContentTypeUsername, UITextContentTypePassword),
                contentTypes.toSet(),
                "$config: expected a username and a password input view attached at the same time, " +
                    "got $contentTypes."
            )
            assertEquals(2, contentTypes.size, "$config: unexpected number of input views.")
        }

    @Test
    fun testUnfocusedPasswordInputViewExposesItsText() =
        runUIKitInstrumentedTest(params = configurations) { config ->
            setCredentialsContent(config)

            val passwordView = findUITextInputView(UITextContentTypePassword)

            assertFalse(
                passwordView.isFirstResponder,
                "$config: the password field was expected to stay unfocused."
            )
            assertEquals(
                PASSWORD,
                passwordView.textInputDocumentText(),
                "$config: the unfocused password input view didn't expose its text."
            )
        }

    @OptIn(BetaInteropApi::class)
    @Test
    fun testSecureInputViewMasqueradesAsUITextField() =
        runUIKitInstrumentedTest(params = configurations) { config ->
            setCredentialsContent(config)

            assertTrue(
                findUITextInputView(UITextContentTypePassword).isKindOfClass(UITextField),
                "$config: the secure input view must report itself as a UITextField, otherwise iOS " +
                    "never offers to save the password."
            )
            assertFalse(
                findUITextInputView(UITextContentTypeUsername).isKindOfClass(UITextField),
                "$config: only secure input views should masquerade as a UITextField."
            )
        }

    @OptIn(BetaInteropApi::class)
    @Test
    fun testSecureInputViewTextSelectorReturnsWholeDocument() =
        runUIKitInstrumentedTest(params = configurations) { config ->
            setCredentialsContent(config)

            val text = findUITextInputView(UITextContentTypePassword)
                .performSelector(NSSelectorFromString("text"))

            assertEquals(
                PASSWORD,
                text,
                "$config: -text must return the whole document for a secure input view."
            )
        }

    @Test
    fun testInputViewsAreRemovedWhenTextFieldsLeaveComposition() =
        runUIKitInstrumentedTest(params = configurations) { config ->
            val visible = mutableStateOf(true)

            setContent {
                if (visible.value) {
                    CredentialFields(config, focusRequester = null)
                }
            }
            waitForIdle()
            assertEquals(2, findAllUITextInputViews().size, "$config: input views weren't attached.")

            visible.value = false
            waitForIdle()

            waitUntil("$config: input views outlived the text fields that own them.") {
                findAllUITextInputViews().isEmpty()
            }
        }

    @OptIn(BetaInteropApi::class)
    @Test
    fun testDetachedCredentialViewsStillHoldTheirValues() =
        runUIKitInstrumentedTest(params = configurations) { config ->
            val visible = mutableStateOf(true)

            setContent {
                if (visible.value) {
                    CredentialFields(config, focusRequester = null)
                }
            }
            waitForIdle()

            val usernameView = findUITextInputView(UITextContentTypeUsername)
            val passwordView = findUITextInputView(UITextContentTypePassword)

            visible.value = false
            waitForIdle()
            delay(1000)

            assertEquals(
                USERNAME,
                usernameView.textInputDocumentText(),
                "$config: the detached username view lost its text."
            )
            assertEquals(
                PASSWORD,
                passwordView.textInputDocumentText(),
                "$config: the detached password view lost its text."
            )
            assertEquals(
                PASSWORD,
                passwordView.performSelector(NSSelectorFromString("text")),
                "$config: -text on the detached password view no longer returns the credential."
            )
        }

    private fun UIKitInstrumentedTest.setCredentialsContent(config: Config) {
        val focusRequester = FocusRequester()

        setContent {
            CredentialFields(config, focusRequester)

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }

        waitForIdle()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun CredentialFields(config: Config, focusRequester: FocusRequester?) {
        val usernameOptions = KeyboardOptions(
            platformImeOptions = PlatformImeOptions {
                textContentType(UITextContentTypeUsername)
                isSecureTextEntry(false)
                usingNativeTextInput(config.useNativeTextInput)
            }
        )
        val passwordOptions = KeyboardOptions(
            platformImeOptions = PlatformImeOptions {
                textContentType(UITextContentTypePassword)
                isSecureTextEntry(true)
                usingNativeTextInput(config.useNativeTextInput)
            }
        )
        val usernameModifier = focusRequester
            ?.let { Modifier.focusRequester(it) }
            ?: Modifier

        Column {
            if (config.useBTF2) {
                BasicTextField(
                    state = rememberTextFieldState(USERNAME),
                    modifier = usernameModifier,
                    keyboardOptions = usernameOptions,
                )
                BasicTextField(
                    state = rememberTextFieldState(PASSWORD),
                    keyboardOptions = passwordOptions,
                )
            } else {
                BasicTextField(
                    value = USERNAME,
                    onValueChange = {},
                    modifier = usernameModifier,
                    keyboardOptions = usernameOptions,
                )
                BasicTextField(
                    value = PASSWORD,
                    onValueChange = {},
                    keyboardOptions = passwordOptions,
                )
            }
        }
    }

    private fun UIKitInstrumentedTest.findUITextInputView(contentType: UITextContentType): UIView {
        val matching = findAllUITextInputViews().filter {
            (it as UITextInputTraitsProtocol).textContentType == contentType
        }
        assertEquals(
            1,
            matching.size,
            "Expected exactly one text input view with content type $contentType, " +
                "found ${matching.size}."
        )
        return matching.single()
    }

    private fun UIView.textInputDocumentText(): String? {
        val input = this as UITextInputProtocol
        val range = input.textRangeFromPosition(
            fromPosition = input.beginningOfDocument,
            toPosition = input.endOfDocument
        ) ?: return null
        return input.textInRange(range)
    }
}
