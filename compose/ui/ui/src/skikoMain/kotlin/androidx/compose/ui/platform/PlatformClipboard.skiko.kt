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

// TODO: https://youtrack.jetbrains.com/issue/CMP-1260
// TODO(Merge) Major, Implement after merging 437520c6f81b48618204661cefa664e599147763
// Implement clipboard metadata inspection for non-Android targets; until then all metadata queries return false.
actual class ClipMetadata internal constructor() {
    actual fun hasText(): Boolean = false

    actual fun hasPlainText(): Boolean = false

    actual fun hasHtml(): Boolean = false

    actual fun hasUrl(): Boolean = false
}

@Suppress("DEPRECATION")
internal expect fun createPlatformClipboardManager(): ClipboardManager

internal expect fun createPlatformClipboard(): Clipboard
