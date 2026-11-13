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
import androidx.compose.ui.platform.FONT_FALLBACK_BASE_URL

/** The default text of the accessibility activation button. */
internal const val DEFAULT_A11Y_ACTIVATION_LABEL = "Enable accessibility"

/** Controls when a Compose viewport exposes its accessibility tree. */
@ExperimentalComposeUiApi
sealed interface WebAccessibilityActivation {
    /** Disables both the accessibility tree and the activation button. */
    data object Disabled : WebAccessibilityActivation

    /**
     * Builds the accessibility tree when the user activates it.
     *
     * @property activationLabel The localized button text and accessible name.
     */
    data class OnUserRequest(
        val activationLabel: String = DEFAULT_A11Y_ACTIVATION_LABEL,
    ) : WebAccessibilityActivation

    /** Builds the accessibility tree at startup. */
    data object Eager : WebAccessibilityActivation
}

/**
 * Configuration of [ComposeViewport] behavior.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ComposeViewportConfiguration internal constructor() {

    /**
     * Whether the viewport supports accessibility. Defaults to `true`.
     * Setting this to `false` selects [WebAccessibilityActivation.Disabled]. Setting it to `true` selects
     * [WebAccessibilityActivation.OnUserRequest] with its default label if disabled and otherwise preserves
     * the current configuration. Use [accessibilityActivation] to configure activation explicitly.
     */
    var isA11YEnabled: Boolean
        get() = accessibilityActivation != WebAccessibilityActivation.Disabled
        set(value) {
            if (!value) {
                accessibilityActivation = WebAccessibilityActivation.Disabled
            } else if (accessibilityActivation == WebAccessibilityActivation.Disabled) {
                accessibilityActivation = WebAccessibilityActivation.OnUserRequest()
            }
        }

    /**
     * Configures when the viewport builds its accessibility tree.
     * Defaults to [WebAccessibilityActivation.OnUserRequest].
     */
    @ExperimentalComposeUiApi
    var accessibilityActivation: WebAccessibilityActivation = WebAccessibilityActivation.OnUserRequest()

    /**
     * Base URL used to download Noto fallback fonts for unresolved glyphs.
     *
     * The URL is concatenated directly with each fallback font's relative URL.
     * Set this to `null` to disable automatic fallback font downloads.
     *
     * By default, fonts are downloaded from `fonts.gstatic.com/s/`
     */
    var fontFallbackUrl: String? = FONT_FALLBACK_BASE_URL

    /**
     * Controls whether a mouse clicks on an unfocused element clears focus.
     * It's clearing focus on mouse down by default.
     */
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
     */
    var enableBrowserWindowInsets: Boolean = false

    /**
     * Whether accessibility is activated eagerly: the tree is built at startup rather than on
     * user request.
     */
    internal val isA11YActivatedEagerly: Boolean
        get() = accessibilityActivation == WebAccessibilityActivation.Eager
}
