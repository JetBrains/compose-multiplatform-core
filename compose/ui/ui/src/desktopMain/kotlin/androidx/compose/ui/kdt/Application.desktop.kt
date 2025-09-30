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
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ComposeUIDispatcher
import androidx.compose.ui.kdt.macos.ComposeApplicationMacOs
import androidx.compose.ui.kdt.macos.KDTUiDispatcher
import androidx.compose.ui.platform.GlobalSnapshotManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

interface ComposeApplication: AutoCloseable {
    fun exitApplication()

//    val isActive: Boolean
//    val keyWindow: KdtWindow?
//    val mainWindow: KdtWindow?
//    suspend fun yieldActivationTo(other: KdtApplication): Boolean
//    // or
//    fun requestActivation(): Boolean

    // todo[ps] this functions actually shouldn't be a part of the api
    // but it's a functions shared across platforms
    fun globalDensity(): Density
    fun globalLayoutDirection(): LayoutDirection
    fun createWindow(): ComposeWindow
    fun macOsApplication(): ComposeApplicationMacOs?
}

val LocalComposeApplication = staticCompositionLocalOf<ComposeApplication> {
    error("No Application provided")
}

/**
 * This function is intended to be called in Dock
 * to initialize a shared application instance
 */
fun initApplication(): ComposeApplication {
    // todo[ps] support other platforms here
    val application = ComposeApplicationMacOs()
    ComposeUIDispatcher = KDTUiDispatcher()
    return application
}

/**
 * This is an entry point into composition, it can be called per frontend.
 * It's blocking until there are some LunchEffect in composition or some
 * windows are presented.
 */
fun runApplication(application: ComposeApplication, content: @Composable () -> Unit) {
    runBlocking(ComposeUIDispatcher) {
        withContext(YieldFrameClock) {
            GlobalSnapshotManager.ensureStarted()

            val recomposer = Recomposer(coroutineContext)

            launch {
                recomposer.runRecomposeAndApplyChanges()
            }

            launch {
                val applier = ApplicationApplier()
                val composition = Composition(applier, recomposer)
                try {
                    composition.setContent {
                        CompositionLocalProvider(
                            LocalComposeApplication provides application,
                            // Resources which are defined at the application level can use
                            // density to calculate intrinsicSize
                            // todo[ps] invalidate when screen configuration changed
                            LocalDensity provides application.globalDensity(),
                            LocalLayoutDirection provides application.globalLayoutDirection(),
                        ) {
                            content()
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
