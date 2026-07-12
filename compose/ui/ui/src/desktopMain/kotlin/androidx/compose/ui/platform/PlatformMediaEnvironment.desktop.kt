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

@file:OptIn(ExperimentalMediaQueryApi::class)

package androidx.compose.ui.platform

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.GlobalDensity
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.SystemTheme

internal class DesktopMediaEnvironment(val windowInfo: WindowInfo) : PlatformMediaEnvironment {



    private var systemThemeSubscriberCount = 0
    private var pollingSystemThemeJob: Job? = null
    private val systemThemeSubscribeLock = Any()
    private var currentSystemTheme = mutableStateOf(org.jetbrains.skiko.currentSystemTheme)

    @OptIn(DelicateCoroutinesApi::class)
    internal fun onSystemThemeSubscriberAdded() {
        synchronized(systemThemeSubscribeLock) {
            if (systemThemeSubscriberCount == 0) {
                pollingSystemThemeJob = GlobalScope.launch {
                    withContext(Dispatchers.IO) {
                        pollCurrentSystemTheme()
                    }
                }
            }
            systemThemeSubscriberCount += 1
        }
    }

    internal fun onSystemThemeSubscriberRemoved() {
        synchronized(systemThemeSubscribeLock) {
            systemThemeSubscriberCount -= 1
            if (systemThemeSubscriberCount == 0) {
                pollingSystemThemeJob?.cancel()
                pollingSystemThemeJob = null
            }
        }
    }

    private suspend fun pollCurrentSystemTheme() {
        while (true) {
            currentSystemTheme.value = org.jetbrains.skiko.currentSystemTheme
            delay(1.seconds)
        }
    }

    @VisibleForTesting
    internal fun systemThemeSubscriberCount() = systemThemeSubscriberCount

    @VisibleForTesting
    internal fun systemThemePollingJob() = pollingSystemThemeJob

    override val systemTheme: SystemTheme
        get() = currentSystemTheme.value

    override val systemDensity: Density
        get() = GlobalDensity

    override val windowPosture: UiMediaScope.Posture
        get() = UiMediaScope.Posture.Flat

    override val windowWidth: Dp
        get() = windowInfo.containerDpSize.width

    override val windowHeight: Dp
        get() = windowInfo.containerDpSize.height

    override val pointerPrecision: UiMediaScope.PointerPrecision
        get() = UiMediaScope.PointerPrecision.Fine

    override val keyboardKind: UiMediaScope.KeyboardKind
        get() = UiMediaScope.KeyboardKind.Physical

    override val hasMicrophone: Boolean
        get() = true //inferred as always having at least 1 microphone

    override val hasCamera: Boolean
        get() = false //no reliable way to get it

    override val viewingDistance: UiMediaScope.ViewingDistance
        get() = UiMediaScope.ViewingDistance.Near

    override fun dispose() {
        pollingSystemThemeJob?.cancel()
    }
}