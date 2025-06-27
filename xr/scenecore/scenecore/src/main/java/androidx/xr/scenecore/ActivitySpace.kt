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

@file:Suppress("BanConcurrentHashMap")

package androidx.xr.scenecore

import androidx.annotation.RestrictTo
import androidx.xr.runtime.internal.ActivitySpace as RtActivitySpace
import androidx.xr.runtime.internal.JxrPlatformAdapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * ActivitySpace is an Entity used to track the system-managed pose and boundary of the volume
 * associated with this Spatialized Activity. The Application cannot directly control this volume,
 * but the system might update it in response to the User moving it or entering or exiting FullSpace
 * mode.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ActivitySpace
private constructor(rtActivitySpace: RtActivitySpace, entityManager: EntityManager) :
    BaseEntity<RtActivitySpace>(rtActivitySpace, entityManager) {

    internal companion object {
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager
        ): ActivitySpace = ActivitySpace(adapter.activitySpace, entityManager)
    }

    private val boundsListeners:
        ConcurrentMap<Consumer<Dimensions>, RtActivitySpace.OnBoundsChangedListener> =
        ConcurrentHashMap()

    /**
     * The listener registered when using the deprecated registerOnBoundsChangedListener method. We
     * keep this reference so it can be removed using the corresponding unregister method.
     */
    // TODO: b/370538244 - remove with deprecated spatial state callbacks
    private var registeredBoundsListener: Consumer<Dimensions>? = null

    /**
     * Retrieves a copy of the current bounds of this ActivitySpace.
     *
     * @return [Dimensions] representing the current bounds of this ActivitySpace.
     */
    // TODO b/370618648: remove suppression after API review is complete.
    public fun getBounds(): Dimensions = rtEntity.bounds.toDimensions()

    /**
     * Adds the given [Consumer] as a listener to be invoked when this ActivitySpace's current
     * boundary changes. [Consumer#accept(Dimensions)] will be invoked on the main thread.
     *
     * @param listener The Consumer to be invoked when this ActivitySpace's current boundary
     *   changes.
     */
    // TODO b/370618648: remove suppression after API review is complete.
    public fun addBoundsChangedListener(listener: Consumer<Dimensions>): Unit =
        addBoundsChangedListener(HandlerExecutor.mainThreadExecutor, listener)

    /**
     * Adds the given [Consumer] as a listener to be invoked when this ActivitySpace's current
     * boundary changes. [Consumer#accept(Dimensions)] will be invoked on the given executor.
     *
     * @param callbackExecutor The executor on which to invoke the listener on.
     * @param listener The Consumer to be invoked when this ActivitySpace's current boundary
     *   changes.
     */
    // TODO b/370618648: remove suppression after API review is complete.
    public fun addBoundsChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<Dimensions>
    ) {
        val rtListener: RtActivitySpace.OnBoundsChangedListener =
            RtActivitySpace.OnBoundsChangedListener { rtDimensions ->
                callbackExecutor.execute { listener.accept(rtDimensions.toDimensions()) }
            }
        boundsListeners.compute(
            listener,
            { _, _ ->
                rtEntity.addOnBoundsChangedListener(rtListener)
                rtListener
            },
        )
    }

    /**
     * Releases the given [Consumer] from receiving updates when the ActivitySpace's boundary
     * changes.
     *
     * @param listener The Consumer to be removed from receiving updates.
     */
    // TODO b/370618648: remove suppression after API review is complete.
    public fun removeBoundsChangedListener(listener: Consumer<Dimensions>): Unit {
        boundsListeners.computeIfPresent(
            listener,
            { _, rtListener ->
                rtEntity.removeOnBoundsChangedListener(rtListener)
                null // returning null from computeIfPresent removes this entry from the Map
            },
        )
    }

    /**
     * Sets a callback to be invoked when the bounds of the ActivitySpace change. The callback will
     * be dispatched on the UI thread.
     *
     * @param listener A ((Dimensions) -> Unit) callback, where Dimensions are in meters.
     */
    // TODO: b/370538244 - remove with deprecated spatial state callbacks
    @Suppress("Deprecation")
    @Deprecated(message = "use addBoundsChangedListener(Consumer<Dimensions>)")
    public fun registerOnBoundsChangedListener(listener: OnBoundsChangeListener) {
        if (registeredBoundsListener != null) unregisterOnBoundsChangedListener()
        registeredBoundsListener =
            Consumer<Dimensions> { bounds -> listener.onBoundsChanged(bounds) }
        addBoundsChangedListener(registeredBoundsListener!!)
    }

    /** Clears the listener set by [registerOnBoundsChangedListener]. */
    // TODO: b/370538244 - remove with deprecated spatial state callbacks
    @Deprecated(message = "use removeBoundsChangedListener(Consumer<Dimensions>)")
    public fun unregisterOnBoundsChangedListener() {
        if (registeredBoundsListener != null) {
            removeBoundsChangedListener(registeredBoundsListener!!)
            registeredBoundsListener = null
        }
    }

    /**
     * Registers a listener to be called when the underlying space has moved or changed.
     *
     * @param listener The listener to register if non-null, else stops listening if null.
     * @param executor The executor to run the listener on. Defaults to SceneCore executor if null.
     */
    @JvmOverloads
    @Suppress("ExecutorRegistration")
    public fun setOnSpaceUpdatedListener(
        listener: OnSpaceUpdatedListener?,
        executor: Executor? = null,
    ) {
        rtEntity.setOnSpaceUpdatedListener(
            listener?.let { { it.onSpaceUpdated() } } ?: {},
            executor
        )
    }
}

// TODO: b/370538244 - remove with deprecated spatial state callbacks
@Deprecated(message = "Use addBoundsChangedListener(Consumer<Dimensions>)")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun interface OnBoundsChangeListener {
    public fun onBoundsChanged(bounds: Dimensions) // Dimensions are in meters.
}
