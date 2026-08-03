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

package androidx.compose.ui.desktop.wasm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.desktop.Application
import androidx.compose.ui.desktop.ApplicationSession
import androidx.compose.ui.desktop.LightweightWindowId
import androidx.compose.ui.desktop.Screen
import androidx.compose.ui.desktop.Window
import androidx.compose.ui.desktop.WindowCloseRequestReason
import androidx.compose.ui.desktop.activateApplication
import androidx.compose.ui.desktop.deactivateApplication
import androidx.compose.ui.desktop.removeApplication
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.DefaultHapticFeedback
import androidx.compose.ui.platform.DefaultInputModeManager
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.createPlatformClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import kotlin.js.js
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement

/**
 * The browser-canvas [Application]. A single window is hosted in the container element selected by
 * `window.__airConfig['target.window.element']`, falling back to the single element with the
 * `air-window` class.
 *
 * The FQN and the [initialize]/[current]/[activationFlow] surface deliberately match Noria's
 * implementation of the same facade so that product code compiles against either runtime.
 */
object WasmJsApplication : Application, Clipboard by createPlatformClipboard() {
    val activationFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var shutdown = false
    private var initialized = false

    fun initialize(
        uriHandler: UriHandler = WasmJsUriHandler(),
        customQuit: (() -> Boolean)? = null,
    ): WasmJsApplication {
        check(!shutdown) {
            "WasmJsApplication has already been shut down and cannot be reinitialized in the same page"
        }
        initialized = true
        this.uriHandler = uriHandler
        this.customQuit = customQuit
        activateApplication(this)
        return this
    }

    val current: WasmJsApplication
        get() {
            check(initialized && !shutdown) { "WasmJsApplication has not been initialized" }
            return this
        }

    private var uriHandler: UriHandler = WasmJsUriHandler()
    private var customQuit: (() -> Boolean)? = null

    override fun openUri(uri: String) {
        uriHandler.openUri(uri)
    }

    override fun close() {
        MainScope().launch { stopAndJoin() }
    }

    override val systemTheme: SystemTheme
        get() = if (window.matchMedia("(prefers-color-scheme: dark)").matches) {
            SystemTheme.Dark
        } else {
            SystemTheme.Light
        }

    override var windows: Map<LightweightWindowId, WasmJsWindow> by mutableStateOf(emptyMap())
        internal set

    override fun createWindow(
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window {
        check(windows.isEmpty()) { "Multi-window setups are not supported in the browser" }
        val rootElement = defaultWindowContainer()
        val canvas = rootElement.hostWindowCanvas()
        return WasmJsWindow(this, rootElement, canvas, session, onCloseRequest)
    }

    override fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId) {
    }

    override fun reuseWindow(
        id: LightweightWindowId,
        session: ApplicationSession,
        onCloseRequest: (WindowCloseRequestReason) -> Unit,
    ): Window? {
        val existingWindow = windows[id] ?: return null
        existingWindow.disposeForReuse()
        return WasmJsWindow(this, existingWindow.rootElement, existingWindow.canvas, session, onCloseRequest)
    }

    override fun disposeReusableNativeWindowResources(id: LightweightWindowId) {
    }

    override val focusedWindow: Window?
        get() = windows.values.firstOrNull { it.isFocused }

    override val isActive: Boolean get() = true

    override fun requestActivation() {
        activationFlow.tryEmit(Unit)
    }

    override val screens: Map<out Any, Screen> get() = mapOf(WasmJsScreen.name to WasmJsScreen)

    override fun showEmojiAndSymbolsPopup() {}

    private val quitHandlers = LinkedHashMap<String, () -> Boolean>()

    override fun quit() {
        val shouldTerminate = quitHandlers.values.all { it() }
        if (shouldTerminate) {
            customQuit?.invoke() ?: MainScope().launch { stopAndJoin() }
        }
    }

    override fun putQuitHandler(id: String, quitHandler: () -> Boolean) {
        quitHandlers[id] = quitHandler
    }

    override fun removeQuitHandler(id: String) {
        quitHandlers.remove(id)
    }

    override suspend fun awaitWhenReady() {}

    override val nativeApplication: Any = Unit

    internal val fontFamilyResolver: FontFamily.Resolver by lazy { createFontFamilyResolver() }

    @Composable
    override fun withCompositionLocal(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalUriHandler provides this,
            LocalClipboard provides this,
            LocalFontFamilyResolver provides fontFamilyResolver,
            LocalHapticFeedback provides remember { DefaultHapticFeedback() },
            LocalInputModeManager provides remember { DefaultInputModeManager() },
        ) {
            content()
        }
    }

    override suspend fun stopAndJoin() {
        try {
            resetState()
        } finally {
            window.close()
            removeApplication(this)
            shutdown = true
        }
    }

    override suspend fun resetForReuse() {
        try {
            resetState()
        } finally {
            deactivateApplication(this)
        }
    }

    private fun resetState() {
        windows.values.toList().forEach { it.dispose() }
        quitHandlers.clear()
    }
}

private fun defaultWindowContainer(): HTMLDivElement {
    airConfigTargetWindowElement()?.let { return it }
    val candidates = document.getElementsByClassName("air-window")
    check(candidates.length == 1) {
        "Expected exactly one element with the 'air-window' class, found ${candidates.length}"
    }
    return candidates.item(0) as HTMLDivElement
}

private fun airConfigTargetWindowElement(): HTMLDivElement? =
    js("(window.__airConfig && window.__airConfig['target.window.element']) || null")

private fun HTMLDivElement.hostWindowCanvas(): HTMLCanvasElement {
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.style.width = "100%"
    canvas.style.height = "100%"
    canvas.style.display = "block"
    canvas.style.outlineStyle = "none"
    canvas.tabIndex = 0
    canvas.setAttribute("draggable", "true")
    while (childNodes.length > 0) {
        childNodes.item(0)?.let { removeChild(it) }
    }
    appendChild(canvas)
    return canvas
}
