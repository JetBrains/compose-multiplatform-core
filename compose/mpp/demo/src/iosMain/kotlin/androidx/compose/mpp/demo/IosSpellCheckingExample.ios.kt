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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.TriStateCheckbox
import androidx.compose.mpp.demo.textfield.ClearFocusBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import platform.UIKit.UITextAutocorrectionType
import platform.UIKit.UITextSpellCheckingType

private const val TextWithTypos = "The quick brwon fox jumps over the lazzy dog"

val IosImeOptionsSpellCheckingExample = Screen.Example("Spell Checking") {
    ClearFocusBox {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SpellCheckingBlock("BTF1", nativeInput = false, textFieldState = false)
            SpellCheckingBlock("BTF2", nativeInput = false, textFieldState = true)
            SpellCheckingBlock("NITI BTF1", nativeInput = true, textFieldState = false)
            SpellCheckingBlock("NITI BTF2", nativeInput = true, textFieldState = true)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SpellCheckingBlock(
    title: String,
    nativeInput: Boolean,
    textFieldState: Boolean
) {
    var autoCorrect by remember { mutableStateOf(OptionValue.NotSet) }
    var autocorrection by remember { mutableStateOf(OptionValue.NotSet) }
    var spellChecking by remember { mutableStateOf(OptionValue.NotSet) }

    val keyboardOptions = KeyboardOptions(
        autoCorrectEnabled = autoCorrect.toBooleanOrNull(),
        platformImeOptions = PlatformImeOptions {
            usingNativeTextInput(nativeInput)
            autocorrectionType(autocorrection.toAutocorrectionType())
            spellCheckingType(spellChecking.toSpellCheckingType())
        }
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, color = Color.Black, fontSize = 18.sp)

        val fieldModifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
        val textStyle = TextStyle(color = Color.Black, fontSize = 16.sp)

        if (textFieldState) {
            val state = rememberTextFieldState(TextWithTypos)
            BasicTextField(
                state = state,
                modifier = fieldModifier,
                keyboardOptions = keyboardOptions,
                textStyle = textStyle
            )
        } else {
            var text by remember { mutableStateOf(TextWithTypos) }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = fieldModifier,
                keyboardOptions = keyboardOptions,
                textStyle = textStyle
            )
        }

        OptionRow(
            "KeyboardOptions.autoCorrectEnabled",
            autoCorrect,
            autoCorrect.label(on = "true", off = "false")
        ) { autoCorrect = autoCorrect.next() }

        OptionRow(
            "PlatformImeOptions.autocorrectionType",
            autocorrection,
            autocorrection.label(on = "Yes", off = "No")
        ) { autocorrection = autocorrection.next() }

        OptionRow(
            "PlatformImeOptions.spellCheckingType",
            spellChecking,
            spellChecking.label(on = "Yes", off = "No")
        ) { spellChecking = spellChecking.next() }
    }
}

@Composable
private fun OptionRow(
    name: String,
    value: OptionValue,
    valueLabel: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TriStateCheckbox(
            state = value.toToggleableState(),
            onClick = onClick
        )
        Text("$name = $valueLabel", color = Color.Black, fontSize = 14.sp)
    }
}

private enum class OptionValue {
    NotSet,
    On,
    Off,
}

private fun OptionValue.next() = when (this) {
    OptionValue.NotSet -> OptionValue.On
    OptionValue.On -> OptionValue.Off
    OptionValue.Off -> OptionValue.NotSet
}

private fun OptionValue.toToggleableState() = when (this) {
    OptionValue.NotSet -> ToggleableState.Off
    OptionValue.On -> ToggleableState.On
    OptionValue.Off -> ToggleableState.Indeterminate
}

private fun OptionValue.label(on: String, off: String) = when (this) {
    OptionValue.NotSet -> "not set"
    OptionValue.On -> on
    OptionValue.Off -> off
}

private fun OptionValue.toBooleanOrNull() = when (this) {
    OptionValue.NotSet -> null
    OptionValue.On -> true
    OptionValue.Off -> false
}

private fun OptionValue.toAutocorrectionType() = when (this) {
    OptionValue.NotSet -> null
    OptionValue.On -> UITextAutocorrectionType.UITextAutocorrectionTypeYes
    OptionValue.Off -> UITextAutocorrectionType.UITextAutocorrectionTypeNo
}

private fun OptionValue.toSpellCheckingType() = when (this) {
    OptionValue.NotSet -> null
    OptionValue.On -> UITextSpellCheckingType.UITextSpellCheckingTypeYes
    OptionValue.Off -> UITextSpellCheckingType.UITextSpellCheckingTypeNo
}
