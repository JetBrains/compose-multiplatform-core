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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.skiko.SystemTheme
import org.w3c.dom.HTMLElement
import org.w3c.dom.OPEN
import org.w3c.dom.ShadowRootInit
import org.w3c.dom.ShadowRootMode
import org.w3c.dom.events.Event

class WebPreferencesObserverTest {
    @Test
    fun readsPreferencesBeforeObservationStarts() {
        val probe = TestFontSizeProbe(20f)
        val media = testMediaQuery(initialMatches = true)
        val observer = observer(probe, media)
        try {
            assertEquals(1.25f, observer.fontScale)
            assertEquals(0f, observer.motionDurationScale.scaleFactor)
            assertEquals(0, probe.observeCount)

            val changes = mutableListOf<Float>()
            // A ResizeObserver implementation may deliver a callback during registration.
            probe.onObserve = { probe.changeTo(24f) }
            observer.start { changes.add(it) }
            assertEquals(listOf(1.5f), changes)
            assertEquals(1.5f, observer.fontScale)
        } finally {
            observer.dispose()
        }
    }

    @Test
    fun defaultAndUnsupportedPreferencesRetainNormalBehavior() {
        // A non-matching feature is neutral even if matchMedia is available.
        for (media in listOf(null, testMediaQuery())) {
            val observer = observer(TestFontSizeProbe(), media)
            try {
                assertEquals(1f, observer.fontScale)
                assertEquals(1f, observer.motionDurationScale.scaleFactor)
            } finally {
                observer.dispose()
            }
        }
    }

