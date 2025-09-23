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

package androidx.compose.ui.input.specs

import androidx.compose.ui.events.beforeInput
import androidx.compose.ui.events.compositionEnd
import androidx.compose.ui.events.compositionStart
import androidx.compose.ui.events.eventKeyCode
import androidx.compose.ui.events.keyEvent
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

internal interface СompositeInputTestSpec : TextFieldTestSpec {

    // delay in web tests called directly will be completely ignored
    private suspend fun waitFor(millis: Long) {
        withContext(Dispatchers.Default) { delay(millis) }
    }

    fun triggerComposingSequence(triggerKey: String, typedKey: String, triggeredKey: String): List<Event>

    @Test
    fun compositeInput() = runApplicationTest {
        val textFieldValue = createApplicationWithHolder()

        val backingTextField = getShadowRoot().querySelector("textarea")
        assertIs<HTMLTextAreaElement>(backingTextField)

        sendToHtmlInput(
        *triggerComposingSequence("a", "1", "啊").toTypedArray()
        )

        textFieldValue.awaitAndAssertTextEquals("啊")

        sendStandardKeyboardSequence("x")
        textFieldValue.awaitAndAssertTextEquals("啊x")
    }
}


internal interface ChromeCompositeInput : СompositeInputTestSpec {
    override fun triggerComposingSequence(
        triggerKey: String,
        typedKey: String,
        triggeredKey: String
    ): List<Event> {
        return listOf(
            keyEvent(triggerKey),
            compositionStart(),
            beforeInput("insertCompositionText", triggerKey),
            keyEvent(triggerKey, type = "keyup", isComposing = true),

            keyEvent(typedKey),
            beforeInput("insertCompositionText", typedKey, isComposing = true),
            compositionEnd(triggeredKey),
            keyEvent(typedKey, type = "keyup")
        )
    }
}

internal interface FirefoxCompositeInput : СompositeInputTestSpec {
    override fun triggerComposingSequence(
        triggerKey: String,
        typedKey: String,
        triggeredKey: String
    ): List<Event> {
        return listOf(
            keyEvent("Process", code  = triggerKey.eventKeyCode()),
            compositionStart(),
            beforeInput("insertCompositionText", triggerKey),
            keyEvent(triggerKey, type = "keyup"),
            keyEvent( "Process", code  = typedKey.eventKeyCode()),
            beforeInput("insertCompositionText", triggeredKey, isComposing = true),
            compositionEnd(triggeredKey),
            keyEvent(typedKey, type = "key up")
        )
    }
}

internal interface SafariCompositeInput : СompositeInputTestSpec {
    override fun triggerComposingSequence(
        triggerKey: String,
        typedKey: String,
        triggeredKey: String
    ): List<Event> {
        return listOf(
            compositionStart(),
            beforeInput("insertCompositionText", triggerKey, isComposing = true),
            keyEvent(triggerKey),
            keyEvent(triggerKey, type = "keyup"),
            beforeInput("deleteCompositionText", null, isComposing = true),
            beforeInput("insertFromComposition", triggeredKey, isComposing = true),
            compositionEnd(triggeredKey),
            keyEvent(typedKey),
            keyEvent(typedKey, type = "keyup")
        )
    }
}

internal interface IosCompositeInput : СompositeInputTestSpec {
    override fun triggerComposingSequence(
        triggerKey: String,
        typedKey: String,
        triggeredKey: String
    ): List<Event> {
        return listOf(
            keyEvent(triggerKey, keyCode = 229),
            compositionStart(),
            beforeInput("insertCompositionText", triggerKey,isComposing = true),
            keyEvent(triggerKey, type = "keyup"),
            beforeInput("insertCompositionText", triggeredKey, isComposing = true),
            beforeInput("deleteCompositionText", null, isComposing = true),
            beforeInput("insertFromComposition", triggeredKey, isComposing = true),
            compositionEnd(triggeredKey),
        )
    }
}