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

import android.app.ActivityOptions
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Helper for accessing Projected device [Context] and its features.
 *
 * Projected device is an XR device connected to an Android device (host). Host can project the
 * application content to the Projected device and let users interact with it.
 *
 * The Projected device context will ensure Projected device system services are returned, when
 * queried for system services from this object.
 *
 * Note: The application context's deviceId can switch between the Projected and host deviceId
 * depending on which activity was most recently in the foreground. Prefer using the Activity
 * context to minimize the risk of running into this problem.
 */
public object ProjectedContext {

    private const val TAG = "ProjectedContext"

    @VisibleForTesting internal const val PROJECTED_DEVICE_NAME = "ProjectionDevice"
    @VisibleForTesting internal const val PROJECTED_DISPLAY_NAME = "ProjectionDisplay"

    @VisibleForTesting
    internal const val REQUIRED_LAUNCH_FLAGS =
        (Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /**
     * Explicitly create the Projected device context from any context object.
     *
     * @throws IllegalStateException if the projected device was not found.
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public fun createProjectedDeviceContext(context: Context): Context {
        val deviceId =
            getProjectedDeviceId(context)
                ?: throw IllegalStateException("Projected device not found.")
        return context.createDeviceContext(deviceId)
    }

    /**
     * Explicitly create the host device context from any context object. The host is the device
     * that connects to a Projected device.
     *
     * If an application is using a Projected device context and it wants to use system services
     * from the host (e.g. phone), it needs to use the host device context.
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public fun createHostDeviceContext(context: Context): Context =
        context.createDeviceContext(Context.DEVICE_ID_DEFAULT)

    /**
     * Returns the name of the Projected device or null if either virtual device wasn't found or the
     * name of the virtual device wasn't set.
     *
     * @throws IllegalArgumentException If another context is used (e.g. the host context).
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public fun getProjectedDeviceName(context: Context): String? =
        // TODO: b/424812882 - Turn this into a lint check with an annotation.
        if (isProjectedDeviceContext(context)) {
            getVirtualDevice(context)?.name
        } else {
            throw IllegalArgumentException(
                "Provided context is not the Projected device context. Can't get the device name."
            )
        }

    /** Returns whether the provided context is the Projected device context. */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public fun isProjectedDeviceContext(context: Context): Boolean =
        getVirtualDevice(context)?.name?.startsWith(PROJECTED_DEVICE_NAME) == true

    /**
     * Takes an [Intent] with the description of the activity to start and returns it with added
     * flags to start the activity on the Projected device.
     *
     * @param intent The description of the activity to start.
     */
    @JvmStatic
    public fun addProjectedFlags(intent: Intent): Intent = intent.addFlags(REQUIRED_LAUNCH_FLAGS)

    /**
     * Creates [ActivityOptions] that should be used to start an activity on the Projected device.
     *
     * @param context any [Context] object. If the provided context is not a Projected device
     *   context, a Projected device context will be created automatically and used to create
     *   Projected [ActivityOptions].
     * @throws IllegalStateException if the projected display was not found or if the Projected
     *   device doesn't have any displays.
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public fun createProjectedActivityOptions(context: Context): ActivityOptions {
        val projectedDeviceContext =
            if (isProjectedDeviceContext(context)) {
                context
            } else {
                createProjectedDeviceContext(context)
            }

        val projectedDisplayIds = getProjectedDisplayIds(projectedDeviceContext)

        check(projectedDisplayIds.isNotEmpty()) { "Projected device doesn't have any displays." }

        val projectedDisplay =
            context.getSystemService(DisplayManager::class.java).displays.find {
                it.name == PROJECTED_DISPLAY_NAME && projectedDisplayIds.contains(it.displayId)
            } ?: throw IllegalStateException("No projected display found.")

        return ActivityOptions.makeBasic().setLaunchDisplayId(projectedDisplay.displayId)
    }

    /**
     * Observe whether a Projected device is connected to the host.
     *
     * This method should only be used before an Activity is created. After that, use the Activity
     * lifecycle as an Activity will be stopped when the Projected device disconnects.
     *
     * @param context The context used to access the [VirtualDeviceManager]. It can be any context
     *   object.
     * @param coroutineContext The CoroutineContext that includes CoroutineDispatcher which is where
     *   the Projected device connectivity observer is executed on.
     * @throws IllegalArgumentException if provided coroutineContext doesn't include
     *   CoroutineDispatcher.
     */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    public fun isProjectedDeviceConnected(
        context: Context,
        coroutineContext: CoroutineContext,
    ): StateFlow<Boolean> {
        val coroutineDispatcher =
            coroutineContext[CoroutineDispatcher]
                ?: throw IllegalArgumentException(
                    "CoroutineContext must contain a CoroutineDispatcher."
                )

        val isConnected = MutableStateFlow(getProjectedDeviceId(context) != null)
        val virtualDeviceManager = context.getSystemService(VirtualDeviceManager::class.java)

        val listener =
            object : VirtualDeviceManager.VirtualDeviceListener {
                override fun onVirtualDeviceCreated(deviceId: Int) {
                    isConnected.update { getProjectedDeviceId(context) != null }
                    virtualDeviceManager.unregisterVirtualDeviceListener(this)
                }
            }
        virtualDeviceManager.registerVirtualDeviceListener(
            coroutineDispatcher.asExecutor(),
            listener,
        )
        return isConnected.asStateFlow()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun getProjectedDeviceId(context: Context) =
        context
            .getSystemService(VirtualDeviceManager::class.java)
            .virtualDevices
            // TODO: b/424824481 - Replace the name matching with a better method.
            .find { it.name?.startsWith(PROJECTED_DEVICE_NAME) ?: false }
            ?.deviceId

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun getVirtualDevice(context: Context) =
        context.getSystemService(VirtualDeviceManager::class.java).virtualDevices.find {
            it.deviceId == context.deviceId
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun getProjectedDisplayIds(context: Context) =
        getVirtualDevice(context)?.displayIds ?: IntArray(size = 0)
}
