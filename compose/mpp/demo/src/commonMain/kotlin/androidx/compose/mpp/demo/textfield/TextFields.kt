/*
 * Copyright 2023 The Android Open Source Project
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

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package androidx.compose.mpp.demo.textfield

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.internal.ChangeTracker
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.mpp.demo.Screen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TextFields = Screen.Selection(
    "TextFields",
    Screen.Example("AlmostFullscreen") {
        ClearFocusBox {
            AlmostFullscreen()
        }
    },
    Screen.Example("AlmostFullscreen2") {
        ClearFocusBox {
            AlmostFullscreen2()
        }
    },
    Screen.Example("Keyboard Actions") {
        ClearFocusBox {
            KeyboardActionsExample()
        }
    },
    Screen.Example("Password Textfield Example") {
        ClearFocusBox {
            PasswordTextfieldExample()
        }
    },
    Screen.Example("Emoji") {
        ClearFocusBox {
            EmojiExample()
        }
    },
    Screen.Example("FastDelete") {
        ClearFocusBox {
            FastDelete()
        }
    },
    Screen.Example("OutlinedTextField") {
        ClearFocusBox {
            var text by remember { mutableStateOf("Some text") }
            OutlinedTextField(
                readOnly = true,
                value = text,
                onValueChange = { text = it },
                label = { Text("OutlinedTextField Label") },
            )
        }
    },
    Screen.Example("BasicTextField") {
        var text by remember { mutableStateOf("usage of BasicTextField") }
        BasicTextField(text, { text = it })
    },

    Screen.Example("BasicTextField2") {
        var textFieldState by remember { mutableStateOf("I am an old TextField") }
        val textFieldState2 = remember { TextFieldState("I am a BasicTextField(TextFieldState)") }
        val textFieldState3 = remember { TextFieldState(bigTextExampleString) }
        var isFromHardwareSource by remember { mutableStateOf<Boolean?>(null) }
        val hardwareSourceTracker = remember {
            InputTransformation {
                val tracker = changes as? ChangeTracker
                isFromHardwareSource = if (tracker != null && tracker.changeCount > 0) {
                    tracker.isFromHardwareSource(0)
                } else {
                    null
                }
            }
        }

        val defaultModifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color.LightGray,
                shape = RoundedCornerShape(4.dp)
            )

        ClearFocusBox {
            Column(Modifier.fillMaxWidth()) {
                BasicTextField(
                    textFieldState,
                    onValueChange = { textFieldState = it },
                    defaultModifier.height(24.dp)
                )
                Box(Modifier.height(16.dp))
                BasicTextField(
                    textFieldState2,
                    defaultModifier.height(24.dp)
                )
                Box(Modifier.height(16.dp))
                BasicTextField(
                    textFieldState3,
                    defaultModifier,
                    inputTransformation = hardwareSourceTracker,
                )
                Text(
                    text = "isFromHardwareSource: $isFromHardwareSource",
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    },

    Screen.Example("RTL and BiDi") {
        ClearFocusBox { RtlAndBidiTextfieldExample() }
    }
)

@Composable
private fun AlmostFullscreen() {
    val textState = remember {
        mutableStateOf(
            buildString {
                repeat(100) {
                    appendLine("Text line $it")
                }
            }
        )
    }
    TextField(
        textState.value, { textState.value = it },
        Modifier.fillMaxSize().padding(vertical = 40.dp)
    )
}

@Composable
private fun AlmostFullscreen2() {
    val state = remember {
        TextFieldState(
            buildString {
                repeat(100) {
                    appendLine("Text line $it")
                }
            }
        )
    }
    TextField(
        state,
        Modifier.fillMaxSize().padding(vertical = 40.dp).background(Color.LightGray)
    )
}

private val bigTextExampleString = """
    An example of big text in a TextField
    
    And another paragraph. Just to be sure. Here are random numbers: 0000000 00000000
    0000 000000000000 000000 00000000 0 000
    
    Test with some brackets (word) (longer phrase in a brackets)
    [different types of brackets] words between brackets [another one]
    {let's use curly brackets too} words between brackets {and again}
    
    This must be a random long phrase to check BiDi
    يجب أن تكون هذه عبارة طويلة عشوائية للتحقق من Bidi

    A compound emoji line: 👨‍👩‍👧‍👦👨‍👩‍👧‍👦👨‍👩‍👧‍👦👨‍👩‍👧‍👦,
    
    """.trimIndent() +
    """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. 
            Praesent placerat ligula sapien, sit amet viverra ligula bibendum sed. Fusce vitae neque pulvinar, 
            tempus sapien ut, tincidunt nisl. Interdum et malesuada fames ac ante ipsum primis in faucibus. 
            Sed aliquam congue euismod. Etiam porttitor vehicula ex, a interdum sapien. 
            Nam sed vehicula dui, quis ornare nulla. Nullam porttitor ante nec magna porta, eget cursus sapien auctor. 
            Sed hendrerit, nisi eget consequat molestie, eros massa tristique mi, sed consectetur nisl mauris quis eros. 
            Quisque id leo a sem euismod iaculis non sit amet orci. Proin efficitur pellentesque orci vitae facilisis. 
            Nulla vulputate tempus leo, ut vehicula ex. Maecenas fringilla pulvinar erat, ac dapibus libero tempor vel. 
            Sed et dapibus sapien, vel imperdiet augue.
    """.trimIndent().replace("\n", " ") +
    """
            Integer finibus justo facilisis mi porttitor,
            et malesuada ligula pretium. Integer ipsum felis, 
            dictum a metus ut, sagittis mattis libero. Morbi facilisis pulvinar nulla eget molestie. 
            Nulla porta neque eros, at vulputate turpis tristique pretium. 
            Vestibulum aliquet metus id nisi euismod varius. Nunc nec mi id lorem molestie interdum. 
            Fusce eget metus quis dui varius scelerisque et id mauris. In sit amet nunc sed tellus sagittis finibus. 
            Aliquam eleifend lorem vitae lobortis dapibus. Suspendisse ipsum nisi, molestie et porta quis, maximus at ante. 
            Nam et accumsan nisi, sit amet efficitur ante. Aliquam id volutpat quam, at vestibulum ligula. 
    """.trimIndent().replace("\n", " ")