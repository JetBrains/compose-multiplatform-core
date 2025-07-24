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

package androidx.compose.ui.platform

// clipboard.read(), clipboard.write(), ClipboardItem are not available in FF < 127 versions
// https://developer.mozilla.org/en-US/docs/Web/API/Clipboard/read#browser_compatibility
// https://developer.mozilla.org/en-US/docs/Web/API/Clipboard/write#browser_compatibility
// It's possible to fall back to clipboard.writeText() if the content type is a text.
internal fun isFullClipboardApiSupported(): Boolean =
    js("""
        window.navigator.clipboard && 
        window.navigator.clipboard.write && 
        window.navigator.clipboard.read && 
        typeof(ClipboardItem) !== 'undefined'
    """)

internal fun isSecureContext(): Boolean = js("window.isSecureContext === true")

internal fun isFallbackWriteTextApiAvailable(): Boolean =
    js("window.navigator.clipboard && window.navigator.clipboard.writeText")

/**
 * This Clipboard implementation is dumb.
 * It's created when window.isSecureContext != true.
 * See https://developer.mozilla.org/en-US/docs/Web/API/Clipboard
 */
internal class DumbInsecureContextClipboard : Clipboard {

    override suspend fun getClipEntry(): ClipEntry? {
        println("Clipboard is not available in insecure contexts.")
        return null
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        println("Clipboard is not available in insecure contexts.")
    }

    private val browserClipboard by lazy {
        println("Clipboard is not available in insecure contexts.")
        getW3CClipboard()
    }

    override val nativeClipboard: NativeClipboard
        get() = browserClipboard
}

internal fun getW3CClipboard(): NativeClipboard = js("window.navigator.clipboard")