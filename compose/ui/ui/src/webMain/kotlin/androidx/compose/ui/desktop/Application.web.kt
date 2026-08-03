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

import androidx.compose.ui.desktop.wasm.WasmJsApplication
import androidx.compose.ui.desktop.wasm.WasmJsUriHandler
import androidx.compose.ui.platform.UriHandler
import kotlinx.io.files.Path

actual fun initializeApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    uriHandler: UriHandler,
    customQuit: (() -> Boolean)?,
) {
    // The identifier and the KDT library/log folders have no browser counterpart; the canvas
    // application is a singleton keyed to the hosting page.
    WasmJsApplication.initialize(uriHandler, customQuit)
    activateApplication(WasmJsApplication.current)
}

internal actual fun currentApplication(): Application =
    checkNotNull(activeApplication) { "No active Application has been initialized for this page" }

internal actual fun defaultUriHandler(): UriHandler = WasmJsUriHandler()

internal actual fun activateApplication(application: Application) {
    check(activeApplication == null || activeApplication === application) {
        "Another Application is already active in this page"
    }
    retainedApplications.add(application)
    activeApplication = application
}

internal actual fun deactivateApplication(application: Application) {
    if (activeApplication === application) {
        activeApplication = null
    }
}

internal actual fun removeApplication(application: Application) {
    if (activeApplication === application) {
        activeApplication = null
    }
    retainedApplications.remove(application)
}

private val retainedApplications = LinkedHashSet<Application>()
private var activeApplication: Application? = null
