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

package androidx.compose.ui.layers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.UIKitInstrumentedTest
import androidx.compose.ui.test.findFocusedUITextInput
import androidx.compose.ui.test.findNodeWithTag
import androidx.compose.ui.test.findNodeWithTagOrNull
import androidx.compose.ui.test.runUIKitInstrumentedTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMakeRange
import platform.UIKit.UITextInputProtocol

class TextInputPendingCompositionLayerTest {
    @Test
    fun dialogCanOpenWhenPendingTextIsCommitted() = runUIKitInstrumentedTest {
        var text by mutableStateOf("")
        var showDialog by mutableStateOf(false)
        var textInput: UITextInputProtocol? = null
        var didCommitPendingText = false

        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.testTag(TextFieldTag)
                    )
                    Button(
                        onClick = { showDialog = true },
                        modifier = Modifier.testTag(OpenDialogButtonTag)
                    ) {
                        Text("Open dialog")
                    }
                }
            }

            if (showDialog) {
                Dialog(onDismissRequest = { showDialog = false }) {
                    SideEffect {
                        if (!didCommitPendingText) {
                            didCommitPendingText = true
                            // UIKit commits pending input while the new scene layer is being applied
                            textInput?.unmarkText()
                        }
                    }
                    Surface(Modifier.testTag(DialogTag)) {
                        Text(
                            text = "Dialog content",
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }

        findNodeWithTag(TextFieldTag).tap()
        waitForIdle()
        textInput = setPendingTextComposition()

        findNodeWithTag(OpenDialogButtonTag).tap()
        waitForIdle()

        assertNotNull(findNodeWithTagOrNull(DialogTag), "Dialog content should be visible")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun modalBottomSheetCanOpenWhenPendingTextIsCommitted() = runUIKitInstrumentedTest {
        var text by mutableStateOf("")
        var showSheet by mutableStateOf(false)
        var textInput: UITextInputProtocol? = null
        var didCommitPendingText = false

        setContent {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.testTag(TextFieldTag)
                    )
                    Button(
                        onClick = { showSheet = true },
                        modifier = Modifier.testTag(OpenSheetButtonTag)
                    ) {
                        Text("Open sheet")
                    }
                }
            }

            if (showSheet) {
                ModalBottomSheet(onDismissRequest = { showSheet = false }) {
                    SideEffect {
                        if (!didCommitPendingText) {
                            didCommitPendingText = true
                            // UIKit commits pending input while the new scene layer is being applied
                            textInput?.unmarkText()
                        }
                    }
                    Text(
                        text = "Sheet content",
                        modifier = Modifier
                            .padding(24.dp)
                            .testTag(SheetTag)
                    )
                }
            }
        }

        findNodeWithTag(TextFieldTag).tap()
        waitForIdle()
        textInput = setPendingTextComposition()

        findNodeWithTag(OpenSheetButtonTag).tap()
        waitForIdle()

        assertNotNull(findNodeWithTagOrNull(SheetTag), "Sheet content should be visible")
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun UIKitInstrumentedTest.setPendingTextComposition(): UITextInputProtocol {
        val input = assertNotNull(findFocusedUITextInput())
        val pendingText = "helloi"

        input.setMarkedText(
            markedText = pendingText,
            selectedRange = NSMakeRange(pendingText.length.toULong(), 0u)
        )
        return input
    }

    private companion object {
        const val TextFieldTag = "TextField"
        const val OpenDialogButtonTag = "OpenDialogButton"
        const val OpenSheetButtonTag = "OpenSheetButton"
        const val DialogTag = "Dialog"
        const val SheetTag = "Sheet"
    }
}
