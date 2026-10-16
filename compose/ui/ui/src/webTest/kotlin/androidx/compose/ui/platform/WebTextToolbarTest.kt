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

package androidx.compose.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.WebApplicationScope
import androidx.compose.ui.currentTimeMillis
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.w3c.dom.HTMLElement

@OptIn(InternalComposeUiApi::class)
class WebTextToolbarTest : OnCanvasTests {

    @Test
    fun multipleTextFieldsProduceOnlyOneToolbar() = runApplicationTest {
        lateinit var toolbar: WebTextToolbar
        var fieldCount by mutableStateOf(2)
        var density = 1f
        createComposeWindow {
            toolbar = LocalTextToolbar.current as WebTextToolbar
            density = LocalDensity.current.density
            Column {
                repeat(fieldCount) { index ->
                    BasicTextField(
                        state = rememberTextFieldState("Field $index"),
                        modifier = Modifier.testTag("field$index"),
                    )
                }
            }
        }

        awaitIdle()
        assertNotNull(toolbar.renderer, "Text fields must install the window toolbar renderer")
        toolbar.showCopyMenu(menuRect(density)) {}
        awaitA11YCondition {
            assertEquals(TextToolbarStatus.Shown, toolbar.status, "The menu must stay shown")
            toolbarActionCount() > 0
        }
        assertEquals(1, toolbarActionCount())
        assertEquals(TextToolbarStatus.Shown, toolbar.status)

        fieldCount = 3
        awaitA11YCondition { a11yElement("#field2") != null }
        assertEquals(1, toolbarActionCount(), "Adding a text field must not duplicate the toolbar")

        toolbar.hide()
        awaitA11YCondition { toolbarActionCount() == 0 }
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
    }

    @Test
    fun unfocusedLegacyTextFieldDoesNotHideToolbar() = runApplicationTest {
        lateinit var toolbar: WebTextToolbar
        var density = 1f
        createComposeWindow {
            toolbar = LocalTextToolbar.current as WebTextToolbar
            density = LocalDensity.current.density
            BasicTextField(value = "Text", onValueChange = {})
        }

        awaitIdle()
        toolbar.showCopyMenu(menuRect(density)) {}
        awaitA11YCondition {
            assertEquals(
                TextToolbarStatus.Shown,
                toolbar.status,
                "An unfocused field must not hide the menu",
            )
            toolbarActionCount() == 1
        }
        repeat(10) {
            awaitAnimationFrame()
            assertEquals(TextToolbarStatus.Shown, toolbar.status)
            assertEquals(1, toolbarActionCount())
        }
    }

    @Test
    fun showMenuUpdatesRectAndCallbacks() = runApplicationTest {
        lateinit var toolbar: WebTextToolbar
        var density = 1f
        var firstCallbackCount = 0
        var secondCallbackCount = 0
        createComposeWindow {
            toolbar = LocalTextToolbar.current as WebTextToolbar
            density = LocalDensity.current.density
            BasicTextField(state = rememberTextFieldState("Text"))
        }

        awaitIdle()
        assertNotNull(toolbar.renderer, "Text fields must install the window toolbar renderer")
        toolbar.showCopyMenu(menuRect(density)) { firstCallbackCount++ }
        awaitA11YCondition {
            assertEquals(TextToolbarStatus.Shown, toolbar.status, "The menu must stay shown")
            toolbarActionCount() > 0
        }
        assertEquals(1, toolbarActionCount())
        val initialBounds = toolbarAction().getBoundingClientRect()
        toolbarAction().click()
        assertEquals(1, firstCallbackCount)

        toolbar.showCopyMenu(menuRect(density, left = 40f, top = 120f)) {
            secondCallbackCount++
        }
        awaitA11YCondition {
            val bounds = toolbarAction().getBoundingClientRect()
            bounds.left > initialBounds.left && bounds.top > initialBounds.top
        }

        val updatedBounds = toolbarAction().getBoundingClientRect()
        assertEquals(20.0, updatedBounds.left - initialBounds.left, absoluteTolerance = 1.0)
        assertEquals(20.0, updatedBounds.top - initialBounds.top, absoluteTolerance = 1.0)
        assertEquals(1, toolbarActionCount(), "Updating the menu must replace, not duplicate it")
        toolbarAction().click()
        assertEquals(1, firstCallbackCount, "The previous callback must not be invoked again")
        assertEquals(1, secondCallbackCount)
    }