    @Test
    fun ignoresInvalidAndDuplicateMeasurementsWithoutCappingLargeFonts() {
        val probe = TestFontSizeProbe(Float.NaN)
        val observer = observer(probe)
        try {
            assertEquals(1f, observer.fontScale)
            val changes = mutableListOf<Float>()
            observer.start { changes.add(it) }
            probe.changeTo(20f)
            probe.changeTo(20f)
            for (invalid in listOf(null, 0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                probe.changeTo(invalid)
                assertEquals(1.25f, observer.fontScale)
            }
            probe.changeTo(320f)
            assertEquals(20f, observer.fontScale)
            assertEquals(listOf(1.25f, 20f), changes)
        } finally {
            observer.dispose()
        }
    }

    @Test
    fun motionChangesUpdateTheSameScaleObject() {
        val media = testMediaQuery()
        val observer = observer(TestFontSizeProbe(), media)
        try {
            val scale = observer.motionDurationScale
            assertEquals(1f, scale.scaleFactor)
            media.changeTo(true)
            assertEquals(0f, scale.scaleFactor)
            media.changeTo(false)
            assertEquals(1f, scale.scaleFactor)
        } finally {
            observer.dispose()
        }
    }

    @Test
    fun eachObserverDisposesIndependentlyAndIgnoresQueuedCallbacks() {
        val media = testMediaQuery()
        val firstProbe = TestFontSizeProbe()
        val secondProbe = TestFontSizeProbe()
        val first = observer(firstProbe, media)
        val second = observer(secondProbe, media)
        try {
            val firstChanges = mutableListOf<Float>()
            val secondChanges = mutableListOf<Float>()
            first.start { firstChanges.add(it) }
            second.start { secondChanges.add(it) }
            assertEquals(2, media.listenerCount)
            first.dispose()
            first.dispose()
            assertEquals(1, firstProbe.disposeCount)
            assertEquals(1, media.listenerCount)

            media.sendQueuedChange(true)
            firstProbe.changeTo(24f)
            assertEquals(1f, first.fontScale)
            assertEquals(1f, first.motionDurationScale.scaleFactor)
            assertTrue(firstChanges.isEmpty())

            media.changeTo(true)
            secondProbe.changeTo(24f)
            assertEquals(0f, second.motionDurationScale.scaleFactor)
            assertEquals(listOf(1.5f), secondChanges)
        } finally {
            first.dispose()
            second.dispose()
        }
        assertEquals(0, media.listenerCount)
        assertEquals(1, secondProbe.disposeCount)
    }

    @Test
    fun disposalBeforeStartDoesNotRegisterFontCallbacks() {
        val probe = TestFontSizeProbe()
        val observer = observer(probe)
        observer.dispose()
        observer.start { error("Disposed observer called back") }
        assertEquals(0, probe.observeCount)
    }

    @Test
    fun supportsLegacyMediaListenersAndMissingListenerApis() {
        for (legacy in listOf(false, true)) {
            val media = testMediaQuery(legacy = legacy)
            val observer = observer(TestFontSizeProbe(), media)
            try {
                assertEquals(1, media.listenerCount)
                media.changeTo(true)
                assertEquals(0f, observer.motionDurationScale.scaleFactor)
            } finally {
                observer.dispose()
            }
            assertEquals(0, media.listenerCount)
        }

        val media = testMediaQuery(initialMatches = true, listenersSupported = false)
        val observer = observer(TestFontSizeProbe(), media)
        try {
            assertEquals(0f, observer.motionDurationScale.scaleFactor)
        } finally {
            observer.dispose()
        }
    }

    @Test
    fun darkModeStillReadsChangesAndDisposesIncludingLegacyListeners() {
        for (legacy in listOf(false, true)) {
            val media = testMediaQuery(initialMatches = true, legacy = legacy)
            val observer = getSystemThemeObserver(media.media)
            try {
                assertEquals(SystemTheme.DARK, observer.currentSystemTheme.value)
                media.changeTo(false)
                assertEquals(SystemTheme.LIGHT, observer.currentSystemTheme.value)
                media.changeTo(true)
                assertEquals(SystemTheme.DARK, observer.currentSystemTheme.value)
            } finally {
                observer.dispose()
                observer.dispose()
            }
            media.sendQueuedChange(false)
            assertEquals(SystemTheme.DARK, observer.currentSystemTheme.value)
            assertEquals(0, media.listenerCount)
        }
        val unsupported = getSystemThemeObserver(null)
        try {
            assertEquals(SystemTheme.UNKNOWN, unsupported.currentSystemTheme.value)
        } finally {
            unsupported.dispose()
        }
    }

    @Test
    fun attachedProbeIsIsolatedHiddenAndDoesNotEnlargeScrollArea() {
        val host = document.createElement("div") as HTMLElement
        host.style.cssText = "position: relative; width: 1px; height: 1px; font: 40px serif;"
        document.body!!.appendChild(host)
        val root = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))
        val html = document.documentElement as HTMLElement
        val originalRootStyle = html.style.cssText
        val scrollWidth = host.scrollWidth
        val scrollHeight = host.scrollHeight
        val documentScrollWidth = html.scrollWidth
        val documentScrollHeight = html.scrollHeight
        val probe = DomFontSizeProbe(root)
        try {
            val initial = assertNotNull(probe.readFontSize())
            assertTrue(initial > 0f)
            val element = assertNotNull(
                root.querySelector("[data-compose-font-size-probe]") as? HTMLElement
            )
            assertEquals("true", element.getAttribute("aria-hidden"))
            assertFalse(element.hasAttribute("tabindex"))
            assertEquals("", element.textContent)
            assertEquals("hidden", window.getComputedStyle(element).visibility)
            assertEquals("none", window.getComputedStyle(element).getPropertyValue("pointer-events"))
            assertEquals(initial.toDouble(), element.getBoundingClientRect().width, 0.1)
            assertEquals(scrollWidth, host.scrollWidth)
            assertEquals(scrollHeight, host.scrollHeight)
            assertEquals(documentScrollWidth, html.scrollWidth)
            assertEquals(documentScrollHeight, html.scrollHeight)

            html.style.fontSize = "10px"
            assertEquals(initial, probe.readFontSize())
            html.style.fontSize = "200%"
            assertEquals(initial, probe.readFontSize())
        } finally {
            html.style.cssText = originalRootStyle
            probe.dispose()
            probe.dispose()
            assertEquals(0, root.childNodes.length)
            host.parentNode?.removeChild(host)
        }
    }

    @Test
    fun probeRefreshesOnFocusWithoutResizeObserverAndStopsOnDisposal() {
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val root = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))
        // Null represents an engine without ResizeObserver; no browser globals are patched.
        val probe = DomFontSizeProbe(root, observeResize = { _, _ -> null })
        val observer = WebPreferencesObserver(root, probe, reducedMotionQuery = null)
        try {
            val changes = mutableListOf<Float>()
            observer.start { changes.add(it) }
            // Synthetic CSS changes exercise refresh/propagation, NOT default-font detection.
            val element = root.querySelector("[data-compose-font-size-probe]") as HTMLElement
            element.style.font = "48px sans-serif"
            window.dispatchEvent(Event("focus"))
            assertEquals(3f, observer.fontScale)
            observer.dispose()
            element.style.font = "64px sans-serif"
            window.dispatchEvent(Event("focus"))
            assertEquals(listOf(3f), changes)
        } finally {
            observer.dispose()
            host.parentNode?.removeChild(host)
        }
    }

    @Test
    fun resizeAndVisibilityRefreshesAreReleasedWithTheProbe() {
        val host = document.createElement("div") as HTMLElement
        document.body!!.appendChild(host)
        val root = host.attachShadow(ShadowRootInit(ShadowRootMode.OPEN))
        var resizeCallback: (() -> Unit)? = null
        var disconnectCount = 0
        var isVisible = true
        val probe = DomFontSizeProbe(
            root,
            observeResize = { _, onChange ->
                resizeCallback = onChange
                val disconnect: () -> Unit = { disconnectCount++ }
                disconnect
            },
            isDocumentVisible = { isVisible }
        )
        val observer = WebPreferencesObserver(root, probe, reducedMotionQuery = null)
        try {
            val changes = mutableListOf<Float>()
            observer.start { changes.add(it) }
            val element = root.querySelector("[data-compose-font-size-probe]") as HTMLElement
            element.style.font = "20px sans-serif"
            assertNotNull(resizeCallback).invoke()
            assertEquals(1.25f, observer.fontScale)

            isVisible = false
            element.style.font = "24px sans-serif"
            document.dispatchEvent(Event("visibilitychange"))
            assertEquals(1.25f, observer.fontScale)
            isVisible = true
            document.dispatchEvent(Event("visibilitychange"))
            assertEquals(1.5f, observer.fontScale)

            observer.dispose()
            observer.dispose()
            assertEquals(1, disconnectCount)
            assertEquals(0, root.childNodes.length)
            element.style.font = "32px sans-serif"
            resizeCallback?.invoke()
            document.dispatchEvent(Event("visibilitychange"))
            assertEquals(listOf(1.25f, 1.5f), changes)
        } finally {
            observer.dispose()
            host.parentNode?.removeChild(host)
        }
    }

    private fun observer(
        probe: TestFontSizeProbe,
        media: TestMediaQuery? = null
    ) = WebPreferencesObserver(document.createElement("div"), probe, media?.media)
}
