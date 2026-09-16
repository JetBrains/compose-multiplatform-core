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

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.OnCanvasTests
import androidx.compose.ui.platform.LocalPlatformWindowInsets
import androidx.compose.ui.platform.PlatformWindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window
import org.w3c.dom.events.Event

@OptIn(
    ExperimentalWasmJsInterop::class,
    InternalComposeUiApi::class,
    ExperimentalComposeUiApi::class
)
class WebWindowInsetsTest : OnCanvasTests {

    @AfterTest
    fun cleanup() {
        cleanupMocks()
    }

    private fun mockBrowserEnvironment(
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
        left: Int = 0,
        viewportFitCover: Boolean = true,
        canvasTop: Int = 0,
        canvasLeft: Int = 0,
        canvasRight: Int = 1024,
        canvasBottom: Int = 768,
        innerWidth: Int = 1024,
        innerHeight: Int = 768
    ) {
        mockBrowserEnvironmentInternal(
            top,
            right,
            bottom,
            left,
            viewportFitCover,
            canvasTop,
            canvasLeft,
            canvasRight,
            canvasBottom,
            innerWidth,
            innerHeight
        )
    }

    private fun cleanupMocks() {
        cleanupMocksInternal()
    }

    @Test
    fun testBasicSafeArea() = runApplicationTest {
        mockBrowserEnvironment(top = 10, right = 20, bottom = 30, left = 40)

        var capturedInsets: PlatformWindowInsets? = null
        createComposeWindow(
            configure = { enableBrowserWindowInsets = true }
        ) {
            capturedInsets = LocalPlatformWindowInsets.current
        }

        awaitIdle()

        val insets = capturedInsets ?: error("Insets not captured")
        val density = Density(window.devicePixelRatio.toFloat())

        with(density) {
            assertEquals(10.dp.roundToPx(), insets.statusBars.top, "Status bars top")
            assertEquals(30.dp.roundToPx(), insets.navigationBars.bottom, "Navigation bars bottom")

            assertEquals(40.dp.roundToPx(), insets.displayCutout.left, "Display cutout left")
            assertEquals(10.dp.roundToPx(), insets.displayCutout.top, "Display cutout top")
            assertEquals(20.dp.roundToPx(), insets.displayCutout.right, "Display cutout right")
            assertEquals(30.dp.roundToPx(), insets.displayCutout.bottom, "Display cutout bottom")
        }
    }

    @Test
    fun testDisabledBrowserWindowInsets() = runApplicationTest {
        mockBrowserEnvironment(top = 10, right = 20, bottom = 30, left = 40)

        var capturedInsets: PlatformWindowInsets? = null
        createComposeWindow(
            configure = { enableBrowserWindowInsets = false }
        ) {
            capturedInsets = LocalPlatformWindowInsets.current
        }

        awaitIdle()

        val insets = capturedInsets ?: error("Insets not captured")
        assertEquals(0, insets.statusBars.top, "Status bars top should be 0")
        assertEquals(0, insets.navigationBars.bottom, "Navigation bars bottom should be 0")
    }

    @Test
    fun testDensityConversion() = runApplicationTest {
        mockBrowserEnvironment(top = 15)

        var capturedInsets: PlatformWindowInsets? = null
        createComposeWindow(
            configure = { enableBrowserWindowInsets = true }
        ) {
            capturedInsets = LocalPlatformWindowInsets.current
        }

        awaitIdle()

        val insets = capturedInsets ?: error("Insets not captured")
        val density = Density(window.devicePixelRatio.toFloat())

        with(density) {
            assertEquals(15.dp.roundToPx(), insets.statusBars.top, "Density conversion check")
        }
    }

    @Test
    fun testSafeAreaWithCanvasOffset() = runApplicationTest {
        // Safe area top is 20px, but canvas starts at 15px from the top.
        // Resulting inset should be 20 - 15 = 5px.
        mockBrowserEnvironment(
            top = 20,
            canvasTop = 15,
            innerHeight = 100,
            canvasBottom = 100
        )

        var capturedInsets: PlatformWindowInsets? = null
        createComposeWindow(
            configure = { enableBrowserWindowInsets = true }
        ) {
            capturedInsets = LocalPlatformWindowInsets.current
        }

        awaitIdle()

        val insets = capturedInsets ?: error("Insets not captured")
        val density = Density(window.devicePixelRatio.toFloat())

        with(density) {
            assertEquals(
                5.dp.roundToPx(),
                insets.statusBars.top,
                "Top inset should be clipped by canvas offset"
            )
        }
    }

