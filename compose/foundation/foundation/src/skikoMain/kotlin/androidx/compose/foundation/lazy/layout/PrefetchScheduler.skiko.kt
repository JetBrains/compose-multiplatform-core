/*
 * Copyright 2024 The Android Open Source Project
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

@file:Suppress("DEPRECATION") // b/420551535

package androidx.compose.foundation.lazy.layout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.LocalPlatformPrefetchScheduler
import androidx.compose.ui.platform.PlatformPrefetchPriority
import androidx.compose.ui.platform.PlatformPrefetchRequest
import androidx.compose.ui.platform.PlatformPrefetchRequestScope
import androidx.compose.ui.platform.PlatformPrefetchScheduler

// TODO: https://youtrack.jetbrains.com/issue/CMP-1265

@Composable
@ExperimentalFoundationApi
@OptIn(InternalComposeUiApi::class)
internal actual fun rememberDefaultPrefetchScheduler(): PrefetchScheduler {
    val platformScheduler = LocalPlatformPrefetchScheduler.current
    return remember(platformScheduler) {
        SkikoPrefetchScheduler(platformScheduler)
    }
}

@Suppress("DEPRECATION")
@OptIn(InternalComposeUiApi::class)
private class SkikoPrefetchScheduler(
    private var prefetchScheduler: PlatformPrefetchScheduler
) :
    PrefetchScheduler,
    PriorityPrefetchScheduler {

    override fun scheduleHighPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        schedule(prefetchRequest, PlatformPrefetchPriority.High)
    }

    override fun scheduleLowPriorityPrefetch(prefetchRequest: PrefetchRequest) {
        schedule(prefetchRequest, PlatformPrefetchPriority.Low)
    }

    private fun schedule(
        prefetchRequest: PrefetchRequest,
        priority: PlatformPrefetchPriority,
    ) {
        prefetchScheduler.schedulePrefetch(
            request = object : PlatformPrefetchRequest {
                override fun PlatformPrefetchRequestScope.execute(): Boolean {
                    val platformScope = this
                    return with(prefetchRequest) {
                        object : PrefetchRequestScope {
                            override fun availableTimeNanos(): Long =
                                platformScope.availableTimeNanos()
                        }.execute()
                    }
                }
            },
            priority = priority,
        )
    }
}
