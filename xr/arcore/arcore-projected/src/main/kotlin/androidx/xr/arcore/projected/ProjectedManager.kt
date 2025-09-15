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
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.RestrictTo
import androidx.xr.projected.ProjectedServiceBinding
import androidx.xr.runtime.Config
import androidx.xr.runtime.internal.LifecycleManager
import kotlin.coroutines.CoroutineContext
import kotlin.time.ComparableTimeMark
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Manages the lifecycle of a Projected session.
 *
 * @property activity The [Activity] instance.
 * @property perceptionManager The [ProjectedPerceptionManager] instance.
 * @property timeSource The [ProjectedTimeSource] instance.
 * @property coroutineContext The [CoroutineContext] for this manager.
 * @property testPerceptionService An optional [IProjectedPerceptionService] for testing
 */
@Suppress("NotCloseable")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ProjectedManager
internal constructor(
    private val activity: Activity,
    internal val perceptionManager: ProjectedPerceptionManager,
    internal val timeSource: ProjectedTimeSource,
    private val coroutineContext: CoroutineContext,
    private val testPerceptionService: IProjectedPerceptionService? = null,
) : LifecycleManager {
    override val config: Config = Config()
    // TODO(b/411154789): Remove once Session runtime invocations are forced to run sequentially.
    internal var running: Boolean = false
    private val serviceDeferred = CompletableDeferred<IProjectedPerceptionService>()
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = IProjectedPerceptionService.Stub.asInterface(binder)
                serviceDeferred.complete(service)
                perceptionManager.service = service
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                perceptionManager.service = null
            }
        }

    /**
     * This method implements the [LifecycleManager.create] method.
     *
     * This method must be called before any operations can be performed by the
     * [ProjectedPerceptionManager].
     */
    override fun create() {
        checkProjectedSupportedAndUpToDate(activity)
        // Other create's are blocking and this makes testing easier.
        runBlocking(coroutineContext) {
            if (testPerceptionService != null) {
                perceptionManager.service = testPerceptionService
                return@runBlocking
            }

            val isBindingPermitted =
                ProjectedServiceBinding.bindPerception(activity, serviceConnection)
            check(isBindingPermitted) {
                "Projected perception service not found or binding was not permitted."
            }
            withTimeout(SERVICE_CONNECTION_TIMEOUT_MS) { serviceDeferred.await() }
        }
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
        if (testPerceptionService == null) {
            activity.unbindService(serviceConnection)
        }
        perceptionManager.service = null
    }

    // Verify that Projected is installed and using the current version.
    internal fun checkProjectedSupportedAndUpToDate(activity: Activity) {}

    private companion object {
        /**
         * Timeout for binding to the projected service. 2 sec is what we hope the 95%ile is for
         * binding to the service is.
         */
        private const val SERVICE_CONNECTION_TIMEOUT_MS = 2000L
    }
}
