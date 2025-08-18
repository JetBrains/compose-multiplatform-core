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

import android.app.Activity
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Config
import androidx.xr.runtime.internal.LifecycleManager
import kotlin.coroutines.CoroutineContext
import kotlin.time.ComparableTimeMark

/**
 * Manages the lifecycle of a Projected session.
 *
 * @property activity The [Activity] instance.
 * @property perceptionManager The [ProjectedPerceptionManager] instance.
 * @property timeSource The [ProjectedTimeSource] instance.
 * @property coroutineContext The [CoroutineContext] for this manager.
 */
@Suppress("NotCloseable")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ProjectedManager
internal constructor(
    private val activity: Activity,
    internal val perceptionManager: ProjectedPerceptionManager,
    internal val timeSource: ProjectedTimeSource,
    private val coroutineContext: CoroutineContext,
) : LifecycleManager {
    override val config: Config = Config()
    // TODO(b/411154789): Remove once Session runtime invocations are forced to run sequentially.
    internal var running: Boolean = false

    /**
     * This method implements the [LifecycleManager.create] method.
     *
     * This method must be called before any operations can be performed by the
     * [ArCorePerceptionManager].
     */
    override fun create() {
        throw NotImplementedError("create is currently not supported by Projected.")
    }

    override fun configure(config: Config) {
        throw NotImplementedError("configure is currently not supported by Projected.")
    }

    override fun resume() {
        throw NotImplementedError("resume is currently not supported by Projected.")
    }

    override suspend fun update(): ComparableTimeMark {
        throw NotImplementedError("update is currently not supported by Projected.")
    }

    override fun pause() {
        throw NotImplementedError("pause is currently not supported by Projected.")
    }

    override fun stop() {
        throw NotImplementedError("stop is currently not supported by Projected.")
    }

    // Verify that Projected is installed and using the current version.
    internal fun checkProjectedSupportedAndUpToDate(activity: Activity) {}

    private companion object {
        const private val PROJECTED_PACKAGE_NAME = "com.google.android.glasses.core"
    }
}
