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

@file:OptIn(ExperimentalMediaQueryApi::class, ExperimentalWasmJsInterop::class)

package androidx.compose.ui.platform

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope.FoldOrientation
import androidx.compose.ui.UiMediaScope.FoldState
import androidx.compose.ui.UiMediaScope.KeyboardKind
import androidx.compose.ui.UiMediaScope.PointerPrecision
import androidx.compose.ui.UiMediaScope.ViewingDistance
import androidx.compose.ui.UiMediaScope.WindowFold
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest

class WebMediaScopeTest {
    @Test
    fun windowSizeTracksWindowInfo() {
        val windowInfo = WindowInfoImpl().apply { containerDpSize = DpSize(400.dp, 600.dp) }
        withMediaScope(windowInfo = windowInfo) { scope, _ ->
            val size by derivedStateOf { DpSize(scope.windowWidth, scope.windowHeight) }
            assertEquals(DpSize(400.dp, 600.dp), size)

            windowInfo.containerDpSize = DpSize(800.dp, 500.dp)
            assertEquals(DpSize(800.dp, 500.dp), size)
        }
    }

    @Test
    fun pointerPrecisionUsesMostPreciseAvailableDevice() = withMediaScope { scope, browser ->
        val precision by derivedStateOf { scope.pointerPrecision }
        assertEquals(PointerPrecision.None, precision)

        browser.setMediaQuery("(any-pointer: coarse)", true)
        assertEquals(PointerPrecision.Coarse, precision)
        browser.setMediaQuery("(any-pointer: fine)", true)
        assertEquals(PointerPrecision.Fine, precision)
        browser.setMediaQuery("(any-pointer: fine)", false)
        assertEquals(PointerPrecision.Coarse, precision)
        browser.setMediaQuery("(any-pointer: coarse)", false)
        assertEquals(PointerPrecision.None, precision)
    }

    @Test
    fun postureTracksSegmentsAndFoldState() = withMediaScope { scope, browser ->
        val posture by derivedStateOf { scope.windowPosture }
        assertTrue(posture.isFlat)
        assertTrue(posture.folds.isEmpty())

        browser.setMediaQuery("(horizontal-viewport-segments: 2)", true)
        assertEquals(listOf(WindowFold(FoldState.Flat, FoldOrientation.Vertical)), posture.folds)

        browser.setMediaQuery("(device-posture: folded)", true)
        assertFalse(posture.isFlat)
        assertFalse(posture.isTabletop)
        assertEquals(
            listOf(WindowFold(FoldState.HalfOpened, FoldOrientation.Vertical)),
            posture.folds,
        )

        browser.setMediaQuery("(horizontal-viewport-segments: 2)", false)
        browser.setMediaQuery("(vertical-viewport-segments: 2)", true)
        assertTrue(posture.isTabletop)

        browser.setMediaQuery("(device-posture: folded)", false)
        assertTrue(posture.isFlat)
        assertFalse(posture.isTabletop)
    }

    @Test
    fun keyboardVisibilityOverridesDesktopEstimate() = withMediaScope { scope, browser ->
        val keyboard by derivedStateOf { scope.keyboardKind }
        assertEquals(KeyboardKind.Physical, keyboard)
        browser.setKeyboardVisible(true)
        assertEquals(KeyboardKind.Virtual, keyboard)
        browser.setKeyboardVisible(false)
        assertEquals(KeyboardKind.Physical, keyboard)
    }

    @Test
    fun mobileUserAgentOnlyReportsVisibleVirtualKeyboard() {
        for (userAgent in listOf("Android Linux Mobile", "iPhone", "iPad", "iPod")) {
            withMediaScope(userAgent = userAgent) { scope, browser ->
                assertEquals(KeyboardKind.None, scope.keyboardKind)
                browser.setKeyboardVisible(true)
                assertEquals(KeyboardKind.Virtual, scope.keyboardKind)
                browser.setKeyboardVisible(false)
                assertEquals(KeyboardKind.None, scope.keyboardKind)
            }
        }
    }

