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

@file:Suppress("DEPRECATION")

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

@Composable
@ExperimentalFoundationApi
@OptIn(InternalComposeUiApi::class)
internal actual fun rememberDefaultPrefetchScheduler(): PrefetchScheduler {
    val platformScheduler = LocalPlatformPrefetchScheduler.current
    return remember(platformScheduler) {
        SkikoPrefetchScheduler(platformScheduler)
    }
}

@OptIn(InternalComposeUiApi::class)
private class SkikoPrefetchScheduler(
    private val prefetchScheduler: PlatformPrefetchScheduler
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
            request = PlatformPrefetchRequestAdapter(prefetchRequest),
            priority = priority,
        )
    }
}

@OptIn(InternalComposeUiApi::class)
private class PlatformPrefetchRequestAdapter(
    private val prefetchRequest: PrefetchRequest,
) : PlatformPrefetchRequest {
    override fun PlatformPrefetchRequestScope.execute(): Boolean {
        val prefetchScope = PrefetchRequestScopeAdapter(this)
        return with(prefetchRequest) {
            prefetchScope.execute()
        }
    }
}

@OptIn(InternalComposeUiApi::class)
private class PrefetchRequestScopeAdapter(
    private val platformScope: PlatformPrefetchRequestScope,
) : PrefetchRequestScope {
    override fun availableTimeNanos(): Long =
        platformScope.availableTimeNanos()
}
