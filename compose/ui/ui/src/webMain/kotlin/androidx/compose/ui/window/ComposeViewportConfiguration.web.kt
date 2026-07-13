/*
 * Copyright 2025 The Android Open Source Project
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

import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.isClearFocusOnMouseDownEnabled

/**
 * Configuration of [ComposeViewport] behavior.
 */
@ExperimentalComposeUiApi
class ComposeViewportConfiguration internal constructor() {

    /**
     * Indicates whether accessibility (a11y) is enabled for the associated Compose viewport.
     * When it's enabled, the Compose Viewport will maintain a DOM tree mirroring the Compose semantics nodes.
     * That DOM tree is visibly hidden, but reachable by the accessibility tools.
     * It can be disabled to avoid the overhead of maintaining the DOM tree.
     * By default, it is set to `true`.
     *
     * Note: This API is experimental and subject to change in the future.
     */
    @ExperimentalComposeUiApi
    var isA11YEnabled: Boolean = true

    /**
     * Controls whether a mouse clicks on an unfocused element clears focus.
     * It's clearing focus on mouse down by default.
     */
    @ExperimentalComposeUiApi
    var isClearFocusOnMouseDownEnabled: Boolean = ComposeUiFlags.isClearFocusOnMouseDownEnabled

    /**
     * Controls whether the Compose scene handles system window insets (status bar, navigation bar,
     * IME keyboard) and exposes them via [androidx.compose.foundation.layout.WindowInsets] APIs
     * such as `WindowInsets.safeDrawing`, `WindowInsets.ime`, etc.
     *
     * When set to `true`, the scene reads safe area insets from the browser using CSS
     * `env(safe-area-inset-*)` environment variables, and tracks IME (virtual keyboard) geometry.
     *
     * **Prerequisite**: the page must opt in to edge-to-edge rendering by including
     * `viewport-fit=cover` in the viewport meta tag:
     * ```html
     * <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
     * ```
     * Without `viewport-fit=cover`, the browser applies safe area padding automatically and all
     * `env(safe-area-inset-*)` variables return `0px`, so insets will always be zero.
     *
     * By default, this is `false` and the scene reports zero insets.
     *
     * **Scrollable containers:** insets are re-read on `window resize` and keyboard geometry events,
     * but not on page scroll. If the [composeScene] is inside a scrollable page, its viewport position
     * changes as the user scrolls, so the insets may become invalid. In that case
     * it is recommended to disable inset handling entirely (`enableBrowserWindowInsets = false`) and
     * manage padding manually.
     *
     * Note: This API is experimental and subject to change in the future.
     */
    @ExperimentalComposeUiApi
    var enableBrowserWindowInsets: Boolean = false
}