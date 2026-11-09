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

package androidx.compose.ui.window

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.WebApplicationScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.platform.WebInsetsTestEnvironment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.w3c.dom.HTMLElement

@OptIn(InternalComposeUiApi::class)
class WebWindowInsetsTest : OnCanvasTests {
    @Test
    fun safeAreaIsProvidedToComposition() = runApplicationTest {
        withEnvironment { environment, container ->
            var capturedInsets: SafeAreaSnapshot? = null
            var density = Density(1f)
            createComposeWindow(configure = { enableBrowserWindowInsets = true }) {
                density = LocalDensity.current
                capturedInsets = LocalPlatformWindowInsets.current.captureSafeArea()
            }

            environment.setSafeArea(left = 40f, top = 10f)
            resize(container)

            val top = with(density) { 10.dp.roundToPx() }
            val left = with(density) { 40.dp.roundToPx() }
            awaitValue(SafeAreaSnapshot(
                statusBarTop = top,
                cutoutLeft = left,
                cutoutTop = top
            )) { capturedInsets }
        }
    }

    @Test
    fun disabledBrowserWindowInsetsStayZeroAfterResize() = runApplicationTest {
        withEnvironment { environment, container ->
            var capturedInsets: SafeAreaSnapshot? = null
            var capturedWidth: Float? = null
            createComposeWindow(configure = { enableBrowserWindowInsets = false }) {
                capturedInsets = LocalPlatformWindowInsets.current.captureSafeArea()
                capturedWidth = LocalWindowInfo.current.containerDpSize.width.value
            }
            awaitValue(SafeAreaSnapshot()) { capturedInsets }

            environment.setSafeArea(left = 40f, top = 10f, right = 20f, bottom = 30f)
            resize(container)
            // Wait for the resize to reach composition even though the insets stay zero.
            awaitValue(container.clientWidth.toFloat()) { capturedWidth }
            assertEquals(SafeAreaSnapshot(), capturedInsets)
        }
    }

    @Test
    fun containerResizeRecomposesInsetReader() = runApplicationTest {
        withEnvironment { environment, container ->
            var capturedTop: Int? = null
            var density = Density(1f)
            createComposeWindow(configure = { enableBrowserWindowInsets = true }) {
                density = LocalDensity.current
                // Read the state inside composition, not through a captured live getter.
                capturedTop = LocalPlatformWindowInsets.current.statusBars.top
            }

            environment.setSafeArea(top = 10f)
            resize(container)
            val initialTop = with(density) { 10.dp.roundToPx() }
            awaitValue(initialTop) { capturedTop }

            environment.setSafeArea(top = 50f)
            resize(container)
            val updatedTop = with(density) { 50.dp.roundToPx() }
            awaitValue(updatedTop) { capturedTop }
        }
    }

    private suspend fun WebApplicationScope.withEnvironment(
        block: suspend (WebInsetsTestEnvironment, HTMLElement) -> Unit
    ) {
        val environment = WebInsetsTestEnvironment()
        val container = getContainer() as HTMLElement
        val originalStyle = container.style.cssText
        try {
            // Real geometry, independent of the size of Karma's default test container.
            container.style.cssText = "position: fixed; left: 0; top: 0; width: 200px; height: 150px;"
            block(environment, container)
        } finally {
            try {
                getComposeWindowOrNull()?.dispose()
            } finally {
                container.style.cssText = originalStyle
                environment.restore()
            }
        }
    }

    private fun resize(container: HTMLElement) {
        container.style.width = "${container.clientWidth - 1}px"
    }

    private suspend fun <T> WebApplicationScope.awaitValue(
        expected: T,
        current: () -> T?
    ) {
        try {
            // Browser frames use real time, so do not let runTest advance a virtual timeout.
            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) {
                    while (current() != expected) {
                        awaitAnimationFrame()
                        awaitIdle()
                    }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for $expected; actual: ${current()}", timeout)
        }
        assertEquals(expected, current())
    }

    private data class SafeAreaSnapshot(
        val statusBarTop: Int = 0,
        val navigationBarBottom: Int = 0,
        val cutoutLeft: Int = 0,
        val cutoutTop: Int = 0,
        val cutoutRight: Int = 0,
        val cutoutBottom: Int = 0
    )

    private fun PlatformWindowInsets.captureSafeArea() = SafeAreaSnapshot(
        statusBarTop = statusBars.top,
        navigationBarBottom = navigationBars.bottom,
        cutoutLeft = displayCutout.left,
        cutoutTop = displayCutout.top,
        cutoutRight = displayCutout.right,
        cutoutBottom = displayCutout.bottom
    )
}
