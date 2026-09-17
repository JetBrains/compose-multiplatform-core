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

package androidx.compose.ui.platform.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.w3c.dom.HTMLElement

class A11YTextFieldTest : OnCanvasTests {

    @Test
    fun textFieldWithLabelUpdatesEditableText() = runApplicationTest {
        var text by mutableStateOf("")

        createComposeWindow {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Paste here") },
                modifier = Modifier.testTag("textFieldTag"),
            )
        }

        awaitA11YChanges()

        val textField = getShadowRoot().getElementById("textFieldTag") as? HTMLElement
        assertNotNull(textField)
        assertEquals("Paste here", textField.getAttribute("aria-label"))

        text = "Entered text"
        awaitA11YChanges()

        assertEquals("Entered text", textField.innerText)
        assertEquals("Paste here", textField.getAttribute("aria-label"))
    }

    @Test
    fun textFieldWithPlaceholderUpdatesEditableText() = runApplicationTest {
        var text by mutableStateOf("")

        createComposeWindow {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Paste here") },
                modifier = Modifier.testTag("textFieldTag"),
            )
        }

        awaitA11YChanges()

        val textField = getShadowRoot().getElementById("textFieldTag") as? HTMLElement
        assertNotNull(textField)

        text = "Entered text"
        awaitA11YChanges()

        assertEquals("Entered text", textField.innerText)
    }

    @Test
    fun textFieldWithLabelKeepsInteractiveChildren() = runApplicationTest {
        var text by mutableStateOf("")
        var trailingIconClicks = 0

        createComposeWindow {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Paste here") },
                trailingIcon = {
                    IconButton(
                        onClick = { trailingIconClicks++ },
                        modifier = Modifier.testTag("trailingIconTag"),
                    ) {
                        Text("Clear")
                    }
                },
                modifier = Modifier.testTag("textFieldTag"),
            )
        }

        awaitA11YChanges()

        val trailingIcon = getShadowRoot().getElementById("trailingIconTag") as? HTMLElement
        assertNotNull(trailingIcon)
        assertEquals("button", trailingIcon.getAttribute("role"))

        text = "Entered text"
        awaitA11YChanges()

        val trailingIconAfterTextUpdate =
            getShadowRoot().getElementById("trailingIconTag") as? HTMLElement
        assertSame(trailingIcon, trailingIconAfterTextUpdate)
        trailingIcon.click()
        assertEquals(1, trailingIconClicks)
    }

    @Test
    fun passwordTextIsMaskedByDefaultAndCanBeExplicitlyRevealed() = runApplicationTest {
        val password = "secret"
        var revealPassword by mutableStateOf(false)

        createComposeWindow {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .testTag("passwordFieldTag")
                    .semantics {
                        editableText = AnnotatedString(password)
                        password(isPasswordObfuscated = !revealPassword)
                    }
            )
        }

        awaitA11YChanges()

        val passwordField = getShadowRoot().getElementById("passwordFieldTag") as? HTMLElement
        assertNotNull(passwordField)
        assertEquals("textbox", passwordField.getAttribute("role"))
        assertEquals("••••••", passwordField.innerText)
        assertFalse(passwordField.innerText.contains(password))

        revealPassword = true
        awaitA11YChanges()

        assertEquals(password, passwordField.innerText)
    }
}