    @Test
    fun ipadDesktopUserAgentDoesNotImplyPhysicalKeyboard() =
        withMediaScope(
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15)",
            platform = "MacIntel",
            maxTouchPoints = 5,
        ) { scope, _ ->
            assertEquals(KeyboardKind.None, scope.keyboardKind)
        }

    @Test
    fun unlabeledInputsAreDetectedAndUpdatedWithoutPermissionRequests() = runTest {
        withMediaScope { scope, browser ->
            val capabilities by derivedStateOf { scope.hasMicrophone to scope.hasCamera }
            assertEquals(false to false, capabilities)

            browser.resolveEnumeration(0, microphone = true, camera = false)
            flushPromises()
            assertEquals(true to false, capabilities)

            browser.dispatchDeviceChange()
            browser.resolveEnumeration(1, microphone = false, camera = true)
            flushPromises()
            assertEquals(false to true, capabilities)

            browser.dispatchDeviceChange()
            browser.resolveEnumeration(2, microphone = false, camera = false)
            flushPromises()
            assertEquals(false to false, capabilities)
            assertEquals(0, browser.permissionRequests())
        }
    }

    @Test
    fun latestDeviceEnumerationWins() = runTest {
        withMediaScope { scope, browser ->
            browser.dispatchDeviceChange()
            browser.resolveEnumeration(1, microphone = true, camera = false)
            flushPromises()

            browser.resolveEnumeration(0, microphone = false, camera = true)
            flushPromises()
            assertTrue(scope.hasMicrophone)
            assertFalse(scope.hasCamera)
        }
    }

    @Test
    fun enumerationFailureUsesSafeDefaultsAndCanRecover() = runTest {
        withMediaScope { scope, browser ->
            browser.rejectEnumeration(0)
            flushPromises()
            assertFalse(scope.hasMicrophone)
            assertFalse(scope.hasCamera)

            browser.dispatchDeviceChange()
            browser.resolveEnumeration(1, microphone = true, camera = true)
            flushPromises()
            assertTrue(scope.hasMicrophone)
            assertTrue(scope.hasCamera)

            browser.dispatchDeviceChange()
            browser.rejectEnumeration(2)
            flushPromises()
            assertFalse(scope.hasMicrophone)
            assertFalse(scope.hasCamera)
        }
    }

    @Test
    fun visibilityChangeRefreshesDevices() = withMediaScope { _, browser ->
        assertEquals(1, browser.enumerationCount())
        browser.dispatchVisibilityChange()
        assertEquals(2, browser.enumerationCount())
    }

    @Test
    fun disposalStopsListenersAndIgnoresPendingEnumeration() = runTest {
        withMediaScope { scope, browser ->
            browser.setMediaQuery("(any-pointer: fine)", true)
            browser.setKeyboardVisible(true)
            browser.resolveEnumeration(0, microphone = true, camera = false)
            flushPromises()
            browser.dispatchDeviceChange()

            scope.dispose()
            browser.setMediaQuery("(any-pointer: fine)", false)
            browser.setMediaQuery("(vertical-viewport-segments: 2)", true)
            browser.setMediaQuery("(device-posture: folded)", true)
            browser.setKeyboardVisible(false)
            browser.dispatchDeviceChange()
            browser.dispatchVisibilityChange()
            browser.resolveEnumeration(1, microphone = false, camera = true)
            flushPromises()

            assertEquals(PointerPrecision.Fine, scope.pointerPrecision)
            assertEquals(KeyboardKind.Virtual, scope.keyboardKind)
            assertTrue(scope.windowPosture.folds.isEmpty())
            assertTrue(scope.hasMicrophone)
            assertFalse(scope.hasCamera)
            assertEquals(2, browser.enumerationCount())
        }
    }

    @Test
    fun unsupportedBrowserApisUseFallbacks() =
        withMediaScope(apisSupported = false) { scope, _ ->
            assertEquals(PointerPrecision.None, scope.pointerPrecision)
            assertEquals(KeyboardKind.Physical, scope.keyboardKind)
            assertTrue(scope.windowPosture.isFlat)
            assertFalse(scope.hasMicrophone)
            assertFalse(scope.hasCamera)
            assertEquals(ViewingDistance.Near, scope.viewingDistance)
        }
}

