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

// Must stay in package androidx.compose.ui.text: Paragraph is a sealed interface, so its subtypes
// have to be declared in the same package.
package androidx.compose.ui.text

import androidx.compose.ui.InternalComposeUiApi

/**
 * Platform [Paragraph] supplied by the registered ui-text backend. The backend implements it
 * directly (so no wrapper is needed) and may add line-metric accessors that are not part of the
 * common [Paragraph] API.
 */
@InternalComposeUiApi
interface PlatformParagraph : Paragraph {
    /** Returns the ascent (distance above the baseline, in pixels) of the line at [lineIndex]. */
    fun getLineAscent(lineIndex: Int): Float

    /** Returns the descent (distance below the baseline, in pixels) of the line at [lineIndex]. */
    fun getLineDescent(lineIndex: Int): Float
}
