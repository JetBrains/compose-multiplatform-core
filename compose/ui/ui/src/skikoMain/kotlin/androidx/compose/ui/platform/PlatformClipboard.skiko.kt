/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.platform

actual class ClipMetadata internal constructor(
    private val hasTextValue: Boolean,
    private val hasPlainTextValue: Boolean,
    private val hasHtmlValue: Boolean,
    private val hasUrlValue: Boolean,
) {
    actual fun hasText(): Boolean = hasTextValue

    actual fun hasPlainText(): Boolean = hasPlainTextValue

    actual fun hasHtml(): Boolean = hasHtmlValue

    actual fun hasUrl(): Boolean = hasUrlValue
}

@Suppress("DEPRECATION")
internal expect fun createPlatformClipboardManager(): ClipboardManager

internal expect fun createPlatformClipboard(): Clipboard