    @Test
    fun toolbarPositionDoesNotDependOnTextFieldParentOffset() = runApplicationTest {
        lateinit var toolbar: WebTextToolbar
        var density = 1f
        var parentOffset by mutableStateOf(10.dp)
        createComposeWindow {
            toolbar = LocalTextToolbar.current as WebTextToolbar
            density = LocalDensity.current.density
            Box(Modifier.offset(parentOffset, parentOffset)) {
                BasicTextField(
                    state = rememberTextFieldState("Text"),
                    modifier = Modifier.testTag("textField"),
                )
            }
        }

        awaitIdle()
        assertNotNull(toolbar.renderer, "Text fields must install the window toolbar renderer")
        // showMenu receives window coordinates, not coordinates relative to the text field.
        toolbar.showCopyMenu(menuRect(density)) {}
        awaitA11YCondition {
            assertEquals(TextToolbarStatus.Shown, toolbar.status, "The menu must stay shown")
            toolbarActionCount() > 0 && a11yElement("#textField") != null
        }
        val initialToolbarBounds = toolbarAction().getBoundingClientRect()
        val initialFieldBounds = assertNotNull(a11yElement("#textField")).getBoundingClientRect()

        parentOffset = 40.dp
        awaitA11YCondition {
            val bounds = assertNotNull(a11yElement("#textField")).getBoundingClientRect()
            bounds.left > initialFieldBounds.left && bounds.top > initialFieldBounds.top
        }

        val updatedFieldBounds = assertNotNull(a11yElement("#textField")).getBoundingClientRect()
        assertEquals(30.0, updatedFieldBounds.left - initialFieldBounds.left, absoluteTolerance = 1.0)
        assertEquals(30.0, updatedFieldBounds.top - initialFieldBounds.top, absoluteTolerance = 1.0)
        val updatedToolbarBounds = toolbarAction().getBoundingClientRect()
        assertEquals(initialToolbarBounds.left, updatedToolbarBounds.left, absoluteTolerance = 1.0)
        assertEquals(initialToolbarBounds.top, updatedToolbarBounds.top, absoluteTolerance = 1.0)
        assertEquals(1, toolbarActionCount())
    }

    // BasicTextField has no buttons; the only button is the single Copy action requested below.
    private fun toolbarActionCount(): Int =
        assertNotNull(getA11YContainer()).querySelectorAll("[role=button]").length

    private fun toolbarAction(): HTMLElement = assertNotNull(a11yElement("[role=button]"))

    private fun a11yElement(selector: String): HTMLElement? =
        assertNotNull(getA11YContainer()).querySelector(selector) as? HTMLElement

    private suspend fun WebApplicationScope.awaitA11YCondition(condition: () -> Boolean) {
        awaitIdle()
        val deadline = currentTimeMillis() + 5_000
        while (!condition()) {
            assertTrue(
                currentTimeMillis() < deadline,
                "Timed out waiting for toolbar DOM update: ${getA11YContainer()?.innerHTML}",
            )
            awaitAnimationFrame()
        }
    }

    private fun menuRect(density: Float, left: Float = 20f, top: Float = 100f): Rect = Rect(
        left = left * density,
        top = top * density,
        right = (left + 20f) * density,
        bottom = (top + 20f) * density,
    )

    private fun WebTextToolbar.showCopyMenu(rect: Rect, onCopyRequested: () -> Unit) {
        showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
        )
    }
}
