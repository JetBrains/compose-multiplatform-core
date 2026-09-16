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

package androidx.compose.mpp.demo.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerIcon

/**
 * See https://developer.mozilla.org/en-US/docs/Web/CSS/cursor
 */
private const val CUSTOM_CURSOR_SVG_DATA_URL =
    "data:image/svg+xml;utf8," +
        "<svg xmlns='http://www.w3.org/2000/svg' width='32' height='32' viewBox='0 0 32 32'>" +
        "<circle cx='16' cy='16' r='12' fill='%23ff5722' stroke='white' stroke-width='2'/>" +
        "<circle cx='16' cy='16' r='3' fill='white'/>" +
        "</svg>"

@OptIn(ExperimentalComposeUiApi::class)
internal actual val platformPointerIcons = listOf(
    "grab" to PointerIcon("grab"),
    "grabbing" to PointerIcon("grabbing"),
    "zoom-in" to PointerIcon("zoom-in"),
    "zoom-out" to PointerIcon("zoom-out"),
    "not-allowed" to PointerIcon("not-allowed"),
    "help" to PointerIcon("help"),
    "progress" to PointerIcon("progress"),
    "col-resize" to PointerIcon("col-resize"),
    "row-resize" to PointerIcon("row-resize"),

    // CSS `url(...)` custom image cursor. A fallback keyword is required by the spec.
    // See https://developer.mozilla.org/en-US/docs/Web/CSS/cursor#values (url() syntax).
    "url(svg) fallback=auto" to PointerIcon(
        "url(\"$CUSTOM_CURSOR_SVG_DATA_URL\"), auto"
    ),

    // optional x- and y-coordinates indicating the cursor hotspot; the precise position within the cursor that is being pointed to.
    "url(svg) hotspot=16,16" to PointerIcon(
        "url(\"$CUSTOM_CURSOR_SVG_DATA_URL\") 16 16, pointer"
    ),

    "url(loading.svg)" to PointerIcon("url(\"loading.svg\"), auto"),
)
