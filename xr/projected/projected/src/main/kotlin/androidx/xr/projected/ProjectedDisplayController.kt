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
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import androidx.xr.projected.platform.IProjectedService
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

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

    /** Map to convert the provided listener to the one passed to the [EngagementModeClient]. */
    private val engagementModeListeners =
        mutableMapOf<Consumer<Set<EngagementMode>>, Consumer<Int>>()

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
     * An EngagementMode value.
     *
     * The EngagementMode represents how a user is interacting with a projected application (e.g.
     * are visuals on)
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public class EngagementMode internal constructor(private val id: Int) {
        override fun toString(): String =
            when (id) {
                0 -> "VISUALS_ON"
                else -> "UNKNOWN($id)"
            }

        override fun equals(other: Any?): Boolean = (other is EngagementMode) && this.id == other.id

        override fun hashCode(): Int = id.hashCode()

        public companion object {
            /**
             * Indicates the engagement mode includes a visual presentation. When this mode is
             * active, the user can visually see the app UI on a visible window.
             */
            @JvmField public val VISUALS_ON: EngagementMode = EngagementMode(0)
        }
    }

    /**
     * Adds a callback to listen for the EngagementMode.
     *
     * The EngagementMode represents how a user is interacting with a projected application (e.g.
     * are visuals on). The callback will be called as soon as it is available.
     */
    // TODO: b/457550010 - Make EngagementMode calls thread safe.
    @RequiresApi(Build.VERSION_CODES.N)
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addEngagementModeChangedListener(
        executor: Executor = Dispatchers.Main.asExecutor(),
        listener: Consumer<Set<EngagementMode>>,
    ) {
        val convertedListener = Consumer { engagementModeFlags: Int ->
            listener.accept(convertToEngagementModeSet(engagementModeFlags))
        }
        engagementModeListeners[listener] = convertedListener
        engagementModeClient.addUpdateCallback(executor, convertedListener)
    }

    /**
     * Remove a listener to stop consuming [EngagementMode] values. If the listener has already been
     * removed then this is a no-op.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun removeEngagementModeChangedListener(listener: Consumer<Set<EngagementMode>>) {
        val convertedListener: Consumer<Int>? = this.engagementModeListeners[listener]
        convertedListener?.let {
            engagementModeClient.removeUpdateCallback(it)
            engagementModeListeners.remove(listener)
        }
    }

    /**
     * Disconnects from the service providing features for Projected devices. Methods from the
     * [ProjectedDisplayController] shouldn't be called after this. All EngagementMode changed
     * listeners will be removed when this is called.
     *
     * This method should be called in [android.app.Activity.onDestroy].
     */
    override fun close() {
        connection.disconnect()
    }

    private fun convertToEngagementModeSet(engagementModes: Int): Set<EngagementMode> {
        val engagementModeSet: MutableSet<EngagementMode> = mutableSetOf()
        if (engagementModes and EngagementModeClient.ENGAGEMENT_MODE_FLAG_VISUALS_ON != 0) {
            engagementModeSet.add(EngagementMode.VISUALS_ON)
        }
        return engagementModeSet
    }

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
        @ExperimentalProjectedApi
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
