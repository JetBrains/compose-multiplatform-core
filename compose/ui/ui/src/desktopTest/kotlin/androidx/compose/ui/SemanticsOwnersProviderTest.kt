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

package androidx.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.runApplicationTest
import java.awt.BorderLayout
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertContentEquals

class SemanticsOwnersProviderTest {

    @Test
    fun semanticsOwnersProvidedInComposeWindow() = runApplicationTest {
        launchTestWindowApplication {
            TextApp()
        }
        awaitIdle()
        assertSemanticsOwnersProvidedBy(window::semanticsOwners)
    }

    fun semanticsOwnersProvidedInComposePanel(visible: Boolean) = runApplicationTest {
        val window = JFrame()
        try {
            val composePanel = ComposePanel()
            composePanel.setContent {
                TextApp()
            }
            composePanel.isVisible = visible

            window.contentPane.add(composePanel, BorderLayout.CENTER)
            window.pack()
            window.isVisible = true

            awaitIdle()
            assertSemanticsOwnersProvidedBy(composePanel::semanticsOwners)
        } finally {
            window.dispose()
        }
    }

    @Test
    fun semanticsOwnersProvidedInVisibleComposePanel() =
        semanticsOwnersProvidedInComposePanel(visible = true)

    @Test
    fun semanticsOwnersProvidedInInvisibleComposePanel() =
        semanticsOwnersProvidedInComposePanel(visible = false)

    @Test
    fun semanticsOwnersProvidedInImageComposeScene() {
        val imageComposeScene = ImageComposeScene(800, 600) {
            TextApp()
        }
        imageComposeScene.render(0L)

        assertSemanticsOwnersProvidedBy(imageComposeScene::semanticsOwners)
    }

    private fun assertSemanticsOwnersProvidedBy(
        getSemanticsOwners: () -> Collection<SemanticsOwner>
    ) {
        val strings = getSemanticsOwners().collectText().map { it.text }
        assertContentEquals(listOf("Hello", "World"), strings)
    }

    @Composable
    private fun TextApp() {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(64.dp)
        ) {
            Text("Hello")
            OutlinedTextField(rememberTextFieldState("World"))
        }
    }
}

private fun Collection<SemanticsOwner>.collectText(): List<AnnotatedString> {
    val result = mutableListOf<AnnotatedString>()
    forEach {
        it.rootSemanticsNode.collectTextRecursive(result)
    }
    return result
}

private fun SemanticsNode.collectTextRecursive(result: MutableList<AnnotatedString>) {
    result.addAll(config.getOrNull(SemanticsProperties.Text) ?: emptyList())
    config.getOrNull(SemanticsProperties.EditableText)?.let {
        result.add(it)
    }
    for (child in children) {
        child.collectTextRecursive(result)
    }
}