    @Test
    fun testDynamicUpdateOnResize() = runApplicationTest {
        mockBrowserEnvironment(top = 10)

        var capturedInsets: PlatformWindowInsets? = null
        createComposeWindow(
            configure = { enableBrowserWindowInsets = true }
        ) {
            capturedInsets = LocalPlatformWindowInsets.current
        }

        awaitIdle()

        val insets = capturedInsets ?: error("Insets not captured")
        val density = Density(window.devicePixelRatio.toFloat())

        with(density) {
            assertEquals(10.dp.roundToPx(), insets.statusBars.top, "Initial top inset")
        }

        // Update mock and trigger resize
        mockBrowserEnvironment(top = 50)
        window.dispatchEvent(Event("resize"))

        awaitIdle()

        with(density) {
            assertEquals(50.dp.roundToPx(), insets.statusBars.top, "Updated top inset after resize")
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun mockBrowserEnvironmentInternal(
    safeAreaTop: Int,
    safeAreaRight: Int,
    safeAreaBottom: Int,
    safeAreaLeft: Int,
    hasViewportFitCover: Boolean,
    canvasTop: Int,
    canvasLeft: Int,
    canvasRight: Int,
    canvasBottom: Int,
    innerWidth: Int,
    innerHeight: Int
): Unit = js(
    """(function() {
        window._mockValues = {
            top: safeAreaTop,
            right: safeAreaRight,
            bottom: safeAreaBottom,
            left: safeAreaLeft,
            viewportFitCover: hasViewportFitCover,
            canvasTop: canvasTop,
            canvasLeft: canvasLeft,
            canvasRight: canvasRight,
            canvasBottom: canvasBottom,
            innerWidth: innerWidth,
            innerHeight: innerHeight
        };

        if (!window._oldGetComputedStyle) {
            window._oldGetComputedStyle = window.getComputedStyle;
            window.getComputedStyle = function(el) {
                var style = window._oldGetComputedStyle(el);
                if (el === document.documentElement) {
                    return {
                        getPropertyValue: function(prop) {
                            if (prop === '--cmp-safe-top') return window._mockValues.top + 'px';
                            if (prop === '--cmp-safe-right') return window._mockValues.right + 'px';
                            if (prop === '--cmp-safe-bottom') return window._mockValues.bottom + 'px';
                            if (prop === '--cmp-safe-left') return window._mockValues.left + 'px';
                            return style.getPropertyValue(prop);
                        }
                    };
                }
                return style;
            };
        }

        if (!window._oldQuerySelector) {
            window._oldQuerySelector = document.querySelector;
            document.querySelector = function(selector) {
                if (selector === 'meta[name=viewport]') {
                    return {
                        getAttribute: function(name) {
                            if (name === 'content') {
                                return window._mockValues.viewportFitCover ? 'viewport-fit=cover' : '';
                            }
                            return null;
                        }
                    };
                }
                return window._oldQuerySelector.call(document, selector);
            };
        }

        if (!window._oldInnerWidth) {
            window._oldInnerWidth = Object.getOwnPropertyDescriptor(window, 'innerWidth') || { value: window.innerWidth };
            window._oldInnerHeight = Object.getOwnPropertyDescriptor(window, 'innerHeight') || { value: window.innerHeight };
            Object.defineProperty(window, 'innerWidth', { 
                get: function() { return window._mockValues.innerWidth; },
                configurable: true 
            });
            Object.defineProperty(window, 'innerHeight', { 
                get: function() { return window._mockValues.innerHeight; },
                configurable: true 
            });
        }

        if (!window._oldGetBoundingClientRect) {
            window._oldGetBoundingClientRect = Element.prototype.getBoundingClientRect;
            Element.prototype.getBoundingClientRect = function() {
                if (this.nodeName === 'CANVAS' || this.id === 'canvasApp') {
                     return {
                        top: window._mockValues.canvasTop,
                        left: window._mockValues.canvasLeft,
                        right: window._mockValues.canvasRight,
                        bottom: window._mockValues.canvasBottom,
                        width: window._mockValues.canvasRight - window._mockValues.canvasLeft,
                        height: window._mockValues.canvasBottom - window._mockValues.canvasTop,
                        x: window._mockValues.canvasLeft,
                        y: window._mockValues.canvasTop
                    };
                }
                return window._oldGetBoundingClientRect.call(this);
            };
        }
    })()"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun cleanupMocksInternal(): Unit = js(
    """(function() {
        if (window._oldGetComputedStyle) {
            window.getComputedStyle = window._oldGetComputedStyle;
            delete window._oldGetComputedStyle;
        }
        if (window._oldQuerySelector) {
            document.querySelector = window._oldQuerySelector;
            delete window._oldQuerySelector;
        }
        if (window._oldInnerWidth) {
            if (window._oldInnerWidth.get) {
                Object.defineProperty(window, 'innerWidth', window._oldInnerWidth);
                Object.defineProperty(window, 'innerHeight', window._oldInnerHeight);
            } else {
                window.innerWidth = window._oldInnerWidth.value;
                window.innerHeight = window._oldInnerHeight.value;
            }
            delete window._oldInnerWidth;
            delete window._oldInnerHeight;
        }
        if (window._oldGetBoundingClientRect) {
            Element.prototype.getBoundingClientRect = window._oldGetBoundingClientRect;
            delete window._oldGetBoundingClientRect;
        }
        delete window._mockValues;
    })()"""
)
