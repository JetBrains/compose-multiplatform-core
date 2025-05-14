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

package androidx.compose.ui.window.window

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowTestScope
import androidx.compose.ui.window.runApplicationTest
import com.google.common.truth.Truth.assertThat
import org.junit.experimental.theories.DataPoint

open class BaseWindowTextFieldTest {
    internal fun <S: TextFieldTestScope> runTextFieldTest(
        textFieldKind: TextFieldKind<S>,
        name: String,
        body: suspend S.() -> Unit
    ) = runApplicationTest(
        hasAnimations = true,
        animationsDelayMillis = 100
    ) {
        var scope: S? = null
        launchTestApplication {
            Window(onCloseRequest = ::exitApplication) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (scope == null) {
                        scope = textFieldKind.createScope(this@runApplicationTest, window)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$name ($scope)")
                        Box(Modifier.border(1.dp, Color.Black).padding(8.dp)) {
                            scope!!.TextField()
                        }
                    }
                }
            }
        }

        awaitIdle()
        scope!!.body()
    }

    internal abstract class TextFieldTestScope(
        private val windowTestScope: WindowTestScope,
        val window: ComposeWindow
    ) {
        abstract val text: String
        abstract val selection: TextRange
        abstract val composition: TextRange?

        @Composable
        abstract fun TextField()

        suspend fun awaitIdle() {
            windowTestScope.awaitIdle()
        }

        suspend fun assertStateEquals(
            actual: String,
            selection: TextRange,
            composition: TextRange?,
            awaitIdle: Boolean = true
        ) {
            if (awaitIdle) {
                windowTestScope.awaitIdle()
            }
            assertThat(text).isEqualTo(actual)
            assertThat(selection).isEqualTo(selection)
            assertThat(composition).isEqualTo(composition)
        }
    }

    internal abstract class TextField1Scope(
        windowTestScope: WindowTestScope,
        window: ComposeWindow
    ): TextFieldTestScope(windowTestScope, window) {

        protected var textFieldValue by mutableStateOf(TextFieldValue())

        override val text: String
            get() = textFieldValue.text

        override val selection: TextRange
            get() = textFieldValue.selection

        override val composition: TextRange?
            get() = textFieldValue.composition

        override fun toString() = "TextField1"
    }

    internal abstract class TextField2Scope(
        windowTestScope: WindowTestScope,
        window: ComposeWindow
    ): TextFieldTestScope(windowTestScope, window) {
        protected val textFieldState = TextFieldState()
        var inputTransformation: InputTransformation? by mutableStateOf(null)

        override val text: String
            get() = textFieldState.text.toString()

        override val selection: TextRange
            get() = textFieldState.selection

        override val composition: TextRange?
            get() = textFieldState.composition
    }

    internal abstract class SecureTextFieldScope(
        windowTestScope: WindowTestScope,
        window: ComposeWindow,
        textObfuscationMode: TextObfuscationMode,
    ): TextField2Scope(windowTestScope, window) {

        var textObfuscationMode by mutableStateOf(textObfuscationMode)

    }

    internal fun interface TextFieldKind<S: TextFieldTestScope> {
        fun createScope(windowTestScope: WindowTestScope, window: ComposeWindow): S
    }

    companion object {
        @JvmField
        @DataPoint
        internal val TextField1 = TextFieldKind<TextField1Scope> { windowTestScope, window ->
            object : TextField1Scope(windowTestScope, window) {
                @Composable
                override fun TextField() {
                    val focusRequester = FocusRequester()
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = {
                            textFieldValue = it
                        },
                        modifier = Modifier.focusRequester(focusRequester)
                    )

                    LaunchedEffect(focusRequester) {
                        focusRequester.requestFocus()
                    }
                }

                override fun toString() = "TextField1"
            }
        }

        @JvmField
        @DataPoint
        internal val TextField2 = TextFieldKind<TextField2Scope> { windowTestScope, window ->
            object : TextField2Scope(windowTestScope, window) {
                @Composable
                override fun TextField() {
                    val focusRequester = FocusRequester()
                    BasicTextField(
                        state = textFieldState,
                        inputTransformation = inputTransformation,
                        modifier = Modifier.focusRequester(focusRequester)
                    )

                    LaunchedEffect(focusRequester) {
                        focusRequester.requestFocus()
                    }
                }

                override fun toString() = "TextField2"
            }
        }

        @JvmField
        @DataPoint
        internal val SecureTextField = TextFieldKind<SecureTextFieldScope> { windowTestScope, window ->
            object : SecureTextFieldScope(windowTestScope, window, TextObfuscationMode.Hidden) {
                @Composable
                override fun TextField() {
                    val focusRequester = FocusRequester()

                    BasicSecureTextField(
                        state = textFieldState,
                        modifier = Modifier.focusRequester(focusRequester),
                        textObfuscationMode = textObfuscationMode
                    )

                    LaunchedEffect(focusRequester) {
                        focusRequester.requestFocus()
                    }
                }

                override fun toString() = "SecureTextField"
            }
        }
    }
}