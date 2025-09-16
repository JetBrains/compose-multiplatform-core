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

import android.app.Activity
import androidx.annotation.RestrictTo
import androidx.xr.runtime.SessionConnector
import androidx.xr.runtime.internal.Entity as RtEntity
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.LifecycleManager
import androidx.xr.runtime.internal.SpatialCapabilities as RtSpatialCapabilities
import androidx.xr.runtime.internal.SpatialModeChangeListener as RtSpatialModeChangeListener
import androidx.xr.runtime.internal.SpatialVisibility as RtSpatialVisibility
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Vector3
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Scene is the primary interface to SceneCore functionality for the application. Each spatialized
 * [Activity] must create and hold an instance of a Scene.
 *
 * Once created, the application can use the Scene object to create spatialized entities, such as
 * Widget panels and geometric models, set the background environment, and anchor content to the
 * real world.
 */
@Suppress("NotCloseable")
public class Scene : SessionConnector {

    internal val entityManager = EntityManager()

    internal lateinit var platformAdapter: JxrPlatformAdapter
        private set

    /**
     * The [SpatialEnvironment] for this scene.
     *
     * This object provides APIs to manage the XR background and passthrough settings. Use it to set
     * a custom skybox, define the 3D geometry of the environment, and control the opacity of the
     * camera passthrough feed.
     *
     * @see SpatialEnvironment
     */
    public lateinit var spatialEnvironment: SpatialEnvironment
        private set

    /**
     * The [PerceptionSpace] represents the origin of the space in which ARCore for Jetpack XR
     * provides tracking info. The transformations provided by the PerceptionSpace are only valid
     * for the call frame, as the transformation can be changed by the system at any time.
     */
    public lateinit var perceptionSpace: PerceptionSpace
        private set

    /**
     * The [ActivitySpace] is a special entity that represents the space in which the application is
     * launched. It is the default parent of all entities in the scene.
     *
     * The ActivitySpace is created automatically when the [Session] is created.
     */
    public lateinit var activitySpace: ActivitySpace
        private set

    /**
     * The [SpatialUser] represents the user within the XR scene, providing access to tracking
     * information for the user's head and eyes.
     *
     * Use it to get the following:
     * - **Head Pose**: Access [SpatialUser.head] to get the position and orientation of the user's
     *   head in the scene.
     * - **Camera Views**: Access [SpatialUser.cameraViews] to get the pose and field of view for
     *   each of the user's camera views.
     *
     * Note: Accessing properties on [SpatialUser] requires head tracking to be enabled in the
     * session [androidx.xr.runtime.Session.config].
     *
     * @see SpatialUser
     * @see Head
     * @see CameraView
     */
    public lateinit var spatialUser: SpatialUser
        private set

    /**
     * A spatialized [MainPanelEntity] associated with the "main window" for the Activity. When in
     * Home Space Mode, this is the application's "main window".
     *
     * If called multiple times, this will return the same MainPanelEntity.
     */
    public lateinit var mainPanelEntity: MainPanelEntity
        private set

    /**
     * Returns the current [SpatialCapabilities] of the Session. The set of capabilities can change
     * within a session. The returned object will not update if the capabilities change; this method
     * should be called again to get the latest set of capabilities.
     */
    public var spatialCapabilities: SpatialCapabilities = SpatialCapabilities(0)
        private set
        get() = platformAdapter.spatialCapabilities.toSpatialCapabilities()

    /**
     * The [Entity] that will be used by the default [SpatialModeChangeListener] to be placed at a
     * location provided by the system thinks is an optimal placement for content when the scene
     * enters FULL_SPACE_MANAGED mode or is re-centered. This ensures continuity of the user's
     * attention between sequential FULL_SPACE_MANAGED sessions.
     *
     * Unmovable entities are not allowed to be the key, for example, [AnchorEntity].
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public var keyEntity: Entity? = null
        private set

    /**
     * The [SpatialModeChangeListener] used to handle scenegraph updates when the spatial mode for
     * the scene changes.
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @set:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public var spatialModeChangeListener: SpatialModeChangeListener =
        /**
         * The default [spatialModeChangeListener], which translates the key entity to the
         * recommended pose when the scene encounters spatial mode change, and applies the
         * recommended scale. This default handler can be replaced with the client's own
         * [SpatialModeChangeListener] by updating the [Scene.spatialModeChangeListener] property.
         */
        object : SpatialModeChangeListener {
            override fun onSpatialModeChanged(recommendedPose: Pose, recommendedScale: Float) {
                keyEntity?.setPose(recommendedPose, Space.ACTIVITY)
                keyEntity?.setScale(recommendedScale, Space.ACTIVITY)
            }
        }

