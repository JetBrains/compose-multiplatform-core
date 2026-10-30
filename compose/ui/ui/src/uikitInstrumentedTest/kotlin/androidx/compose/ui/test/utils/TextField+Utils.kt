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

package androidx.compose.ui.test.utils

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import platform.UIKit.UIView
import platform.UIKit.UIWindow

internal enum class BasicTextFieldType { V1, V2 }

/**
 * Runs [testBlock] for every combination of the parameters. Passing a non-null parameter limits
 * the run to that value only.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun runTextFieldTest(
    newContextMenuEnabled: Boolean? = null,
    usingNativeTextInput: Boolean? = null,
    textFieldType: BasicTextFieldType? = null,
    testBlock: UIKitInstrumentedTest.(
        newContextMenuEnabled: Boolean,
        usingNativeTextInput: Boolean,
        textFieldType: BasicTextFieldType
    ) -> Unit
) {
    val oldValue = ComposeFoundationFlags.isNewContextMenuEnabled

    val newContextMenus = newContextMenuEnabled?.let(::listOf) ?: listOf(false, true)
    val nativeTextInputs = usingNativeTextInput?.let(::listOf) ?: listOf(false, true)
    val types = textFieldType?.let(::listOf) ?: BasicTextFieldType.entries

    val variations = newContextMenus.flatMap { menu ->
        nativeTextInputs.flatMap { native ->
            types.map { type -> Triple(menu, native, type) }
        }
    }

    try {
        for ((newContextMenuEnabled, usingNativeTextInput, type) in variations) {
            ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenuEnabled
            println("Debug: newContextMenuEnabled=$newContextMenuEnabled, usingNativeTextInput=$usingNativeTextInput, textFieldType=$type")
            runUIKitInstrumentedTest {
                testBlock(newContextMenuEnabled, usingNativeTextInput, type)
            }
        }
    } finally {
        ComposeFoundationFlags.isNewContextMenuEnabled = oldValue
    }
}

/**
 * The state of [TestTextField] that works for both [BasicTextFieldType]s.
 *
 * @param filter accepts or rejects a new text for [BasicTextFieldType.V1]. Use
 * [InputTransformation] for [BasicTextFieldType.V2].
 */
internal class TestTextFieldState(
    text: String = "",
    selection: TextRange = TextRange.Zero,
    val textFieldType: BasicTextFieldType,
    val filter: (String) -> Boolean = { true },
) {
    val value = mutableStateOf(TextFieldValue(text, selection))
    val state = TextFieldState(text, selection)

    val text: String
        get() = if (textFieldType == BasicTextFieldType.V1) value.value.text else state.text.toString()

    val selection: TextRange
        get() = if (textFieldType == BasicTextFieldType.V1) value.value.selection else state.selection

    /** Changes the selection from the code, bypassing the text input. */
    fun updateSelection(selection: TextRange) {
        if (textFieldType == BasicTextFieldType.V1) {
            value.value = value.value.copy(selection = selection)
        } else {
            state.edit { this.selection = selection }
        }
    }

    fun isFullySelected(): Boolean = if (textFieldType == BasicTextFieldType.V1) {
        value.value.selection.start == 0 && value.value.selection.end == value.value.text.length
    } else {
        state.selection.start == 0 && state.selection.end == state.text.length
    }
}

/**
 * [BasicTextField] of the [TestTextFieldState.textFieldType].
 *
 * [inputTransformation] and [outputTransformation] are applied to [BasicTextFieldType.V2] only,
 * and [visualTransformation] - to [BasicTextFieldType.V1] only.
 */
@Suppress("DEPRECATION")
@Composable
internal fun TestTextField(
    state: TestTextFieldState,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    outputTransformation: OutputTransformation? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    if (state.textFieldType == BasicTextFieldType.V1) {
        BasicTextField(
            value = state.value.value,
            onValueChange = {
                if (state.filter(it.text)) {
                    state.value.value = it
                }
            },
            modifier = modifier,
            keyboardOptions = keyboardOptions,
            readOnly = readOnly,
            visualTransformation = visualTransformation,
        )
    } else {
        BasicTextField(
            state = state.state,
            modifier = modifier,
            keyboardOptions = keyboardOptions,
            readOnly = readOnly,
            inputTransformation = inputTransformation,
            outputTransformation = outputTransformation,
        )
    }
}

internal val loupeClassNames = listOf(
    "_UITextMagnifiedLoupeView",
    "_UITextLoupeView",
    "LoupeView"
)

internal val UIView.isLoupeView: Boolean get() {
    val name = objcClassName(this) ?: return false
    return loupeClassNames.any { name.contains(it) }
}

private fun findFirstDescendant(root: UIView, predicate: (UIView) -> Boolean): UIView? {
    if (predicate(root)) return root
    root.subviews.forEach { view ->
        view as UIView
        val hit = findFirstDescendant(view, predicate)
        if (hit != null) return hit
    }
    return null
}

internal fun UIKitInstrumentedTest.findFirstDescendant(predicate: (UIView) -> Boolean): UIView? {
    val windowScene = viewController.view.window?.windowScene ?: return null
    windowScene.windows.forEach { window ->
        window as UIWindow
        val hit = findFirstDescendant(window, predicate)
        if (hit != null) return hit
    }
    return null
}
