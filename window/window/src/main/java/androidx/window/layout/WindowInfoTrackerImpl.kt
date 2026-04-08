/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.window.layout

import android.app.Activity
import android.content.Context
import androidx.annotation.UiContext
import androidx.core.util.Consumer
import androidx.window.WindowSdkExtensions
import androidx.window.layout.adapter.EngagementModeBackend
import androidx.window.layout.adapter.WindowBackend
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * An implementation of [WindowInfoTracker] that provides the [WindowLayoutInfo] and [WindowMetrics]
 * for the given [Activity] or [UiContext].
 *
 * @param windowMetricsCalculator a helper to calculate the [WindowMetrics] for the [Activity].
 * @param windowBackend a helper to provide the [WindowLayoutInfo].
 */
internal class WindowInfoTrackerImpl(
    private val windowMetricsCalculator: WindowMetricsCalculator,
    private val windowBackend: WindowBackend,
    private val windowSdkExtensions: WindowSdkExtensions,
    private val engagementModeBackend: EngagementModeBackend,
) : WindowInfoTracker {

    /**
     * A [Flow] of window layout changes in the current visual [UiContext]. A context has to be
     * either an [Activity] or created with [Context.createWindowContext].
     */
    override fun windowLayoutInfo(@UiContext context: Context): Flow<WindowLayoutInfo> {
        return windowLayoutInfoInternal(context)
    }

    /** A [Flow] of window layout changes in the current visual [Activity]. */
    override fun windowLayoutInfo(activity: Activity): Flow<WindowLayoutInfo> {
        return windowLayoutInfoInternal(activity)
    }

    private fun windowLayoutInfoInternal(@UiContext context: Context): Flow<WindowLayoutInfo> {
        val engagementFlow = callbackFlow {
            val listener = Consumer { engagementMode: WindowLayoutInfo.EngagementMode ->
                trySend(engagementMode)
            }
            engagementModeBackend.addEngagementLayoutChangeCallback(
                context,
                Runnable::run,
                listener,
            )
            awaitClose { engagementModeBackend.removeEngagementLayoutChangeCallback(listener) }
        }

        val layoutFlow = callbackFlow {
            val listener = Consumer { info: WindowLayoutInfo -> trySend(info) }
            windowBackend.registerLayoutChangeCallback(context, Runnable::run, listener)
            awaitClose { windowBackend.unregisterLayoutChangeCallback(listener) }
        }

        return combine(layoutFlow, engagementFlow) { info, mode -> withEngagementMode(info, mode) }
            .flowOn(Dispatchers.Main)
    }

    override val supportedPostures: List<SupportedPosture>
        get() {
            windowSdkExtensions.requireExtensionVersion(6)
            return windowBackend.supportedPostures
        }

    override fun getCurrentWindowLayoutInfo(@UiContext context: Context): WindowLayoutInfo {
        windowSdkExtensions.requireExtensionVersion(9)
        val windowLayoutInfo = windowBackend.getCurrentWindowLayoutInfo(context)
        val engagementLayoutMode = engagementModeBackend.getCurrentEngagementLayoutMode(context)
        return withEngagementMode(windowLayoutInfo, engagementLayoutMode)
    }

    override fun registerWindowLayoutInfoListener(
        @UiContext context: Context,
        executor: Executor,
        listener: Consumer<WindowLayoutInfo>,
    ) {
        windowBackend.registerLayoutChangeCallback(context, executor, listener)
    }

    override fun unregisterWindowLayoutInfoListener(listener: Consumer<WindowLayoutInfo>) {
        windowBackend.unregisterLayoutChangeCallback(listener)
    }

    internal fun withEngagementMode(
        windowLayoutInfo: WindowLayoutInfo,
        mode: WindowLayoutInfo.EngagementMode,
    ): WindowLayoutInfo {
        val newModes = windowLayoutInfo.engagementModes.toMutableSet()
        newModes.add(mode)
        if (mode == WindowLayoutInfo.EngagementMode.ENGAGEMENT_PRECISE_POINTER) {
            newModes.remove(WindowLayoutInfo.EngagementMode.ENGAGEMENT_TOUCH)
        } else if (mode == WindowLayoutInfo.EngagementMode.ENGAGEMENT_TOUCH) {
            newModes.remove(WindowLayoutInfo.EngagementMode.ENGAGEMENT_PRECISE_POINTER)
        }
        return WindowLayoutInfo(windowLayoutInfo.displayFeatures, newModes)
    }
}
