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

package androidx.xr.projected

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.RestrictTo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/** Establishes a connection to the projected service. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal object ProjectedServiceConnection {

    /**
     * Timeout for binding to the projected service. Selected value is commonly used, e.g. by the
     * JUnit rules for testing services.
     */
    private const val SERVICE_CONNECTION_TIMEOUT_MS = 5000L

    /**
     * Connects to the projected service and returns an [IProjectedService] instance.
     *
     * This method binds to the projected service and waits for the connection to be established.
     * The connection is automatically unbound when the provided [lifecycleOwner] is destroyed.
     *
     * @param context The context to use for binding to the service.
     * @param lifecycleOwner The lifecycle owner to scope the service connection to.
     * @return An [IProjectedService] instance.
     * @throws IllegalStateException if the projected service is not found or binding is not
     *   permitted.
     * @throws kotlinx.coroutines.TimeoutCancellationException if the connection times out.
     */
    internal suspend fun connect(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    ): IProjectedService {
        val serviceDeferred = CompletableDeferred<IProjectedService>()

        val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    serviceDeferred.complete(IProjectedService.Stub.asInterface(service))
                }

                override fun onServiceDisconnected(name: ComponentName?) {}
            }

        lifecycleOwner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    context.unbindService(serviceConnection)
                }
            }
        )

        val isBindingPermitted = ProjectedServiceBinding.bind(context, serviceConnection)
        if (!isBindingPermitted) {
            serviceDeferred.completeExceptionally(
                IllegalStateException("Projected service not found or binding was not permitted.")
            )
        }

        return withTimeout(SERVICE_CONNECTION_TIMEOUT_MS) { serviceDeferred.await() }
    }
}
