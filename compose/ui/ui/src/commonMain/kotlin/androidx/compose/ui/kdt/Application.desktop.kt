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

package androidx.compose.ui.kdt

import androidx.annotation.MainThread
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.io.files.Path
import noria.ui.loop.RenderLoop

suspend fun awaitApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    content: @Composable () -> Unit,
) {
    val terminationSignal = Job()
    initializeApplication(
        identifier,
        openUrls,
        libraryFolder,
        logFolder,
    ) { terminationSignal.complete() }
    val application = Application.current
    application.awaitWhenReady()
    coroutineScope {
        application.run {
            launchScene(content)
        }
        terminationSignal.join()
    }
    application.stopAndJoin()
}

expect fun initializeApplication(
    identifier: String,
    openUrls: (List<String>) -> Unit,
    libraryFolder: Path,
    logFolder: Path,
    uriHandler: UriHandler = defaultUriHandler(),
    customQuit: (() -> Boolean)? = null,
)

internal expect fun currentApplication(): Application

internal expect fun defaultUriHandler(): UriHandler

internal expect fun activateApplication(application: Application)

internal expect fun deactivateApplication(application: Application)

internal expect fun removeApplication(application: Application)

interface Application : Clipboard, UriHandler {
    companion object {
        val current: Application
            get() = currentApplication()
    }

    val systemTheme: SystemTheme
    val dragThreshold: Dp
        get() = DefaultDragThreshold
    val doubleClickDistance: Dp
        get() = DefaultDoubleClickDistance

    fun createWindow(
        scene: Scene<*>,
        onCloseRequest: () -> Unit,
    ): Window

    fun prepareNativeWindowResourcesForReuse(id: LightweightWindowId)

    fun reuseWindow(
        id: LightweightWindowId,
        scene: Scene<*>,
        onCloseRequest: () -> Unit,
    ): Window?

    fun disposeReusableNativeWindowResources(id: LightweightWindowId)

    //    val mainWindow: KdtWindow?
    val windows: Map<LightweightWindowId, Window>
    val focusedWindow: Window?

    val isActive: Boolean

    @MainThread
    fun requestActivation()
//    suspend fun yieldActivationTo(other: KdtApplication): Boolean

    val screens: Map<out Any, Screen>

    @MainThread
    fun showEmojiAndSymbolsPopup()

    fun quit()
    fun putQuitHandler(id: String, quitHandler: () -> Boolean)
    fun removeQuitHandler(id: String)
    suspend fun awaitWhenReady()

    val nativeApplication: Any

    fun CoroutineScope.launchScene(content: @Composable () -> Unit): SceneHandle {
        return launchScene(EmptyCoroutineContext, {}, {}, content)
    }

    fun <T> CoroutineScope.launchScene(
        applyCoroutineContext: CoroutineContext,
        prepareMainThread: () -> T,
        restoreMainThread: (T) -> Unit,
        content: @Composable () -> Unit,
    ): SceneHandle

    suspend fun resetForReuse() {
        stopAndJoin()
    }

    suspend fun stopAndJoin()
}

interface IconDecoratedApplication : Application {
    @MainThread
    fun setIcon(icon: ByteArray)
}

internal val DefaultDragThreshold = 8.dp
internal val DefaultDoubleClickDistance = 5.dp

data class SceneHandle(
    val renderLoop: RenderLoop,
    val broadcastFrameClock: BroadcastFrameClock,
)
