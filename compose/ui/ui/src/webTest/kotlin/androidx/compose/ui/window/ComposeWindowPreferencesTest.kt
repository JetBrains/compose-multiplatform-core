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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.WebApplicationScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.browser.document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.w3c.dom.HTMLElement
import org.w3c.dom.get

class ComposeWindowPreferencesTest : OnCanvasTests {
    @Test
    fun preferencesApplyInitiallyAndLiveWithAccessibilityDisabled() = runApplicationTest {
        val probe = TestFontSizeProbe(20f)
        val media = testMediaQuery(initialMatches = true)
        lateinit var preferences: WebPreferencesObserver
        var density = Density(1f)
        var effectScale: MotionDurationScale? = null
        var compositionIdentity: Any? = null
        createComposeWindow(configure = {
            isA11YEnabled = false
            fontFallbackUrl = null
            webPreferencesObserverFactory = { root ->
                WebPreferencesObserver(root, probe, media.media).also { preferences = it }
            }
        }) {
            density = LocalDensity.current
            compositionIdentity = remember { Any() }
            LaunchedEffect(Unit) {
                effectScale = currentCoroutineContext()[MotionDurationScale]
            }
        }
        awaitUntil { effectScale != null }
        assertEquals(1.25f, density.fontScale)
        assertNull(getA11YContainer())
        assertSame(preferences.motionDurationScale, effectScale)
        assertEquals(0f, effectScale!!.scaleFactor)
        val identity = compositionIdentity
        val pixelDensity = density.density

        probe.changeTo(24f)
        media.changeTo(false)
        awaitUntil { density.fontScale == 1.5f }
        assertEquals(pixelDensity, density.density)
        assertEquals(1f, effectScale!!.scaleFactor)
        assertSame(identity, compositionIdentity)

        val composeWindow = assertNotNull(getComposeWindowOrNull())
        composeWindow.dispose()
        composeWindow.dispose()
        assertEquals(0, media.listenerCount)
        assertEquals(1, probe.disposeCount)
        media.sendQueuedChange(true)
        probe.changeTo(32f)
        assertEquals(1f, effectScale!!.scaleFactor)
        assertEquals(1.5f, preferences.fontScale)
    }

    @Test
    fun fontOnlyChangesRelayoutSpButNotDpOrTheCanvasAndUpdateAccessibilityBounds() =
        runApplicationTest {
            val probe = TestFontSizeProbe(20f)
            var density = Density(1f)
            var dpBounds: Rect? = null
            var textBounds: Rect? = null
            createComposeWindow(configure = {
                fontFallbackUrl = null
                webPreferencesObserverFactory = { WebPreferencesObserver(it, probe, null) }
            }) {
                density = LocalDensity.current
                Column {
                    Box(Modifier.size(40.dp).testTag("dp").onGloballyPositioned {
                        dpBounds = it.boundsInRoot()
                    })
                    BasicText(
                        "Scaling",
                        modifier = Modifier.testTag("text").onGloballyPositioned {
                            textBounds = it.boundsInRoot()
                        },
                        style = TextStyle(fontSize = 16.sp)
                    )
                }
            }
            awaitUntil { textBounds != null }
            assertEquals(1.25f, density.fontScale)
            awaitMirrorBounds("text", assertNotNull(textBounds), density)
            val initialDpBounds = assertNotNull(dpBounds)
            val initialTextBounds = assertNotNull(textBounds)
            val initialDensity = density.density
            val canvas = getCanvas()
            val initialCanvasBounds = canvas.getBoundingClientRect()
            val initialBufferWidth = canvas.width
            val initialBufferHeight = canvas.height

            // No viewport, CSS size, DPR, or visualViewport change accompanies this notification.
            probe.changeTo(24f)
            awaitUntil {
                density.fontScale == 1.5f &&
                    (textBounds?.height ?: 0f) > initialTextBounds.height
            }
            assertEquals(initialDensity, density.density)
            assertEquals(initialDpBounds, dpBounds)
            assertEquals(40f, assertNotNull(dpBounds).width / density.density, 1f)
            assertEquals(initialBufferWidth, canvas.width)
            assertEquals(initialBufferHeight, canvas.height)
            val currentCanvasBounds = canvas.getBoundingClientRect()
            assertEquals(initialCanvasBounds.width, currentCanvasBounds.width, 0.01)
            assertEquals(initialCanvasBounds.height, currentCanvasBounds.height, 0.01)
            awaitMirrorBounds("text", assertNotNull(textBounds), density)
            awaitMirrorBounds("dp", assertNotNull(dpBounds), density)

            val unchangedDensity = density
            probe.changeTo(24f)
            awaitAnimationFrame()
            awaitIdle()
            assertSame(unchangedDensity, density)
        }

