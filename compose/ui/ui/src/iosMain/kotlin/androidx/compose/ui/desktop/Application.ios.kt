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

package androidx.compose.ui.desktop

import androidx.compose.ui.platform.UriHandler
import kotlinx.io.files.Path

private const val NOT_SUPPORTED_MESSAGE =
    "The desktop Application entry API is not supported on iOS; Compose scenes are hosted by ComposeUIViewController."

actual fun initializeApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    uriHandler: UriHandler,
    customQuit: (() -> Boolean)?,
) {
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun currentApplication(): Application {
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun defaultUriHandler(): UriHandler {
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun activateApplication(application: Application) {
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun deactivateApplication(application: Application) {
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun removeApplication(application: Application) {
    error(NOT_SUPPORTED_MESSAGE)
}
