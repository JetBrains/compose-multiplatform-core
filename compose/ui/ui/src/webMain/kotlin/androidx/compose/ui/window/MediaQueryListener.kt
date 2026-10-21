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

import kotlin.js.js
import kotlinx.browser.window
import org.w3c.dom.MediaQueryList
import org.w3c.dom.events.Event

internal abstract class MediaQueryListener(
    query: String,
    private val media: MediaQueryList? = matchMediaOrNull(query)
) {
    private var isDisposed = false
    private var stopListening: (() -> Unit)? = null

    abstract fun onChange(matches: Boolean)

    private val listener: (Event) -> Unit = {
        if (!isDisposed && media != null) onChange(media.matches)
    }

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        stopListening?.invoke()
        stopListening = null
    }

    init {
        media?.let { media ->
            try {
                media.addEventListener("change", listener)
                stopListening = { media.removeEventListener("change", listener) }
            } catch (t: Throwable) {
                // Older browsers only expose addListener/removeListener. If neither API is
                // available, the initial match is still usable, without live observation.
                if (runCatching { media.addListener(listener) }.isSuccess) {
                    stopListening = { media.removeListener(listener) }
                }
            }
        }
    }

    fun matches(): MediaQueryStatus = when {
        media == null -> MediaQueryStatus.UNSUPPORTED
        media.matches -> MediaQueryStatus.MATCH
        else -> MediaQueryStatus.NO_MATCH
    }
}

internal sealed interface MediaQueryStatus {
    object UNSUPPORTED : MediaQueryStatus
    object MATCH : MediaQueryStatus
    object NO_MATCH : MediaQueryStatus
}

// supported by all browsers since 2015
// https://developer.mozilla.org/en-US/docs/Web/API/Window/matchMedia
// Changed from `@JsFun` annotation because in 2.2.20 it's marked as not available on LV = 2.0
// TODO: Cannot add opt-in with LV = 2.0 due to https://youtrack.jetbrains.com/issue/KT-79716
private fun isMatchMediaSupported(): Boolean = js("typeof window.matchMedia === 'function'")

internal fun matchMediaOrNull(query: String): MediaQueryList? =
    if (isMatchMediaSupported()) runCatching { window.matchMedia(query) }.getOrNull() else null
