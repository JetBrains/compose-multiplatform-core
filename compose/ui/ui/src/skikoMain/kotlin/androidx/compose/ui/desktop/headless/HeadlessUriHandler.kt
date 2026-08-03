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

package androidx.compose.ui.desktop.headless

import androidx.compose.ui.platform.UriHandler

/**
 * Records the last URL a headless run tried to open, for tests that drive login/OAuth flows
 * without a real browser (Fleet's BrowserUtils polls this; UIMode.REAL_WITH_FAKE_BROWSER installs
 * [HeadlessUriHandler] even with real windowing backends).
 */
object FakeBrowser {
    var lastOpenedUrl: String? = null
}

class HeadlessUriHandler : UriHandler {
    override fun openUri(uri: String) {
        FakeBrowser.lastOpenedUrl = uri
    }
}
