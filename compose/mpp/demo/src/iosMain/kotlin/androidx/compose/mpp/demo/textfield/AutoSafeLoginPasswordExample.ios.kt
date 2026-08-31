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

package androidx.compose.mpp.demo

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.uikit.LocalUIView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.time.Duration.Companion.seconds
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import kotlinx.coroutines.delay
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIKeyboardTypeEmailAddress
import platform.UIKit.UITextAutocapitalizationType
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextBorderStyle
import platform.UIKit.UITextContentTypePassword
import platform.UIKit.UITextContentTypeUsername
import platform.UIKit.UITextField
import platform.UIKit.UITextSpellCheckingType
import platform.UIKit.endEditing

val AutoSafeLoginPasswordExample = Screen.Selection(
    title = "Autosave Login & Password",
    screens = listOf(
        Screen.Fullscreen("UITextField safe password") { back -> UITextFieldSafePassword(back) },
        Screen.Fullscreen("Core Text Field safe password") { back -> ComposeCoreTextFieldSafePassword(back) },
        Screen.Fullscreen("Basic Text Field safe password") { back -> ComposeBasicTextFieldSafePassword(back) },
    )
)

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
@Composable
private fun UITextFieldSafePassword(back: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    if (isLoggingIn) {
        LaunchedEffect(Unit) {
            delay(1.seconds)
            back()
        }
    }

    Column(Modifier.padding(16.dp).safeDrawingPadding()) {
        Text("Login (UITextField)")
        Spacer(Modifier.height(4.dp))
        UIKitView(
            factory = {
                val textField = object : UITextField(CGRectZero.readValue()) {
                    @ObjCAction
                    fun editingChanged() {
                        login = text ?: ""
                    }
                }
                textField.placeholder = "Email"
                textField.borderStyle = UITextBorderStyle.UITextBorderStyleRoundedRect
                textField.autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
                textField.autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
                textField.spellCheckingType = UITextSpellCheckingType.UITextSpellCheckingTypeNo
                textField.keyboardType = UIKeyboardTypeEmailAddress
                textField.textContentType = UITextContentTypeUsername
                textField.addTarget(
                    target = textField,
                    action = NSSelectorFromString(textField::editingChanged.name),
                    forControlEvents = UIControlEventEditingChanged
                )
                textField
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            update = { it.text = login },
            properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true)
        )

        Spacer(Modifier.height(12.dp))
        Text("Password (UITextField)")
        Spacer(Modifier.height(4.dp))
        UIKitView(
            factory = {
                val textField = object : UITextField(CGRectZero.readValue()) {
                    @ObjCAction
                    fun editingChanged() {
                        password = text ?: ""
                    }
                }
                textField.placeholder = "Password"
                textField.borderStyle = UITextBorderStyle.UITextBorderStyleRoundedRect
                textField.autocapitalizationType = UITextAutocapitalizationType.UITextAutocapitalizationTypeNone
                textField.autocorrectionType = UITextAutocorrectionType.UITextAutocorrectionTypeNo
                textField.spellCheckingType = UITextSpellCheckingType.UITextSpellCheckingTypeNo
                textField.secureTextEntry = true
                textField.textContentType = UITextContentTypePassword
                textField.addTarget(
                    target = textField,
                    action = NSSelectorFromString(textField::editingChanged.name),
                    forControlEvents = UIControlEventEditingChanged
                )
                textField
            },
            modifier = Modifier.fillMaxWidth().height(40.dp),
            update = { it.text = password },
            properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true)
        )

        val localView = LocalUIView.current
        Button({ localView.superview?.endEditing(true) }) {
            Text("End editing")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { isLoggingIn = true },
            enabled = login.isNotEmpty() && password.isNotEmpty() && !isLoggingIn
        ) {
            Text(if (isLoggingIn) "Logging in..." else "Login")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ComposeCoreTextFieldSafePassword(back: () -> Unit) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }

    if (isLoggingIn) {
        LaunchedEffect(Unit) {
            delay(1.seconds)
            back()
        }
    }

    Column(Modifier.padding(16.dp).safeDrawingPadding()) {
        val fieldModifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .padding(4.dp)

        Text("Login (Compose TextField)")
        Spacer(Modifier.height(4.dp))
        TextField(
            value = login,
            onValueChange = { login = it },
            modifier = fieldModifier,
            placeholder = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                platformImeOptions = PlatformImeOptions {
                    textContentType(UITextContentTypeUsername)
                }
            )
        )

        Spacer(Modifier.height(12.dp))
        Text("Password (Compose TextField)")
        Spacer(Modifier.height(4.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            modifier = fieldModifier,
            placeholder = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                platformImeOptions = PlatformImeOptions {
                    isSecureTextEntry(true)
                }
            )
        )

        val manager = LocalFocusManager.current
        Button({ manager.clearFocus() }) {
            Text("End editing")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { isLoggingIn = true },
            enabled = login.isNotEmpty() && password.isNotEmpty() && !isLoggingIn
        ) {
            Text(if (isLoggingIn) "Logging in..." else "Login")
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ComposeBasicTextFieldSafePassword(back: () -> Unit) {
    val login = rememberTextFieldState()
    val password = rememberTextFieldState()
    var isLoggingIn by remember { mutableStateOf(false) }

    if (isLoggingIn) {
        LaunchedEffect(Unit) {
            delay(1.seconds)
            back()
        }
    }

    Column(Modifier.padding(16.dp).safeDrawingPadding()) {
        val fieldModifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
            .padding(4.dp)

        Text("Login (Compose TextField)")
        Spacer(Modifier.height(4.dp))
        TextField(
            state = login,
            modifier = fieldModifier,
            placeholder = { Text("Email") },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                platformImeOptions = PlatformImeOptions {
                    textContentType(UITextContentTypeUsername)
                }
            )
        )

        Spacer(Modifier.height(12.dp))
        Text("Password (Compose TextField)")
        Spacer(Modifier.height(4.dp))
        TextField(
            state = password,
            modifier = fieldModifier,
            placeholder = { Text("Password") },
            lineLimits = TextFieldLineLimits.SingleLine,
            outputTransformation = remember {
                OutputTransformation { replace(0, length, "*".repeat(length)) }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                platformImeOptions = PlatformImeOptions {
                    isSecureTextEntry(true)
                }
            )
        )

        val manager = LocalFocusManager.current
        Button({ manager.clearFocus() }) {
            Text("End editing")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { isLoggingIn = true },
            enabled = login.text.isNotEmpty() && password.text.isNotEmpty() && !isLoggingIn
        ) {
            Text(if (isLoggingIn) "Logging in..." else "Login")
        }
    }
}