    private val spatialCapabilitiesListeners:
        ConcurrentMap<Consumer<SpatialCapabilities>, Consumer<RtSpatialCapabilities>> =
        ConcurrentHashMap()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun initialize(
        lifecycleManager: LifecycleManager,
        platformAdapter: JxrPlatformAdapter,
    ): Unit {
        this.platformAdapter = platformAdapter
        spatialEnvironment = SpatialEnvironment(platformAdapter)
        perceptionSpace = PerceptionSpace.create(platformAdapter)
        activitySpace = ActivitySpace.create(platformAdapter, entityManager)
        spatialUser = SpatialUser.create(lifecycleManager, platformAdapter)
        mainPanelEntity = MainPanelEntity.create(lifecycleManager, platformAdapter, entityManager)
        platformAdapter.spatialModeChangeListener =
            object : RtSpatialModeChangeListener {
                override fun onSpatialModeChanged(
                    recommendedPose: Pose,
                    recommendedScale: Vector3,
                ) {
                    spatialModeChangeListener.onSpatialModeChanged(
                        recommendedPose,
                        recommendedScale.x,
                    )
                }
            }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    override fun close(): Unit {
        entityManager.clear()
        removeSceneFromCache(this)
    }

    /**
     * The current clipping configuration of all panels in the Scene.
     *
     * Setting this property updates the clipping behavior.
     *
     * @see PanelClippingConfig
     */
    public var panelClippingConfig: PanelClippingConfig = PanelClippingConfig()
        set(value) {
            field = value
            platformAdapter.enablePanelDepthTest(value.isDepthTestEnabled)
        }

    /**
     * Adds the given [Consumer] as a listener to be invoked when this [Session]'s current
     * [SpatialCapabilities] change.
     *
     * @param listener The Consumer to be invoked asynchronously on the main thread executor
     *   whenever the SpatialCapabilities changes.
     */
    public fun addSpatialCapabilitiesChangedListener(
        listener: Consumer<SpatialCapabilities>
    ): Unit = addSpatialCapabilitiesChangedListener(HandlerExecutor.mainThreadExecutor, listener)

    /**
     * Adds the given [Consumer] as a listener to be invoked when this [Session]'s current
     * [SpatialCapabilities] change.
     *
     * @param callbackExecutor The [Executor] to run the listener on.
     * @param listener The Consumer to be invoked asynchronously on the given callbackExecutor
     *   whenever the SpatialCapabilities changes.
     */
    public fun addSpatialCapabilitiesChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialCapabilities>,
    ): Unit {
        // wrap the client's listener in a callback that receives & converts the platformAdapter
        // SpatialCapabilities type.
        val rtListener: Consumer<RtSpatialCapabilities> =
            Consumer<RtSpatialCapabilities> { rtCaps: RtSpatialCapabilities ->
                listener.accept(rtCaps.toSpatialCapabilities())
            }
        spatialCapabilitiesListeners.compute(
            listener,
            { _, _ ->
                platformAdapter.addSpatialCapabilitiesChangedListener(callbackExecutor, rtListener)
                rtListener
            },
        )
    }

    /**
     * Releases the given [Consumer] from receiving updates when the [Session]'s
     * [SpatialCapabilities] change.
     *
     * @param listener The Consumer to be removed. It will no longer receive change events.
     */
    public fun removeSpatialCapabilitiesChangedListener(
        listener: Consumer<SpatialCapabilities>
    ): Unit {
        spatialCapabilitiesListeners.computeIfPresent(
            listener,
            { _, rtListener ->
                platformAdapter.removeSpatialCapabilitiesChangedListener(rtListener)
                null
            },
        )
    }

    /**
     * Returns all entities of the given type or its subtypes.
     *
     * @param type the type of [Entity] to return.
     * @return a list of all entities of the given type.
     */
    public fun <T : Entity> getEntitiesOfType(type: Class<out T>): List<T> =
        entityManager.getEntitiesOfType(type)

    internal fun getEntityForRtEntity(entity: RtEntity): Entity? {
        return entityManager.getEntityForRtEntity(entity)
    }

