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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.BrowserCursor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.PlatformPrefetchScheduler
import androidx.compose.ui.platform.PlatformScreenReader
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.WebHapticFeedback
import androidx.compose.ui.platform.WebOutOfFrameExecutor
import androidx.compose.ui.platform.WebPrefetchScheduler
import androidx.compose.ui.platform.WebTextInputService
import androidx.compose.ui.platform.WebTextToolbar
import androidx.compose.ui.platform.WebWakeLockManager
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.accessibility.ComposeWebSemanticsListener
import androidx.compose.ui.platform.isIdleCallbackSupported
import androidx.compose.ui.platform.isPostingTasksSupported
import androidx.compose.ui.platform.isVibrationSupported
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.toDpRect
import kotlinx.coroutines.coroutineScope
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

/**
 * Implements [PlatformContext] bindings using browser APIs.
 */
internal abstract class WebWindowPlatformContext(
    private val canvas: HTMLCanvasElement,
    private val layerRoot: HTMLElement,
    private val a11yContainerElement: HTMLDivElement?,
    private val configuration: ComposeViewportConfiguration,
    override val windowInfo: WindowInfo
) : PlatformContext by PlatformContext.Empty() {
    protected abstract val density: Density
    protected abstract val currentTouchOffset: Offset

    protected abstract fun sendKeyEvent(keyEvent: KeyEvent): Boolean

    override val textToolbar: WebTextToolbar = WebTextToolbar()

    override val screenReader: PlatformScreenReader
        get() = object : PlatformScreenReader {
            override var isActive = configuration.isA11YEnabled
        }

    override val outOfFrameExecutor: WebOutOfFrameExecutor? by lazy(LazyThreadSafetyMode.NONE) {
        if (isPostingTasksSupported) WebOutOfFrameExecutor() else null
    }

    @Suppress("RedundantOverride")
    override fun convertLocalToWindowPosition(localPosition: Offset): Offset {
        // TODO (o.karpovich): Currently, CfW uses AttachedComposeSceneLayer, so
        // Window Rect == Canvas Rect, although a canvas might take only a portion of the browser's
        // viewport: Window Rect > Canvas Rect.
        // Update this implementation when implementing https://youtrack.jetbrains.com/issue/CMP-8359
        // The implementation will have to rely on the <canvas> of a particular layer.
        return super.convertLocalToWindowPosition(localPosition)
    }

    @Suppress("RedundantOverride")
    override fun convertWindowToLocalPosition(positionInWindow: Offset): Offset {
        // TODO (o.karpovich): Currently, CfW uses AttachedComposeSceneLayer, so
        // Window Rect == Canvas Rect, although a canvas might take only a portion of the browser's
        // viewport: Window Rect > Canvas Rect.
        // Update this implementation when implementing https://youtrack.jetbrains.com/issue/CMP-8359
        return super.convertWindowToLocalPosition(positionInWindow)
    }

    override val hapticFeedback by lazy(LazyThreadSafetyMode.NONE) {
        if (isVibrationSupported()) WebHapticFeedback() else super.hapticFeedback
    }

    override val prefetchScheduler: PlatformPrefetchScheduler =
        if (isIdleCallbackSupported) WebPrefetchScheduler() else super.prefetchScheduler

    override val semanticsOwnerListener: PlatformContext.SemanticsOwnerListener? =
        if (configuration.isA11YEnabled) {
            ComposeWebSemanticsListener(
                webSemanticsRoot = a11yContainerElement?.also {
                    it.prepareA11yContainer()
                } ?: error("a11yContainerElement must be provided"),
            )
        } else {
            null
        }

    private fun HTMLElement.prepareA11yContainer() {
        setAttribute("aria-label", "")
        setAttribute("role", "presentation")
        setAttribute("aria-live", "polite")
        id = "cmp_a11y_root"
        style.opacity = "0"
        style.setProperty("pointer-events", "none")
    }

    override val textInputService: WebTextInputService by lazy(LazyThreadSafetyMode.NONE) {
        object : WebTextInputService() {

            override val currentTouchOffset: Offset
                get() = this@WebWindowPlatformContext.currentTouchOffset

            override val backingDomInputContainer: HTMLElement
                get() = layerRoot

            override fun getNewGeometryForBackingInput(rect: Rect): DpRect {
                val dpRect = rect.toDpRect(density)
                val left = dpRect.left.value
                val top = dpRect.top.value

                return DpRect(DpOffset(left.dp, top.dp), dpRect.size)
            }

            override fun processKeyboardEvent(keyEvent: KeyEvent): Boolean {
                return sendKeyEvent(keyEvent)
            }

            override fun stopInput() {
                // super.stopInput() will remove the focused backing html input.
                // Once a focused HTML element is removed, the browser moves focus to <body>.
                // Focus the canvas first so it retains focus after the backing input is removed:
                if (!canvas.isFocused()) {
                    canvas.focus()
                }
                super.stopInput()
            }
        }
    }

    override val viewConfiguration =
        object : ViewConfiguration by PlatformContext.DefaultViewConfiguration {
            // Aligning the touchSlop value with the Android default:
            // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/view/ViewConfiguration.java?pli=1#191
            override val touchSlop: Float get() = with(density) { 8.dp.toPx() }
            override val maximumFlingVelocity: Float
                //https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/view/ViewConfiguration.java;l=240;drc=733537294b158d22f2ae383f2ed77c93741798e9
                get() = with(density) { 8000.dp.toPx() }
        }

    override var isKeepScreenOnEnabled: Boolean
        get() = WebWakeLockManager.isWakeLockActive()
        set(value) = WebWakeLockManager.sendWakeLockRequest(this, value)

    override fun setPointerIcon(pointerIcon: PointerIcon) {
        if (pointerIcon is BrowserCursor) {
            canvas.style.cursor = pointerIcon.id
        }
    }

    override suspend fun startInputMethod(request: PlatformTextInputMethodRequest): Nothing {
        coroutineScope {
            WebTextInputSession(this, textInputService)
                .startInputMethod(request)
        }
    }

    override val isClearFocusOnMouseDownEnabled: Boolean
        get() = configuration.isClearFocusOnMouseDownEnabled
}
