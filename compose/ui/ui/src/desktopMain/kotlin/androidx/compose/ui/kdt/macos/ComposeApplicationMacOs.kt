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

package androidx.compose.ui.kdt.macos

import androidx.compose.ui.kdt.ComposeApplication
import androidx.compose.ui.kdt.ComposeWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import org.jetbrains.desktop.macos.Application
import org.jetbrains.desktop.macos.Event
import org.jetbrains.desktop.macos.EventHandlerResult
import org.jetbrains.desktop.macos.GrandCentralDispatch
import org.jetbrains.desktop.macos.KotlinDesktopToolkit
import org.jetbrains.desktop.macos.Screen
import org.jetbrains.desktop.macos.TextDirection
import org.jetbrains.desktop.macos.WindowEvent
import org.jetbrains.desktop.macos.WindowId

class ComposeApplicationMacOs(): ComposeApplication, AutoCloseable {
    init {
        KotlinDesktopToolkit.init()
    }
    val applicationStarted = CountDownLatch(1)
    val allWindows = mutableMapOf<WindowId, ComposeWindowMacOs>()
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
        // todo[ps] join hangs by some reason
//         eventLoopThreadHandler.join()
    }

    override fun macOsApplication(): ComposeApplicationMacOs {
        return this
    }

    override fun close() {
        // todo[ps] close the application
    }

    override fun globalDensity(): Density {
        val density = Screen.allScreens().mainScreen().scale
        return Density(density.toFloat(), fontScale = 1f)
    }

    /**
     * This value is usually stable between system restarts
     * So we can not bothering with its invalidation
     */
    override fun globalLayoutDirection(): LayoutDirection {
        return when (Application.textDirection) {
            TextDirection.LeftToRight -> LayoutDirection.Ltr
            TextDirection.RightToLeft -> LayoutDirection.Rtl
        }
    }

    override fun createWindow(): ComposeWindow {
        val window = ComposeWindowMacOs(this)
        allWindows.put(window.window.windowId(), window)
        return window
    }
}