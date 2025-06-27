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

package androidx.xr.runtime.internal

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.RestrictTo
import androidx.xr.runtime.math.Pose
import com.google.androidxr.splitengine.SubspaceNode
import com.google.common.util.concurrent.ListenableFuture
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * Describes the scenegraph functionality that is required from a [Runtime] implementation. It is
 * expected that these functions are only valid while the [Runtime] is in a resumed state. This is
 * not intended to be used by applications.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface JxrPlatformAdapter {
    /** Returns the Environment for the Session. */
    public val spatialEnvironment: SpatialEnvironment

    /** Get the PanelEntity associated with the main window for the Runtime. */
    public val mainPanelEntity: PanelEntity

    /** Returns the Activity Space entity at the root of the scene. */
    public val activitySpace: ActivitySpace

    /** Returns the HeadActivityPose for the session or null if it not ready. */
    public val headActivityPose: HeadActivityPose?

    /**
     * Returns the entity that represents the ActivitySpace root.
     *
     * <p>SDK's factory methods are expected to use this entity as the default parent for all
     * content entities when no parent is specified.
     */
    // TODO: b/378680989 - Remove this method.
    public val activitySpaceRootImpl: Entity

    /** Return the Spatial Capabilities set that are currently supported by the platform. */
    public val spatialCapabilities: SpatialCapabilities

    /** Returns the PerceptionSpaceActivityPose for the Session. */
    public val perceptionSpaceActivityPose: PerceptionSpaceActivityPose

    /** Returns a [SoundPoolExtensionsWrapper] instance. */
    public val soundPoolExtensionsWrapper: SoundPoolExtensionsWrapper

    /** Returns a [AudioTrackExtensionsWrapper] instance. */
    public val audioTrackExtensionsWrapper: AudioTrackExtensionsWrapper

    /** Returns a [MediaPlayerExtensionsWrapper] instance. */
    public val mediaPlayerExtensionsWrapper: MediaPlayerExtensionsWrapper

    /**
     * Returns the CameraViewActivityPose for the specified camera type or null if it is not
     * ready/available.
     */
    public fun getCameraViewActivityPose(
        @CameraViewActivityPose.CameraType cameraType: Int
    ): CameraViewActivityPose?

    /**
     * Loads glTF Asset for the given asset name from the assets folder using the Split Engine
     * route. The future returned by this method will fire listeners on the UI thread if
     * Runnable::run is supplied.
     */
    public fun loadGltfByAssetName(assetName: String): ListenableFuture<GltfModelResource>

    /**
     * Loads glTF Asset from a provided byte array. The future returned by this method will fire
     * listeners on the UI thread if Runnable::run is supplied.
     */
    // TODO(b/397746548): Add InputStream support for loading glTFs.
    // Suppressed to allow CompletableFuture.
    public fun loadGltfByByteArray(
        assetData: ByteArray,
        assetKey: String,
    ): ListenableFuture<GltfModelResource>

    /**
     * Loads an ExrImage for the given asset name from the assets folder using the Split Engine
     * route.
     */
    @SuppressWarnings("AsyncSuffixFuture")
    public fun loadExrImageByAssetName(assetName: String): ListenableFuture<ExrImageResource>

    /** Loads an ExrImage from a provided byte array using the Split Engine route. */
    // Suppressed to allow CompletableFuture.
    public fun loadExrImageByByteArray(
        assetData: ByteArray,
        assetKey: String,
    ): ListenableFuture<ExrImageResource>

    /**
     * Loads a texture resource for the given asset name or URL. The future returned by this method
     * will fire listeners on the UI thread if Runnable::run is supplied.
     */
    public fun loadTexture(
        assetName: String,
        sampler: TextureSampler,
    ): ListenableFuture<TextureResource>?

    /** Borrows the reflection texture from the currently set environment IBL. */
    public fun borrowReflectionTexture(): TextureResource?

    /** Destroys the given texture resource. */
    public fun destroyTexture(texture: TextureResource)

    /** Returns the reflection texture from the given IBL. */
    public fun getReflectionTextureFromIbl(iblToken: ExrImageResource): TextureResource?

    /**
     * Creates a water material by querying it from the system's built-in materials. The future
     * returned by this method will fire listeners on the UI thread if Runnable::run is supplied.
     */
    public fun createWaterMaterial(isAlphaMapVersion: Boolean): ListenableFuture<MaterialResource>?

    /** Destroys the given water material resource. */
    public fun destroyWaterMaterial(material: MaterialResource)

    /** Sets the reflection cube texture for the water material. */
    public fun setReflectionCube(material: MaterialResource, reflectionCube: TextureResource)

    /** Sets the normal map texture for the water material. */
    public fun setNormalMap(material: MaterialResource, normalMap: TextureResource)

    /** Sets the normal tiling for the water material. */
    public fun setNormalTiling(material: MaterialResource, normalTiling: Float)

    /** Sets the normal speed for the water material. */
    public fun setNormalSpeed(material: MaterialResource, normalSpeed: Float)

    /** Sets the alpha step multiplier for the water material. */
    public fun setAlphaStepMultiplier(material: MaterialResource, alphaStepMultiplier: Float)

    /** Sets the alpha map for the water material. */
    public fun setAlphaMap(material: MaterialResource, alphaMap: TextureResource)

    /** Sets the normal z for the water material. */
    public fun setNormalZ(material: MaterialResource, normalZ: Float)

    /** Sets the normal boundary for the water material. */
    public fun setNormalBoundary(material: MaterialResource, normalBoundary: Float)

    /**
     * A factory function to create a SceneCore GltfEntity. The parent may be the activity space or
     * GltfEntity in the scene.
     */
    public fun createGltfEntity(
        pose: Pose,
        loadedGltf: GltfModelResource,
        parentEntity: Entity,
    ): GltfEntity

    /** A factory function for an Entity which displays drawable surfaces. */
    public fun createSurfaceEntity(
        stereoMode: Int,
        canvasShape: SurfaceEntity.CanvasShape,
        pose: Pose,
        parentEntity: Entity,
    ): SurfaceEntity

    /**
     * Adds the given {@link Consumer} as a listener to be invoked when this Session's current
     * SpatialCapabilities change. {@link Consumer#accept(SpatialCapabilities)} will be invoked on
     * the given Executor.
     */
    @Suppress("ExecutorRegistration")
    public fun addSpatialCapabilitiesChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialCapabilities>,
    )

    /**
     * Releases the given {@link Consumer} from receiving updates when the Session's {@link
     * SpatialCapabilities} change.
     */
    public fun removeSpatialCapabilitiesChangedListener(listener: Consumer<SpatialCapabilities>)

    /**
     * Sets the listener to be invoked when the spatial visibility of the rendered content of the
     * entire scene (all entities, including children of anchors and activitySpace) changes within
     * the user's field of view.
     *
     * <p> This API only checks if the bounds of the renderable content are within the user's field
     * of view. It does not check if the rendered content is visible to the user. For example, if
     * the user is looking straight ahead, and there's only a single invisible child entity (alpha
     * = 0) in front of the user, this API will return SpatialVisibility.WITHIN_FOV even though the
     * user cannot see anything.
     *
     * <p>The listener is invoked on the provided executor. If the app intends to modify the UI
     * elements/views during the callback, the app should provide the thread executor that is
     * appropriate for the UI operations. For example, if the app is using the main thread to render
     * the UI, the app should provide the main thread (Looper.getMainLooper()) executor. If the app
     * is using a separate thread to render the UI, the app should provide the executor for that
     * thread.
     *
     * <p> There can only be one listener set at a time. If a new listener is set, the previous
     * listener will be released.
     *
     * @param callbackExecutor The executor to run the listener on.
     * @param listener The [Consumer] to be invoked asynchronously on the given callbackExecutor
     *   whenever the spatial visibility of the renderable content changes. The parameter passed to
     *   the Consumer’s accept method is the new value for [SpatialVisibility].
     */
    public fun setSpatialVisibilityChangedListener(
        callbackExecutor: Executor,
        listener: Consumer<SpatialVisibility>,
    )

    /** Releases the listener previously added by [setSpatialVisibilityChangedListener]. */
    public fun clearSpatialVisibilityChangedListener()

    /** A function to create a XR Runtime Entity. */
    public fun createLoggingEntity(pose: Pose): LoggingEntity

    /**
     * If the primary Activity for the Session that owns this object has focus, causes it to be
     * placed in FullSpace Mode. Otherwise, this call does nothing.
     */
    public fun requestFullSpaceMode()

    /**
     * If the primary Activity for the Session that owns this object has focus, causes it to be
     * placed in HomeSpace Mode. Otherwise, this call does nothing.
     */
    public fun requestHomeSpaceMode()

    /**
     * A factory function to create a platform PanelEntity. The parent can be any entity.
     *
     * @param context Application Context.
     * @param pose Initial pose of the panel.
     * @param view View inflating this panel.
     * @param dimensions Size of the panel in meters.
     * @param name Name of the panel.
     * @param parent Parent entity.
     */
    public fun createPanelEntity(
        context: Context,
        pose: Pose,
        view: View,
        dimensions: Dimensions,
        name: String,
        parent: Entity,
    ): PanelEntity

    /**
     * A factory function to create a platform PanelEntity. The parent can be any entity.
     *
     * @param context Application Context.
     * @param pose Initial pose of the panel.
     * @param view View inflating this panel.
     * @param pixelDimensions Dimensions for the underlying surface for the given view in pixels.
     * @param name Name of the panel.
     * @param parent Parent entity.
     */
    public fun createPanelEntity(
        context: Context,
        pose: Pose,
        view: View,
        pixelDimensions: PixelDimensions,
        name: String,
        parent: Entity,
    ): PanelEntity

    /**
     * Factory function to create ActivityPanel to launch/move activity into.
     *
     * @param pose Initial pose of the panel.
     * @param windowBoundsPx Boundary for the window
     * @param name Name of the panel.
     * @param hostActivity Activity to host the panel.
     * @param parent Parent entity.
     */
    public fun createActivityPanelEntity(
        pose: Pose,
        windowBoundsPx: PixelDimensions,
        name: String,
        hostActivity: Activity,
        parent: Entity,
    ): ActivityPanelEntity

    /**
     * A factory function to create an Anchor entity.
     *
     * @param bounds Bounds for this Anchor.
     * @param planeType Orientation of the plane to which this anchor should attach.
     * @param planeSemantic Semantic type of the plane to which this anchor should attach.
     * @param searchTimeout How long to search for an anchor. If this is Duration.ZERO, this will
     *   search for an anchor indefinitely.
     */
    public fun createAnchorEntity(
        bounds: Dimensions,
        planeType: PlaneType,
        planeSemantic: PlaneSemantic,
        searchTimeout: Duration,
    ): AnchorEntity

    /**
     * A factory function to create an Anchor entity from a {@link
     * androidx.xr.runtime.internal.Anchor}.
     *
     * @param anchor The {@link androidx.xr.runtime.internal.Anchor} to create the Anchor entity
     *   from.
     */
    public fun createAnchorEntity(anchor: Anchor): AnchorEntity

    /**
     * A factory function to create a content-less entity. This entity is used as a connection point
     * for attaching children entities and managing them (i.e. setPose()) as a group.
     *
     * @param pose Initial pose of the entity.
     * @param name Name of the entity.
     * @param parent Parent entity.
     */
    public fun createEntity(pose: Pose, name: String, parent: Entity): Entity

    /**
     * A factory function to create a SubspaceNodeEntity.
     *
     * @param subspaceNode The SubspaceNode to create the SubspaceNodeEntity from.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    public fun createSubspaceNodeEntity(
        subspaceNode: SubspaceNode,
        size: Dimensions,
    ): SubspaceNodeEntity

    /**
     * Create an Interactable component.
     *
     * @param executor Executor to use for input callbacks.
     * @param listener [JxrPlatformAdapter.InputEventListener] for this component.
     * @return InteractableComponent instance.
     */
    @Suppress("ExecutorRegistration")
    public fun createInteractableComponent(
        executor: Executor,
        listener: InputEventListener,
    ): InteractableComponent

    /**
     * Create an instance of [MovableComponent]. This component allows the user to move the entity.
     *
     * @param systemMovable A [boolean] which causes the system to automatically apply transform
     *   updates to the entity in response to user interaction.
     * @param scaleInZ A [boolean] which tells the system to update the scale of the Entity as the
     *   user moves it closer and further away. This is mostly useful for Panel auto-rescaling with
     *   Distance
     * @param anchorPlacement AnchorPlacement information for when to anchor the entity.
     * @param shouldDisposeParentAnchor A [boolean] which tells the system to dispose of the parent
     *   anchor if that entity was created by the moveable component and is moved off of it.
     * @return [MovableComponent] instance.
     */
    public fun createMovableComponent(
        systemMovable: Boolean,
        scaleInZ: Boolean,
        anchorPlacement: Set<@JvmSuppressWildcards AnchorPlacement>,
        shouldDisposeParentAnchor: Boolean,
    ): MovableComponent

    /**
     * Creates an instance of an AnchorPlacement object.
     *
     * <p>This can be used in movable components to specify the anchor placement for the entity.
     *
     * @param planeTypeFilter A set of plane types to filter for.
     * @param planeSemanticFilter A set of plane semantics to filter for.
     * @return [AnchorPlacement] instance.
     */
    public fun createAnchorPlacementForPlanes(
        planeTypeFilter: Set<@JvmSuppressWildcards PlaneType>,
        planeSemanticFilter: Set<@JvmSuppressWildcards PlaneSemantic>,
    ): AnchorPlacement

    /**
     * Create an instance of [ResizableComponent]. This component allows the user to resize the
     * entity.
     *
     * @param minimumSize Minimum size constraint.
     * @param maximumSize Maximum size constraint.
     * @return [ResizableComponent] instance.
     */
    public fun createResizableComponent(
        minimumSize: Dimensions,
        maximumSize: Dimensions,
    ): ResizableComponent

    /**
     * Create an instance of {@link PointerCaptureComponent}. This component allows the user to
     * capture and redirect to itself all input that would be received by entities other than the
     * Entity it is attached to and that entity's children.
     *
     * <p>In order to enable pointer capture, an application must be in full space and the entity it
     * is attached to must be visible.
     *
     * <p>Attach this component to the entity to enable pointer capture, detach the component to
     * restore normal input flow.
     *
     * @param executor Executor used to propagate state and input events.
     * @param stateListener Callback for updates to the state of pointer capture. Pointer capture
     *   may be temporarily lost by the application for a variety of reasons and this callback will
     *   notify of when that happens.
     * @param inputListener Callback that will receive captured [InputEvent]s
     */
    public fun createPointerCaptureComponent(
        executor: Executor,
        stateListener: PointerCaptureComponent.StateListener,
        inputListener: InputEventListener,
    ): PointerCaptureComponent

    /**
     * A factory function to recreate an Anchor entity which was persisted in a previous session.
     *
     * @param uuid The UUID of the persisted anchor.
     * @param searchTimeout How long to search for an anchor. If this is Duration.ZERO, this will
     *   search for an anchor indefinitely.
     */
    public fun createPersistedAnchorEntity(uuid: UUID, searchTimeout: Duration): AnchorEntity

    /**
     * Sets the full space mode flag to the given {@link Bundle}.
     *
     * <p>The {@link Bundle} then could be used to launch an {@link Activity} with requesting to
     * enter full space mode through {@link Activity#startActivity}. If there's a bundle used for
     * customizing how the {@link Activity} should be started by {@link ActivityOptions.toBundle} or
     * {@link androidx.core.app.ActivityOptionsCompat.toBundle}, it's suggested to use the bundle to
     * call this method.
     *
     * <p>The flag will be ignored when no {@link Intent.FLAG_ACTIVITY_NEW_TASK} is set in the
     * bundle, or it is not started from a focused Activity context.
     *
     * <p>This flag is also ignored when the {@link android.window.PROPERTY_XR_ACTIVITY_START_MODE}
     * property is set to a value other than XR_ACTIVITY_START_MODE_UNDEFINED in the
     * AndroidManifest.xml file for the activity being launched.
     *
     * @param bundle the input bundle to set with the full space mode flag.
     * @return the input {@code bundle} with the full space mode flag set.
     */
    public fun setFullSpaceMode(bundle: Bundle): Bundle

    /**
     * Sets the inherit full space mode environment flag to the given {@link Bundle}.
     *
     * <p>The {@link Bundle} then could be used to launch an {@link Activity} with requesting to
     * enter full space mode while inherit the existing environment through {@link
     * Activity#startActivity}. If there's a bundle used for customizing how the {@link Activity}
     * should be started by {@link ActivityOptions.toBundle} or {@link
     * androidx.core.app.ActivityOptionsCompat.toBundle}, it's suggested to use the bundle to call
     * this method.
     *
     * <p>When launched, the activity will be in full space mode and also inherits the environment
     * from the launching activity. If the inherited environment needs to be animated, the launching
     * activity has to continue updating the environment even after the activity is put into the
     * stopped state.
     *
     * <p>The flag will be ignored when no {@link Intent.FLAG_ACTIVITY_NEW_TASK} is set in the
     * intent, or it is not started from a focused Activity context.
     *
     * <p>The flag will also be ignored when there is no environment to inherit or the activity has
     * its own environment set already.
     *
     * <p>This flag is ignored too when the {@link android.window.PROPERTY_XR_ACTIVITY_START_MODE}
     * property is set to a value other than XR_ACTIVITY_START_MODE_UNDEFINED in the
     * AndroidManifest.xml file for the activity being launched.
     *
     * <p>For security reasons, Z testing for the new activity is disabled, and the activity is
     * always drawn on top of the inherited environment. Because Z testing is disabled, the activity
     * should not spatialize itself.
     *
     * @param bundle the input bundle to set with the inherit full space mode environment flag.
     * @return the input {@code bundle} with the inherit full space mode flag set.
     */
    public fun setFullSpaceModeWithEnvironmentInherited(bundle: Bundle): Bundle

    /**
     * Sets a preferred main panel aspect ratio for home space mode.
     *
     * <p>The ratio is only applied to the activity. If the activity launches another activity in
     * the same task, the ratio is not applied to the new activity. Also, while the activity is in
     * full space mode, the preference is temporarily removed.
     *
     * <p>If the activity's current aspect ratio differs from the {@code preferredRatio}, the panel
     * is automatically resized. This resizing preserves the panel's area. To avoid runtime
     * resizing, consider specifying the desired aspect ratio in your {@code AndroidManifest.xml}.
     * This ensures your activity launches with the preferred aspect ratio from the start.
     *
     * @param activity the activity to set the preference.
     * @param preferredRatio the aspect ratio determined by taking the panel's width over its
     *   height. A value <= 0.0f means there are no preferences.
     */
    public fun setPreferredAspectRatio(activity: Activity, preferredRatio: Float)

    /** Starts the renderer. */
    public fun startRenderer()

    /** Stops the renderer. */
    public fun stopRenderer()

    /** Disposes of the resources used by the platform adapter. */
    public fun dispose()
}
