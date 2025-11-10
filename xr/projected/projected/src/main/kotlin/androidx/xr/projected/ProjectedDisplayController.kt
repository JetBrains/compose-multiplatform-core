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

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.UiContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import androidx.xr.projected.platform.IProjectedService
import java.util.function.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * Controller for the Projected device display.
 *
 * Use [create] to create an instance of this class. Use [close] to clear the instance.
 */
public class ProjectedDisplayController
private constructor(
    private val connection: ProjectedServiceConnection,
    private val projectedService: IProjectedService,
    private val engagementModeClient: EngagementModeClient,
) : AutoCloseable {

    /**
     * Convenience function to set the flag bits as as per the
     * [android.view.WindowManager.LayoutParams] flags. Flags will be cleared automatically after
     * the app stops.
     *
     * Supported flags:
     * - [android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
     *
     * If an unsupported flag is passed, this method does nothing.
     */
    @ExperimentalProjectedApi
    public fun addLayoutParamsFlags(flags: Int) {
        projectedService.addWindowFlags(flags)
    }

    /**
     * Convenience function to remove the flag bits as as per the
     * [android.view.WindowManager.LayoutParams] flags.
     *
     * Supported flags:
     * - [android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
     *
     * If an unsupported flag is passed, this method does nothing.
     */
    @ExperimentalProjectedApi
    public fun removeLayoutParamsFlags(flags: Int) {
        projectedService.clearWindowFlags(flags)
    }

    /**
     * Returns `true` if the currently connected projected device is capable of displaying content
     * to the user.
     *
     * This method does not provide any more details on what type of display it is.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun isDisplayCapable(): Boolean = projectedService.isDisplayCapable()

    /**
     * Disconnects from the service providing features for Projected devices. Methods from the
     * [ProjectedDisplayController] shouldn't be called after this.
     *
     * This method should be called in [android.app.Activity.onDestroy].
     */
    override fun close() {
        connection.disconnect()
    }

    /**
     * A [Flow] of [WindowLayoutInfo] that contains the Engagement mode.
     *
     * A [WindowLayoutInfo] value should be published when [WindowLayoutInfo.EngagementMode] has
     * changed, but the behavior is ultimately decided by the hardware implementation. It is
     * recommended to test the following scenarios:
     * * Values are emitted immediately after subscribing to this function.
     * * There is a long delay between subscribing and receiving the first value.
     * * Never receiving a value after subscription.
     *
     * @param context a [UiContext] such as an [Activity], that listens to configuration changes.
     * @throws IllegalArgumentException when [context] is not a [UiContext].
     */
    // TODO: b/456198269 - investigate if Dispatchers.Main is needed.
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun windowLayoutInfo(@UiContext context: Context): Flow<WindowLayoutInfo> =
        callbackFlow {
                val listener = Consumer { engagementModeFlags: Int ->
                    trySend(WindowLayoutInfo(engagementModeFlags))
                }
                engagementModeClient.addUpdateCallback(Runnable::run, listener)
                awaitClose { engagementModeClient.removeUpdateCallback(listener) }
            }
            .flowOn(Dispatchers.Main)

    public companion object {
        /**
         * Connects to the service providing features for Projected devices and returns the
         * [ProjectedDisplayController] when the connection is established.
         *
         * @param activity The [Activity] running on a Projected device.
         * @throws IllegalStateException if the projected service is not found or binding is not
         *   permitted
         * @throws IllegalArgumentException if provided Activity is not running on a Projected
         *   device.
         */
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @JvmStatic
        public suspend fun create(activity: Activity): ProjectedDisplayController {
            require(
                ProjectedContext.isProjectedDeviceContext(activity),
                { "Provided Activity is not running on a Projected device." },
            )
            val serviceConnection = ProjectedServiceConnection(activity)

            return ProjectedDisplayController(
                serviceConnection,
                projectedService = serviceConnection.connect(),
                EngagementModeClient(activity, Handler.createAsync(Looper.getMainLooper())),
            )
        }
    }
}
