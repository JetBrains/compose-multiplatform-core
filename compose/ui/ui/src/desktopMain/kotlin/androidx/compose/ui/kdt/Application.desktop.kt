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

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.GlobalDensity
import androidx.compose.ui.window.GlobalLayoutDirection
import androidx.compose.ui.window.application
import androidx.compose.ui.window.awaitApplication
import androidx.compose.ui.window.launchApplication
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import noria.NoriaContext
import org.jetbrains.desktop.macos.Application
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.KotlinDesktopToolkit
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.desktop.macos.WindowId

interface KdtApplication {
    fun exitApplication()

//    val isActive: Boolean
//    val keyWindow: KdtWindow?
//    val mainWindow: KdtWindow?
//    suspend fun yieldActivationTo(other: KdtApplication): Boolean
//    // or
//    fun requestActivation(): Boolean
}

val LocalKdtApplication = staticCompositionLocalOf<KdtComposeApplication> {
    error("No Application provided")
}

class KdtComposeApplication(): KdtApplication {
    init {
        KotlinDesktopToolkit.init()
    }
    val applicationStarted = CountDownLatch(1)
    val allWindows = mutableMapOf<WindowId, KdtComposeWindow>()
    val eventLoopThreadHandler = thread(start = true, name = "EventLoopWatcher") {
        GrandCentralDispatch.startOnMainThread {
            Application.init()
            Application.runEventLoop { event ->
                when (event) {
                    is WindowEvent -> {
                        val window = allWindows[event.windowId]
                        window?.handleEvent(event)
                    }
                    is Event.ApplicationDidFinishLaunching -> {
                        applicationStarted.countDown()
                    }
                    else -> {}
                }
                EventHandlerResult.Continue
            }
        }
    }
    val desktopGpuContext by lazy { DesktopGpuContext() }

    init {
        applicationStarted.await()
    }

    override fun exitApplication() {
        //todo close all resources including GPU context and all
        Application.stopEventLoop()
        eventLoopThreadHandler.join()
    }
}

fun kdtApplication(content: @Composable NoriaContext.() -> Unit) {
    val application = KdtComposeApplication()
    ComposeUIDispatcher = KDTUiDispatcher()

    runBlocking(ComposeUIDispatcher) {
        withContext(YieldFrameClock) {
            GlobalSnapshotManager.ensureStarted()

            val recomposer = Recomposer(coroutineContext)
            var isOpen by mutableStateOf(true)

            launch {
                recomposer.runRecomposeAndApplyChanges()
            }

            launch {
                val applier = ApplicationApplier()
                val composition = Composition(applier, recomposer)
                try {
                    composition.setContent {
                        if (isOpen) {
                            CompositionLocalProvider(
                                LocalKdtApplication provides application,
                                // Resources which are defined at the application level can use
                                // density to calculate intrinsicSize
                                LocalDensity provides GlobalDensity,
                                LocalLayoutDirection provides GlobalLayoutDirection,
                            ) {
                                content()
                            }
                        }
                    }
                    recomposer.close()
                    recomposer.join()
                } finally {
                    composition.dispose()
                }
            }
        }
    }
}

private object YieldFrameClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(
        onFrame: (frameTimeNanos: Long) -> R
    ): R {
        // We call `yield` to avoid blocking UI thread. If we don't call this then application
        // can be frozen for the user in some cases as it will not receive any input events.
        //
        // Swing dispatcher will process all pending events and resume after `yield`.
        yield()
        return onFrame(System.nanoTime())
    }
}

private class ApplicationApplier : Applier<Any> {
    override val current: Any = Unit
    override fun down(node: Any) = Unit
    override fun up() = Unit
    override fun insertTopDown(index: Int, instance: Any) {
        if (instance !is Unit) {
            throw IllegalStateException(
                "Composable content may not be added directly into " +
                    androidx.compose.ui.window.ApplicationScope::class.simpleName
            )
        }
    }
    override fun insertBottomUp(index: Int, instance: Any) {
        if (instance !is Unit) {
            throw IllegalStateException(
                "Composable content may not be added directly into " +
                    androidx.compose.ui.window.ApplicationScope::class.simpleName
            )
        }
    }
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun clear() = Unit
    override fun onEndChanges() = Unit
}
