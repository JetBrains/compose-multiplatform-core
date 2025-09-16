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
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.RestrictTo
import androidx.xr.projected.ProjectedServiceBinding
import androidx.xr.runtime.Config
import androidx.xr.runtime.internal.LifecycleManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

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
    private lateinit var serviceConnection: ServiceConnection

    /**
     * This method implements the [LifecycleManager.create] method.
     *
     * This method must be called before any operations can be performed by the
     * [ProjectedPerceptionManager].
     */
    override fun create() {
        checkProjectedSupportedAndUpToDate(activity)
        if (testPerceptionService != null) {
            perceptionManager.service = testPerceptionService
            return
        }

        CoroutineScope(coroutineContext).launch {
            val binder = bindPerceptionService(activity)
        }
    }

    override fun configure(config: Config) {}

    override fun resume() {}

    override suspend fun update(): ComparableTimeMark {
        val now = timeSource.markNow()
        delay(30.milliseconds)
        return timeSource.markNow()
    }

    override fun pause() {}

    override fun stop() {
        if (testPerceptionService == null) {
            activity.unbindService(serviceConnection)
        }
        perceptionManager.service = null
    }

    internal suspend fun bindPerceptionService(context: Context): IBinder {
        return suspendCancellableCoroutine { continuation ->
            serviceConnection =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        println("ProjectedManager: onServiceConnected called")
                        val service = IProjectedPerceptionService.Stub.asInterface(binder)
                        serviceDeferred.complete(service)
                        perceptionManager.service = service
                        service.start(true /* enableVps*/, "" /* api key */)

                        // When the service connects, we resume the coroutine with the binder.
                        if (continuation.isActive) {
                            continuation.resume(binder!!)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        println("onServiceDisconnected called")
                        perceptionManager.service = null
                    }

                    override fun onBindingDied(name: ComponentName?) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Binding died for $name")
                            )
                        }
                    }
                }

            // When the coroutine is cancelled, we must unbind the service.
            continuation.invokeOnCancellation { context.unbindService(serviceConnection) }

            val isBindingPermitted =
                ProjectedServiceBinding.bindPerception(context, serviceConnection)
            check(isBindingPermitted) {
                "Projected perception service not found or binding was not permitted."
            }
        }
    }

    // Verify that Projected is installed and using the current version.
    internal fun checkProjectedSupportedAndUpToDate(activity: Activity) {}
}
