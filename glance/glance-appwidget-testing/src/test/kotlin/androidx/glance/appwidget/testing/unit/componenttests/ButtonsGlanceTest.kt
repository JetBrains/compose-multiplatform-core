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

package androidx.glance.appwidget.testing.unit.componenttests

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.glance.Button
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.layout.Column
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.testing.unit.assertHasClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import org.junit.Test

private const val arbitraryText = "some text"
private const val buttonTestTag = "button-under-test"

private val anImageProvider = ImageProvider(uri = Uri.parse("example.com"))
private const val contentDescription = "a content description"

class ButtonsGlanceTest {

    @Test
    fun translateButton_hasCorrectText() = runGlanceAppWidgetUnitTest {
        // Set the composable to test
        val buttonText = "Button Text"
        val m3ButtonText = "M3 Btn Text"
        provideComposable {
            Column() {
                FilledButton(text = m3ButtonText, onClick = {})
                Button(buttonText, onClick = {})
            }
        }

        // Perform assertions
        onNode(hasText(m3ButtonText)).assertExists()
        onNode(hasText(buttonText)).assertExists()
    }

    @Test
    fun button_hasClick() = runGlanceAppWidgetUnitTest {
        provideComposable { TestTextButton() }

        onNode(hasTestTag(buttonTestTag)).assertHasClickAction()
    }

    @Test
    fun m3FilledButton_hasClick() = runGlanceAppWidgetUnitTest {
        provideComposable { TestTextButton() }
        onNode(hasTestTag(buttonTestTag)).assertHasClickAction()
    }

    @Test
    fun m3CircleIconButton_hasClick() = runGlanceAppWidgetUnitTest {
        provideComposable { TestCircleIconButton() }
        onNode(hasTestTag(buttonTestTag)).assertHasClickAction()
    }

    @Test
    fun m3SquareIconButton_hasClick() = runGlanceAppWidgetUnitTest {
        provideComposable { TestSquareIconButton() }
        onNode(hasTestTag(buttonTestTag)).assertHasClickAction()
    }
}

@Composable
private fun TestTextButton() =
    FilledButton(
        arbitraryText,
        onClick = {},
        modifier = GlanceModifier.semantics { testTag = buttonTestTag },
    )

@Composable
private fun TestCircleIconButton() =
    CircleIconButton(
        imageProvider = anImageProvider,
        contentDescription = contentDescription,
        onClick = {},
        modifier = GlanceModifier.semantics { testTag = buttonTestTag },
    )

@Composable
private fun TestSquareIconButton() =
    SquareIconButton(
        imageProvider = anImageProvider,
        contentDescription = contentDescription,
        onClick = {},
        modifier = GlanceModifier.semantics { testTag = buttonTestTag },
    )
