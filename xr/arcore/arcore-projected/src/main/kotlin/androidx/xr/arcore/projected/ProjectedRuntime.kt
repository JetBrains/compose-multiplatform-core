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

package androidx.xr.arcore.projected

import androidx.annotation.RestrictTo
import androidx.xr.arcore.internal.PerceptionRuntime
import androidx.xr.runtime.Config
import kotlin.time.ComparableTimeMark

/**
 * Implementation of the [androidx.xr.arcore.internal.PerceptionRuntime] interface using Projected.
 *
 * @property lifecycleManager that manages the lifecycle of the Projected session.
 * @property perceptionManager that manages the perception capabilities of a runtime using
 *   Projected.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ProjectedRuntime
internal constructor(
    override val lifecycleManager: ProjectedManager,
    override val perceptionManager: ProjectedPerceptionManager,
) : PerceptionRuntime {
    override fun initialize() {
        lifecycleManager.create()
    }

    override fun resume() {
        lifecycleManager.resume()
    }

    override fun pause() {
        lifecycleManager.pause()
    }

    override suspend fun update(): ComparableTimeMark? {
        return lifecycleManager.update()
    }

    override fun configure(config: Config) {
        lifecycleManager.configure(config)
    }

    override fun destroy() {
        lifecycleManager.stop()
    }
}
