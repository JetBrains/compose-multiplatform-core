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

package androidx.compose.ui.skiko

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.SkikoGraphics
import androidx.compose.ui.graphics.platform.PlatformGraphics
import androidx.compose.ui.graphics.platform.PlatformGraphicsRegistry
import androidx.compose.ui.platform.clearSkikoComposeImplementation
import androidx.compose.ui.platform.registerSkikoComposeImplementation
import androidx.compose.ui.text.platform.PlatformText
import androidx.compose.ui.text.platform.PlatformTextRegistry
import androidx.compose.ui.text.SkikoText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

@OptIn(InternalComposeUiApi::class)
class ImplementationRegistryTest {
    @AfterTest
    fun tearDown() {
        clearSkikoComposeImplementation()
    }

    @Test
    fun registerSkikoComposeImplementationIsIdempotent() {
        PlatformGraphicsRegistry.clear()
        PlatformTextRegistry.clear()

        registerSkikoComposeImplementation()
        registerSkikoComposeImplementation()

        androidx.compose.ui.graphics.Paint()
        androidx.compose.ui.text.font.createFontFamilyResolver()
    }

    @Test
    fun graphicsImplementationRejectsDifferentReplacement() {
        PlatformTextRegistry.clear()
        PlatformGraphicsRegistry.clear()
        PlatformGraphicsRegistry.register(SkikoGraphics)

        val error = assertFailsWith<IllegalStateException> {
            PlatformGraphicsRegistry.register(
                object : PlatformGraphics by SkikoGraphics {}
            )
        }

        assertContains(error.message.orEmpty(), "already registered with a different instance")
    }

    @Test
    fun textImplementationRejectsDifferentReplacement() {
        PlatformGraphicsRegistry.clear()
        PlatformTextRegistry.clear()
        PlatformTextRegistry.register(SkikoText)

        val error = assertFailsWith<IllegalStateException> {
            PlatformTextRegistry.register(
                object : PlatformText by SkikoText {}
            )
        }

        assertContains(error.message.orEmpty(), "already registered with a different instance")
    }

    @Test
    fun graphicsUsageFailsFastWithoutRegistration() {
        PlatformGraphicsRegistry.clear()

        val error = assertFailsWith<IllegalStateException> {
            androidx.compose.ui.graphics.Paint()
        }

        assertContains(error.message.orEmpty(), "No Compose UI graphics implementation is registered")
    }

    @Test
    fun textUsageFailsFastWithoutRegistration() {
        PlatformTextRegistry.clear()

        val error = assertFailsWith<IllegalStateException> {
            androidx.compose.ui.text.font.createFontFamilyResolver()
        }

        assertContains(error.message.orEmpty(), "No Compose UI text implementation is registered")
    }
}
