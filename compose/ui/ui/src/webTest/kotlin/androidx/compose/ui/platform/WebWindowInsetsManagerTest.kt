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

import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window

class WebWindowInsetsManagerTest {
    @Test
    fun safeAreaIsMappedToInsetTypes() = withManager { environment, manager ->
        environment.setSafeArea(left = 40f, top = 10f, right = 20f, bottom = 30f)
        manager.onCanvasResized(environment.canvas())

        val insets = manager.windowInsets
        assertInsets(insets.statusBars, top = 10)
        assertInsets(insets.navigationBars, bottom = 30)
        assertInsets(insets.systemBars, left = 40, top = 10, right = 20, bottom = 30)
        assertInsets(insets.displayCutout, left = 40, top = 10, right = 20, bottom = 30)
        assertInsets(insets.systemGestures, left = 40, top = 10, right = 20, bottom = 30)
        assertInsets(insets.mandatorySystemGestures, top = 10, bottom = 30)
        assertInsets(insets.tappableElement, top = 10)
    }

    @Test
    fun safeAreaIsClippedOnAllSides() = withManager { environment, manager ->
        environment.setSafeArea(left = 10f, top = 20f, right = 30f, bottom = 40f)
        manager.onCanvasResized(environment.canvas(
            left = 6f,
            top = 15f,
            right = window.innerWidth - 12f,
            bottom = window.innerHeight - 25f
        ))

        assertInsets(manager.windowInsets.systemBars, left = 4, top = 5, right = 18, bottom = 15)
    }

    @Test
    fun canvasOutsideSafeAreaHasZeroInsets() = withManager { environment, manager ->
        environment.setSafeArea(left = 10f, top = 20f, right = 30f, bottom = 40f)
        manager.onCanvasResized(environment.canvas(
            left = 11f,
            top = 21f,
            right = window.innerWidth - 31f,
            bottom = window.innerHeight - 41f
        ))

        assertInsets(manager.windowInsets.systemBars)
    }

    @Test
    fun cssPixelsAreScaledAndRounded() = withManager(
        densityProvider = { Density(1.5f) }
    ) { environment, manager ->
        environment.setSafeArea(left = 1f, top = 2.5f, right = 3f, bottom = 4f)
        manager.onCanvasResized(environment.canvas())

        assertInsets(manager.windowInsets.systemBars, left = 2, top = 4, right = 5, bottom = 6)
    }

    @Test
    fun resizeReadsUpdatedSafeArea() = withManager { environment, manager ->
        val canvas = environment.canvas()
        val insets = manager.windowInsets
        environment.setSafeArea(top = 10f)
        manager.onCanvasResized(canvas)
        assertInsets(insets.statusBars, top = 10)

        environment.setSafeArea(top = 50f)
        manager.onCanvasResized(canvas)
        assertInsets(insets.statusBars, top = 50)
    }

    @Test
    fun resizeReadsUpdatedCanvasGeometry() = withManager { environment, manager ->
        environment.setSafeArea(top = 20f)
        manager.onCanvasResized(environment.canvas())
        assertInsets(manager.windowInsets.statusBars, top = 20)

        manager.onCanvasResized(environment.canvas(top = 15f))
        assertInsets(manager.windowInsets.statusBars, top = 5)
    }

    @Test
    fun resizeReadsUpdatedDensity() {
        var density = Density(1f)
        withManager(densityProvider = { density }) { environment, manager ->
            environment.setSafeArea(top = 10f)
            manager.onCanvasResized(environment.canvas())
            assertInsets(manager.windowInsets.statusBars, top = 10)

            density = Density(2f)
            manager.onCanvasResized(environment.canvas())
            assertInsets(manager.windowInsets.statusBars, top = 20)
        }
    }

    private fun withManager(
        densityProvider: () -> Density = { Density(1f) },
        block: (WebInsetsTestEnvironment, WebWindowInsetsManager) -> Unit
    ) {
        val environment = WebInsetsTestEnvironment()
        var manager: WebWindowInsetsManager? = null
        try {
            val createdManager = WebWindowInsetsManager(densityProvider, environment.canvas())
            manager = createdManager
            block(environment, createdManager)
        } finally {
            try {
                manager?.dispose()
            } finally {
                environment.restore()
            }
        }
    }

    private fun assertInsets(
        actual: PlatformInsets,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ) {
        assertEquals(left, actual.left, "Left inset")
        assertEquals(top, actual.top, "Top inset")
        assertEquals(right, actual.right, "Right inset")
        assertEquals(bottom, actual.bottom, "Bottom inset")
    }
}