private inline fun withMediaScope(
    windowInfo: WindowInfo = WindowInfoImpl(),
    userAgent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    platform: String = "Win32",
    maxTouchPoints: Int = 0,
    apisSupported: Boolean = true,
    block: (WebMediaScope, MediaScopeBrowserMocks) -> Unit,
) {
    val browser = installBrowserMocks(userAgent, platform, maxTouchPoints, apisSupported)
    try {
        val scope = WebMediaScope(windowInfo)
        try {
            block(scope, browser)
        } finally {
            scope.dispose()
        }
    } finally {
        browser.restore()
    }
}

private external interface MediaScopeBrowserMocks : JsAny {
    fun setMediaQuery(query: String, matches: Boolean)

    fun setKeyboardVisible(visible: Boolean)

    fun dispatchDeviceChange()

    fun dispatchVisibilityChange()

    fun resolveEnumeration(index: Int, microphone: Boolean, camera: Boolean)

    fun rejectEnumeration(index: Int)

    fun enumerationCount(): Int

    fun permissionRequests(): Int

    fun restore()
}

private suspend fun flushPromises() {
    resolvedPromise().await<JsAny?>()
}

// language=js
private fun resolvedPromise(): Promise<JsAny?> = js("Promise.resolve(null)")

// language=js
private fun installBrowserMocks(
    userAgent: String,
    platform: String,
    maxTouchPoints: Int,
    apisSupported: Boolean,
): MediaScopeBrowserMocks =
    js(
        """(function() {
    const originals = [];
    function replace(target, name, value) {
        originals.push([target, name, Object.getOwnPropertyDescriptor(target, name)]);
        Object.defineProperty(target, name, { configurable: true, value: value });
    }
    const nativeMatchMedia = window.matchMedia.bind(window);
    const queries = new Map();
    replace(window, 'matchMedia', apisSupported ? function(query) {
        if (!queries.has(query)) {
            const media = nativeMatchMedia(query);
            Object.defineProperty(media, 'matches', { configurable: true, writable: true, value: false });
            queries.set(query, media);
        }
        return queries.get(query);
    } : undefined);
    replace(navigator, 'userAgent', userAgent);
    replace(navigator, 'platform', platform);
    replace(navigator, 'maxTouchPoints', maxTouchPoints);
    replace(document, 'visibilityState', 'visible');

    const keyboard = new EventTarget();
    keyboard.boundingRect = { height: 0 };
    replace(navigator, 'virtualKeyboard', apisSupported ? keyboard : undefined);

    const enumerations = [];
    let permissionRequests = 0;
    const devices = new EventTarget();
    devices.enumerateDevices = function() {
        return new Promise(function(resolve, reject) {
            enumerations.push({ resolve: resolve, reject: reject });
        });
    };
    devices.getUserMedia = function() {
        permissionRequests++;
        throw new Error('Device detection must not request permission');
    };
    replace(navigator, 'mediaDevices', apisSupported ? devices : undefined);

    return {
        setMediaQuery: function(query, matches) {
            const media = queries.get(query);
            media.matches = matches;
            media.dispatchEvent(new MediaQueryListEvent('change', { matches: matches, media: query }));
        },
        setKeyboardVisible: function(visible) {
            keyboard.boundingRect.height = visible ? 250 : 0;
            keyboard.dispatchEvent(new Event('geometrychange'));
        },
        dispatchDeviceChange: function() { devices.dispatchEvent(new Event('devicechange')); },
        dispatchVisibilityChange: function() {
            document.dispatchEvent(new Event('visibilitychange'));
        },
        resolveEnumeration: function(index, microphone, camera) {
            const inputs = [{ kind: 'audiooutput', label: '', deviceId: '', groupId: '' }];
            if (microphone) inputs.push({ kind: 'audioinput', label: '', deviceId: '', groupId: '' });
            if (camera) inputs.push({ kind: 'videoinput', label: '', deviceId: '', groupId: '' });
            enumerations[index].resolve(inputs);
        },
        rejectEnumeration: function(index) {
            enumerations[index].reject(new Error('Device information is unavailable'));
        },
        enumerationCount: function() { return enumerations.length; },
        permissionRequests: function() { return permissionRequests; },
        restore: function() {
            originals.reverse().forEach(function(original) {
                if (original[2]) Object.defineProperty(original[0], original[1], original[2]);
                else delete original[0][original[1]];
            });
        }
    };
})()"""
    )