    /**
     * Sets the listener to be invoked when the spatial visibility of the rendered content of the
     * entire scene (all entities, including children of [AnchorEntity]s and [ActivitySpace])
     * changes within the user's field of view. In Home Space Mode, the listener continues to
     * monitor the spatial visibility of the application's main panel.
     *
     * This API only checks if the bounding box of all rendered content (even if partially
     * transparent) is within the user's field of view. Content not rendered due to full
     * transparency (alpha=0) or being hidden is not considered. If the entities in the scene or any
     * of their ancestors are hidden using [Entity.setEnabled] (enabled=false) or if the entities
     * are turned fully transparent using [Entity.setAlpha] (alpha=0.0), then the SpatialVisibility
     * checks will return [SpatialVisibility.SPATIAL_VISIBILITY_OUTSIDE_FIELD_OF_VIEW].
     *
     * The listener is invoked on the provided [Executor].
     *
     * There can only be one listener set at a time. If a new listener is set, the previous listener
     * will be released.
     *
     * @param callbackExecutor The [Executor] to run the listener on.
     * @param listener The [Consumer] to be invoked asynchronously on the given callbackExecutor
     *   whenever the [SpatialVisibility] of the renderable content changes.
     */
    public fun setSpatialVisibilityChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<@SpatialVisibilityValue Int>,
    ): Unit {
        // Wrap client's listener in a callback that converts the platformAdapter's
        // SpatialVisibility.
        val rtListener =
            Consumer<RtSpatialVisibility> { rtVisibility: RtSpatialVisibility ->
                listener.accept(rtVisibility.toSpatialVisibility())
            }
        platformAdapter.setSpatialVisibilityChangedListener(callbackExecutor, rtListener)
    }

    /**
     * Sets the listener to be invoked on the main thread executor when the spatial visibility of
     * the rendered content of the entire scene (all entities, including children of [AnchorEntity]s
     * and [ActivitySpace]) changes within the user's field of view. In Home Space Mode, the
     * listener continues to monitor the spatial visibility of the application's main panel.
     *
     * This API only checks if the bounding box of all rendered content (even if partially
     * transparent) is within the user's field of view. Content not rendered due to full
     * transparency (alpha=0) or being hidden is not considered. If the entities in the scene or any
     * of their ancestors are hidden using [Entity.setEnabled] (enabled=false) or if the entities
     * are turned fully transparent using [Entity.setAlpha] (alpha=0.0), then the SpatialVisibility
     * checks will return [SpatialVisibility.SPATIAL_VISIBILITY_OUTSIDE_FIELD_OF_VIEW].
     *
     * There can only be one listener set at a time. If a new listener is set, the previous listener
     * will be released.
     *
     * @param listener The [Consumer] to be invoked asynchronously on the main thread whenever the
     *   [SpatialVisibility] of the renderable content changes.
     */
    public fun setSpatialVisibilityChangedListener(
        listener: Consumer<@SpatialVisibilityValue Int>
    ): Unit = setSpatialVisibilityChangedListener(HandlerExecutor.mainThreadExecutor, listener)

    /** Releases the listener previously added by [setSpatialVisibilityChangedListener]. */
    public fun clearSpatialVisibilityChangedListener(): Unit =
        platformAdapter.clearSpatialVisibilityChangedListener()

    /**
     * The [Entity] that will be used by the default [SpatialModeChangeListener] to be placed at a
     * location provided by the system thinks is an optimal placement for content when the scene
     * enters FULL_SPACE_MANAGED mode or is re-centered. This ensures continuity of the user's
     * attention between sequential FULL_SPACE_MANAGED sessions.
     *
     * Unmovable entities are not allowed to be the key, for example, [AnchorEntity].
     *
     * Setting null as key is allowed - in which case the default spatial mode change handler will
     * be no-op.
     *
     * @param entity the entity to set as the key.
     * @return true if the entity was successfully set as the key, false otherwise.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public fun setKeyEntity(entity: Entity?): Boolean {
        when (entity) {
            is AnchorEntity -> return false
            else -> {
                keyEntity = entity
                return true
            }
        }
    }

    /**
     * If the [Activity] has focus, causes the Activity to be placed in Full Space Mode. Otherwise,
     * this call does nothing.
     */
    public fun requestFullSpaceMode(): Unit = platformAdapter.requestFullSpaceMode()

    /**
     * If the [Activity] has focus, causes the Activity to be placed in Home Space Mode. Otherwise,
     * this call does nothing.
     */
    public fun requestHomeSpaceMode(): Unit = platformAdapter.requestHomeSpaceMode()
}
