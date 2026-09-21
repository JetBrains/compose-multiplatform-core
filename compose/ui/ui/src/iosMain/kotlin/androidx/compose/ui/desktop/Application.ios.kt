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

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.registerSkikoComposeImplementation
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

// The desktop Application API has no uikit implementation, so one is never active here.
internal actual fun hasActiveComposeApplication(): Boolean = false

internal actual fun defaultUriHandler(): UriHandler {
    error(NOT_SUPPORTED_MESSAGE)
}

@OptIn(InternalComposeUiApi::class)
internal actual fun activateApplication(application: Application) {
    // Every fleet window, and the application's own composition, need ui-skiko's graphics and text
    // implementations. Upstream registers them per host (ComposeContainer, ImageComposeScene, …);
    // fleet's hosts are all created by an application, and every application activates here first.
    registerSkikoComposeImplementation()
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun deactivateApplication(application: Application) {
    error(NOT_SUPPORTED_MESSAGE)
}

internal actual fun removeApplication(application: Application) {
    error(NOT_SUPPORTED_MESSAGE)
}