    @Test
    fun backingTextInputTracksFontDrivenRelayout() = runApplicationTest {
        val probe = TestFontSizeProbe()
        val focusRequester = FocusRequester()
        var density = Density(1f)
        var inputBounds: Rect? = null
        createComposeWindow(configure = {
            fontFallbackUrl = null
            webPreferencesObserverFactory = { WebPreferencesObserver(it, probe, null) }
        }) {
            density = LocalDensity.current
            Column {
                BasicText("Above input", style = TextStyle(fontSize = 16.sp))
                BasicTextField(
                    value = "Input",
                    onValueChange = {},
                    modifier = Modifier.width(150.dp).focusRequester(focusRequester)
                        .onGloballyPositioned { inputBounds = it.boundsInRoot() },
                    textStyle = TextStyle(fontSize = 16.sp)
                )
            }
        }
        awaitUntil { inputBounds != null }
        focusRequester.requestFocus()
        awaitUntil { backingInput() != null }
        awaitAnimationFrame()
        awaitIdle()
        val initialInputTop = assertNotNull(backingInput()).getBoundingClientRect().top
        val initialComposeTop = assertNotNull(inputBounds).top

        probe.changeTo(24f)
        awaitUntil {
            density.fontScale == 1.5f &&
                (inputBounds?.top ?: 0f) > initialComposeTop &&
                (backingInput()?.getBoundingClientRect()?.top ?: 0.0) > initialInputTop
        }
        val composeDelta = (assertNotNull(inputBounds).top - initialComposeTop) / density.density
        val domDelta = assertNotNull(backingInput()).getBoundingClientRect().top - initialInputTop
        assertEquals(composeDelta.toDouble(), domDelta, 1.01)
    }

    @Test
    fun removingViewportDisposesPreferencesAndOtherWindowsKeepObserving() = runApplicationTest {
        val media = testMediaQuery()
        val firstProbe = TestFontSizeProbe()
        val secondProbe = TestFontSizeProbe()
        var firstDensity = Density(1f)
        var secondDensity = Density(1f)
        var secondWindow: ComposeWindow? = null
        createComposeWindow(configure = {
            fontFallbackUrl = null
            webPreferencesObserverFactory = { WebPreferencesObserver(it, firstProbe, media.media) }
        }) {
            firstDensity = LocalDensity.current
        }
        val secondContainer = document.createElement("div") as HTMLElement
        secondContainer.style.cssText = "position: fixed; width: 100px; height: 100px;"
        document.body!!.appendChild(secondContainer)
        try {
            ComposeViewport(secondContainer, configure = {
                isA11YEnabled = false
                fontFallbackUrl = null
                webPreferencesObserverFactory = { WebPreferencesObserver(it, secondProbe, media.media) }
            }) {
                secondWindow = LocalComposeWindow.current
                secondDensity = LocalDensity.current
            }
            awaitUntil { secondWindow != null }
            assertEquals(2, media.listenerCount)
            val component = assertNotNull(getContainer().children[0])
            getContainer().removeChild(component)
            assertEquals(1, firstProbe.disposeCount)
            assertEquals(1, media.listenerCount)

            firstProbe.changeTo(24f)
            secondProbe.changeTo(24f)
            media.changeTo(true)
            awaitUntil { secondDensity.fontScale == 1.5f }
            assertEquals(1f, firstDensity.fontScale)
            assertEquals(0, secondProbe.disposeCount)
        } finally {
            secondWindow?.dispose()
            secondContainer.parentNode?.removeChild(secondContainer)
        }
        assertEquals(0, media.listenerCount)
        assertEquals(1, secondProbe.disposeCount)
    }

    @Test
    fun removingViewportAlsoRemovesTheRealFontProbe() = runApplicationTest {
        createComposeWindow(configure = { fontFallbackUrl = null }) {}
        val root = getShadowRoot()
        assertNotNull(root.querySelector("[data-compose-font-size-probe]"))
        val component = assertNotNull(getContainer().children[0])
        getContainer().removeChild(component)
        assertNull(root.querySelector("[data-compose-font-size-probe]"))
    }

    @Test
    fun fontScaleIsIndependentOfDprAndPinchZoom() {
        assertEquals(Density(1f, 1.25f), webDensity(1f, 1f, 1.25f))
        assertEquals(Density(2f, 1.25f), webDensity(2f, 1f, 1.25f))
        assertEquals(Density(3f, 1.25f), webDensity(2f, 1.5f, 1.25f))
        // Keep the existing viewport-scale buffer cap, not a cap on font preferences.
        assertEquals(Density(4f, 20f), webDensity(2f, 4f, 20f))
        assertEquals(Density(2f, 20f), webDensity(2f, 0.5f, 20f))
    }

    private fun backingInput() =
        getShadowRoot().querySelector("div.compose-backing-field") as? HTMLElement

    private suspend fun WebApplicationScope.awaitMirrorBounds(
        tag: String,
        bounds: Rect,
        density: Density
    ) {
        awaitUntil {
            val element = getShadowRoot().getElementById(tag) as? HTMLElement
            val actual = element?.getBoundingClientRect()
            actual != null && abs(actual.width - bounds.width / density.density) < 1.0 &&
                abs(actual.height - bounds.height / density.density) < 1.0
        }
        val actual = (getShadowRoot().getElementById(tag) as HTMLElement).getBoundingClientRect()
        val canvas = getCanvas().getBoundingClientRect()
        assertEquals(canvas.left + bounds.left / density.density, actual.left, 1.0)
        assertEquals(canvas.top + bounds.top / density.density, actual.top, 1.0)
        assertTrue(actual.width > 0 && actual.height > 0)
    }

    private suspend fun WebApplicationScope.awaitUntil(condition: () -> Boolean) {
        // Use real browser frames for DOM/layout work, not runTest's virtual timeout clock.
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (!condition()) {
                    awaitAnimationFrame()
                    awaitIdle()
                }
            }
        }
    }
}
