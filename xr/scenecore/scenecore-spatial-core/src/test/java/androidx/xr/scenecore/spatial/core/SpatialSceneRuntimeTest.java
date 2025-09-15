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

package androidx.xr.scenecore.spatial.core;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.IBinder;

import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.perception.Anchor;
import androidx.xr.scenecore.impl.perception.Fov;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Plane;
import androidx.xr.scenecore.impl.perception.Session;
import androidx.xr.scenecore.impl.perception.ViewProjection;
import androidx.xr.scenecore.impl.perception.ViewProjections;
import androidx.xr.scenecore.impl.perception.exceptions.FailedToInitializeException;
import androidx.xr.scenecore.internal.ActivitySpace;
import androidx.xr.scenecore.internal.AnchorEntity;
import androidx.xr.scenecore.internal.AnchorPlacement;
import androidx.xr.scenecore.internal.AudioTrackExtensionsWrapper;
import androidx.xr.scenecore.internal.CameraViewActivityPose;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.internal.Entity;
import androidx.xr.scenecore.internal.HeadActivityPose;
import androidx.xr.scenecore.internal.InputEventListener;
import androidx.xr.scenecore.internal.InteractableComponent;
import androidx.xr.scenecore.internal.LoggingEntity;
import androidx.xr.scenecore.internal.MediaPlayerExtensionsWrapper;
import androidx.xr.scenecore.internal.MovableComponent;
import androidx.xr.scenecore.internal.PixelDimensions;
import androidx.xr.scenecore.internal.PlaneSemantic;
import androidx.xr.scenecore.internal.PlaneType;
import androidx.xr.scenecore.internal.PointerCaptureComponent;
import androidx.xr.scenecore.internal.ResizableComponent;
import androidx.xr.scenecore.internal.SoundPoolExtensionsWrapper;
import androidx.xr.scenecore.internal.SpatialCapabilities;
import androidx.xr.scenecore.internal.SpatialEnvironment;
import androidx.xr.scenecore.internal.SpatialModeChangeListener;
import androidx.xr.scenecore.internal.SpatialPointerComponent;
import androidx.xr.scenecore.internal.SpatialVisibility;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.environment.EnvironmentVisibilityState;
import com.android.extensions.xr.environment.PassthroughVisibilityState;
import com.android.extensions.xr.environment.ShadowEnvironmentVisibilityState;
import com.android.extensions.xr.environment.ShadowPassthroughVisibilityState;
import com.android.extensions.xr.node.Mat4f;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeRepository;
import com.android.extensions.xr.space.PerceivedResolution;
import com.android.extensions.xr.space.ShadowSpatialCapabilities;
import com.android.extensions.xr.space.ShadowSpatialState;
import com.android.extensions.xr.space.SpatialState;
import com.android.extensions.xr.space.VisibilityState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Tests for {@link SpatialSceneRuntimeFactory}. */
@SuppressLint("NewApi") // TODO: b/413661481 - Remove this suppression prior to JXR stable release.
@RunWith(RobolectricTestRunner.class)
public class SpatialSceneRuntimeTest {
    private static final int OPEN_XR_REFERENCE_SPACE_TYPE = 1;
    Activity mActivity;
    private SpatialSceneRuntime mRuntime;
    private final EntityManager mEntityManager = new EntityManager();
    private final PerceptionLibrary mPerceptionLibrary = mock(PerceptionLibrary.class);
    private final Session mSession = mock(Session.class);
    private final androidx.xr.scenecore.impl.perception.Plane mPlane =
            mock(androidx.xr.scenecore.impl.perception.Plane.class);
    private final Anchor mAnchor = mock(Anchor.class);
    private final IBinder mSharedAnchorToken = mock(IBinder.class);
    private final NodeRepository mNodeRepository = NodeRepository.getInstance();
    private final @NonNull XrExtensions mXrExtensions =
            Objects.requireNonNull(XrExtensionsProvider.getXrExtensions());
    private final FakeScheduledExecutorService mFakeExecutor = new FakeScheduledExecutorService();

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).create().start().get();

        ShadowXrExtensions.extract(mXrExtensions)
                .setOpenXrWorldSpaceType(OPEN_XR_REFERENCE_SPACE_TYPE);
        when(mPerceptionLibrary.initSession(mActivity, OPEN_XR_REFERENCE_SPACE_TYPE, mFakeExecutor))
                .thenReturn(immediateFuture(mSession));
        when(mPerceptionLibrary.getActivity()).thenReturn(mActivity);

        mRuntime =
                SpatialSceneRuntime.create(
                        mActivity,
                        mFakeExecutor,
                        mXrExtensions,
                        mEntityManager,
                        mPerceptionLibrary,
                        false);
    }

    @After
    public void tearDown() {
        // Dispose the runtime between test cases to clean up lingering references.
        try {
            mRuntime.dispose();
        } catch (NullPointerException e) {
            // Tests which already call dispose will cause a NPE here due to Activity being null
            // when detaching from the scene.
        }
        mRuntime = null;
    }

    @Test
    public void sceneRuntime_setUpSceneRootAndTaskLeashNodes() {
        Node rootNode = mRuntime.getSceneRootNode();
        Node taskWindowLeashNode = mRuntime.getTaskWindowLeashNode();

        assertThat(mNodeRepository.getName(rootNode))
                .isEqualTo("SpatialSceneAndActivitySpaceRootNode");
        assertThat(mNodeRepository.getName(taskWindowLeashNode))
                .isEqualTo("MainPanelAndTaskWindowLeashNode");
        assertThat(mNodeRepository.getParent(taskWindowLeashNode)).isEqualTo(rootNode);
    }

    @Test
    public void initRuntimePerceptionFailure() {
        ListenableFuture<Session> sessionFuture =
                immediateFailedFuture(
                        new FailedToInitializeException("Failed to initialize a session."));
        when(mPerceptionLibrary.initSession(mActivity, OPEN_XR_REFERENCE_SPACE_TYPE, mFakeExecutor))
                .thenReturn(sessionFuture);

        mRuntime =
                SpatialSceneRuntime.create(
                        mActivity,
                        mFakeExecutor,
                        mXrExtensions,
                        new EntityManager(),
                        mPerceptionLibrary,
                        false);

        // The perception library failed to initialize a session, but the runtime should still be
        // created.
        assertThat(mRuntime).isNotNull();
    }

    @Test
    public void getEnvironment_returnsEnvironment() {
        SpatialEnvironment environment = mRuntime.getSpatialEnvironment();
        assertThat(environment).isNotNull();
    }

    @Test
    public void getActivitySpace_returnsEntity() {
        ActivitySpace activitySpace = mRuntime.getActivitySpace();

        assertThat(activitySpace).isNotNull();

        // Verify that there is an underlying extension node.
        ActivitySpaceImpl activitySpaceImpl = (ActivitySpaceImpl) activitySpace;
        assertThat(activitySpaceImpl.getNode()).isNotNull();
    }

    @Test
    public void getHeadActivityPose_returnsNullIfNotReady() {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.getHeadPose()).thenReturn(null);
        HeadActivityPose headActivityPose = mRuntime.getHeadActivityPose();

        assertThat(headActivityPose).isNull();
    }

    @Test
    public void getHeadActivityPose_returnsActivityPose() {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.getHeadPose())
                .thenReturn(androidx.xr.scenecore.impl.perception.Pose.identity());
        HeadActivityPose headActivityPose = mRuntime.getHeadActivityPose();

        assertThat(headActivityPose).isNotNull();
    }

    @Test
    public void getCameraViewActivityPose_returnsNullIfNotReady() {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.getStereoViews()).thenReturn(new ViewProjections(null, null));

        CameraViewActivityPose leftCameraViewActivityPose =
                mRuntime.getCameraViewActivityPose(
                        CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE);
        CameraViewActivityPose rightCameraViewActivityPose =
                mRuntime.getCameraViewActivityPose(
                        CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE);

        assertThat(leftCameraViewActivityPose).isNull();
        assertThat(rightCameraViewActivityPose).isNull();
    }

    @Test
    public void getLeftCameraViewActivityPose_returnsActivityPose() {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        ViewProjection viewProjection =
                new ViewProjection(
                        androidx.xr.scenecore.impl.perception.Pose.identity(), new Fov(0, 0, 0, 0));
        when(mSession.getStereoViews())
                .thenReturn(new ViewProjections(viewProjection, viewProjection));
        CameraViewActivityPose cameraViewActivityPose =
                mRuntime.getCameraViewActivityPose(
                        CameraViewActivityPose.CameraType.CAMERA_TYPE_LEFT_EYE);

        assertThat(cameraViewActivityPose).isNotNull();
    }

    @Test
    public void getRightCameraViewActivityPose_returnsActivityPose() {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        ViewProjection viewProjection =
                new ViewProjection(
                        androidx.xr.scenecore.impl.perception.Pose.identity(), new Fov(0, 0, 0, 0));
        when(mSession.getStereoViews())
                .thenReturn(new ViewProjections(viewProjection, viewProjection));
        CameraViewActivityPose cameraViewActivityPose =
                mRuntime.getCameraViewActivityPose(
                        CameraViewActivityPose.CameraType.CAMERA_TYPE_RIGHT_EYE);

        assertThat(cameraViewActivityPose).isNotNull();
    }

    @Test
    public void getUnknownCameraViewActivityPose_returnsEmptyOptional() {
        CameraViewActivityPose cameraViewActivityPose = mRuntime.getCameraViewActivityPose(555);

        assertThat(cameraViewActivityPose).isNull();
    }

    @Test
    public void onSpatialStateChanged_setsSpatialCapabilities() {
        SpatialState spatialState = ShadowSpatialState.create();
        ShadowSpatialState.extract(spatialState)
                .setSpatialCapabilities(
                        ShadowSpatialCapabilities.create(
                                com.android.extensions.xr.space.SpatialCapabilities
                                        .SPATIAL_UI_CAPABLE));
        mRuntime.onSpatialStateChanged(spatialState);

        SpatialCapabilities caps = mRuntime.getSpatialCapabilities();
        assertThat(caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_UI)).isTrue();
        assertThat(caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_3D_CONTENT)).isFalse();
        assertThat(caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_PASSTHROUGH_CONTROL))
                .isFalse();
        assertThat(caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_APP_ENVIRONMENT))
                .isFalse();
        assertThat(caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_SPATIAL_AUDIO))
                .isFalse();
        assertThat(caps.hasCapability(SpatialCapabilities.SPATIAL_CAPABILITY_EMBED_ACTIVITY))
                .isFalse();
    }

    @Test
    public void onSpatialStateChanged_setsEnvironmentVisibility() {
        SpatialEnvironment environment = mRuntime.getSpatialEnvironment();
        assertThat(environment.isPreferredSpatialEnvironmentActive()).isFalse();

        SpatialState state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setEnvironmentVisibilityState(
                        ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.APP_VISIBLE));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.isPreferredSpatialEnvironmentActive()).isTrue();

        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setEnvironmentVisibilityState(
                        ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.INVISIBLE));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.isPreferredSpatialEnvironmentActive()).isFalse();

        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setEnvironmentVisibilityState(
                        ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.HOME_VISIBLE));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.isPreferredSpatialEnvironmentActive()).isFalse();
    }

    @Test
    public void onSpatialStateChanged_callsEnvironmentListenerOnlyForChanges() {
        SpatialEnvironment environment = mRuntime.getSpatialEnvironment();
        @SuppressWarnings(value = "unchecked")
        Consumer<Boolean> listener = (Consumer<Boolean>) mock(Consumer.class);

        environment.addOnSpatialEnvironmentChangedListener(directExecutor(), listener);

        assertThat(environment.isPreferredSpatialEnvironmentActive()).isFalse();

        // The first spatial state should always fire the listener
        SpatialState state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setEnvironmentVisibilityState(
                        ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.APP_VISIBLE));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        verify(listener).accept(true);

        // The second spatial state should also fire the listener since it's a different state
        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setEnvironmentVisibilityState(
                        ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.INVISIBLE));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.isPreferredSpatialEnvironmentActive()).isFalse();
        verify(listener).accept(false);

        // The third spatial state should not fire the listener since it is the same as the last
        // state.
        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setEnvironmentVisibilityState(
                        ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.INVISIBLE));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.isPreferredSpatialEnvironmentActive()).isFalse();
        verify(listener, times(2))
                .accept(any()); // Verify the listener was not called a third time.
    }

    @Test
    public void onSpatialStateChanged_setsPassthroughOpacity() {
        SpatialEnvironment environment = mRuntime.getSpatialEnvironment();
        assertThat(environment.getCurrentPassthroughOpacity()).isZero();

        SpatialState state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.APP, 0.4f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isEqualTo(0.4f);

        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.HOME, 0.5f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isEqualTo(0.5f);

        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.SYSTEM, 0.9f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isEqualTo(0.9f);

        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.DISABLED, 0.0f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isZero();
    }

    @Test
    public void onSpatialStateChanged_callsPassthroughListenerOnlyForChanges() {
        SpatialEnvironment environment = mRuntime.getSpatialEnvironment();
        @SuppressWarnings(value = "unchecked")
        Consumer<Float> listener = (Consumer<Float>) mock(Consumer.class);

        environment.addOnPassthroughOpacityChangedListener(directExecutor(), listener);

        assertThat(environment.getCurrentPassthroughOpacity()).isZero();

        // The first spatial state should always fire the listener
        SpatialState state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.APP, 1.0f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        verify(listener).accept(1.0f);

        // The second spatial state should also fire the listener even if only the opacity changes
        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.APP, 0.5f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isEqualTo(0.5f);

        // The third spatial state should also fire the listener even if only the visibility state
        // changes, but getCurrentPassthroughOpacity() returns the same value as the last state.
        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.HOME, 0.5f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isEqualTo(0.5f);
        verify(listener, times(2))
                .accept(0.5f); // Verify it was called a second time with this value.

        // The fourth spatial state should not fire the listener since it is the same as the last
        // state.
        state = ShadowSpatialState.create();
        ShadowSpatialState.extract(state)
                .setPassthroughVisibilityState(
                        ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.HOME, 0.5f));
        ShadowXrExtensions.extract(mXrExtensions).sendSpatialState(mActivity, state);
        assertThat(environment.getCurrentPassthroughOpacity()).isEqualTo(0.5f);
        verify(listener, times(3))
                .accept(any()); // Verify the listener was not called a fourth time.
    }

    private Node getNode(Entity entity) {
        return ((AndroidXrEntity) entity).getNode();
    }

    private Entity createGroupEntity() {
        return createGroupEntity(new Pose());
    }

    private Entity createGroupEntity(Pose pose) {
        return mRuntime.createGroupEntity(pose, "test", mRuntime.getActivitySpace());
    }

    @Test
    public void createGroupEntity_returnsEntity() throws Exception {
        assertThat(createGroupEntity()).isNotNull();
    }

    @Test
    public void groupEntity_hasActivitySpaceRootImplAsParentByDefault() throws Exception {
        Entity entity = createGroupEntity();
        assertThat(entity.getParent()).isEqualTo(mRuntime.getActivitySpace());
    }

    @Test
    public void groupEntityAddChildren_addsChildren() throws Exception {
        Entity childEntity1 = createGroupEntity();
        Entity childEntity2 = createGroupEntity();
        Entity parentEntity = createGroupEntity();

        parentEntity.addChild(childEntity1);

        assertThat(parentEntity.getChildren()).containsExactly(childEntity1);

        parentEntity.addChildren(ImmutableList.of(childEntity2));

        assertThat(childEntity1.getParent()).isEqualTo(parentEntity);
        assertThat(childEntity2.getParent()).isEqualTo(parentEntity);
        assertThat(parentEntity.getChildren()).containsExactly(childEntity1, childEntity2);

        Node childNode1 = getNode(childEntity1);
        assertThat(mNodeRepository.getParent(childNode1)).isEqualTo(getNode(parentEntity));
        Node childNode2 = getNode(childEntity2);
        assertThat(mNodeRepository.getParent(childNode2)).isEqualTo(getNode(parentEntity));
    }

    @Test
    public void createLoggingEntity_returnsEntity() {
        Pose pose = new Pose();
        LoggingEntity loggingEntity = mRuntime.createLoggingEntity(pose);
        Pose updatedPose =
                new Pose(
                        new Vector3(1f, pose.getTranslation().getY(), pose.getTranslation().getZ()),
                        pose.getRotation());
        loggingEntity.setPose(updatedPose);
    }

    @Test
    public void loggingEntitySetParent() {
        Pose pose = new Pose();
        LoggingEntity childEntity = mRuntime.createLoggingEntity(pose);
        LoggingEntity parentEntity = mRuntime.createLoggingEntity(pose);

        childEntity.setParent(parentEntity);
        parentEntity.addChild(childEntity);

        assertThat(childEntity.getParent()).isEqualTo(parentEntity);
        assertThat(parentEntity.getParent()).isEqualTo(null);
        assertThat(childEntity.getChildren()).isEmpty();
        assertThat(parentEntity.getChildren()).containsExactly(childEntity);
    }

    @Test
    public void loggingEntityUpdateParent() {
        Pose pose = new Pose();
        LoggingEntity childEntity = mRuntime.createLoggingEntity(pose);
        LoggingEntity parentEntity1 = mRuntime.createLoggingEntity(pose);
        LoggingEntity parentEntity2 = mRuntime.createLoggingEntity(pose);

        childEntity.setParent(parentEntity1);

        assertThat(childEntity.getParent()).isEqualTo(parentEntity1);
        assertThat(parentEntity1.getChildren()).containsExactly(childEntity);
        assertThat(parentEntity2.getChildren()).isEmpty();

        childEntity.setParent(parentEntity2);

        assertThat(childEntity.getParent()).isEqualTo(parentEntity2);
        assertThat(parentEntity2.getChildren()).containsExactly(childEntity);
        assertThat(parentEntity1.getChildren()).isEmpty();
    }

    @Test
    public void createAnchorEntity_returnsAndInitsAnchor() throws Exception {
        Dimensions anchorDimensions = new Dimensions(2f, 5f, 0f);
        androidx.xr.scenecore.impl.perception.Pose perceptionPose =
                androidx.xr.scenecore.impl.perception.Pose.identity();
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.getAllPlanes()).thenReturn(ImmutableList.of(mPlane));
        when(mPlane.getData(any()))
                .thenReturn(
                        new Plane.PlaneData(
                                perceptionPose,
                                3.0f,
                                5.0f,
                                Plane.Type.VERTICAL.intValue,
                                Plane.Label.WALL.intValue));
        when(mPlane.createAnchor(eq(perceptionPose), any())).thenReturn(mAnchor);
        when(mAnchor.getAnchorToken()).thenReturn(mSharedAnchorToken);

        AnchorEntity anchorEntity =
                mRuntime.createAnchorEntity(
                        anchorDimensions, PlaneType.VERTICAL, PlaneSemantic.WALL, Duration.ZERO);

        assertThat(anchorEntity).isNotNull();
        assertThat(anchorEntity.getState()).isEqualTo(AnchorEntity.State.ANCHORED);
    }

    @Test
    public void createPersistedAnchorEntity_returnsEntityInNominalCase() throws Exception {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.createAnchorFromUuid(any())).thenReturn(mAnchor);

        assertThat(
                        mRuntime.createPersistedAnchorEntity(
                                UUID.randomUUID(), /* searchTimeout= */ Duration.ofSeconds(1)))
                .isNotNull();
    }

    @Test
    public void createPersistedAnchorEntity_returnsEntityForNullSession() throws Exception {
        when(mPerceptionLibrary.getSession()).thenReturn(null);

        assertThat(
                        mRuntime.createPersistedAnchorEntity(
                                UUID.randomUUID(), /* searchTimeout= */ Duration.ofSeconds(1)))
                .isNotNull();
    }

    @Test
    public void createPersistedAnchorEntity_returnsEntityForNullAnchor() throws Exception {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.createAnchorFromUuid(any())).thenReturn(null);

        assertThat(
                        mRuntime.createPersistedAnchorEntity(
                                UUID.randomUUID(), /* searchTimeout= */ Duration.ofSeconds(1)))
                .isNotNull();
    }

    @Test
    public void createPersistedAnchorEntity_returnsEntityForNullAnchorToken() throws Exception {
        when(mPerceptionLibrary.getSession()).thenReturn(mSession);
        when(mSession.createAnchorFromUuid(any())).thenReturn(mAnchor);
        when(mAnchor.getAnchorToken()).thenReturn(null);
        UUID uuid = UUID.randomUUID();

        assertThat(
                        mRuntime.createPersistedAnchorEntity(
                                uuid, /* searchTimeout= */ Duration.ofSeconds(1)))
                .isNotNull();
        verify(mPerceptionLibrary, times(3)).getSession();
        verify(mSession).createAnchorFromUuid(uuid);
        verify(mAnchor).getAnchorToken();
    }

    @Test
    public void spatialStateChangeHandler_invokedWhenSpatialStateChangesToFSM() {
        SpatialState spatialState = ShadowSpatialState.create();
        SpatialModeChangeListener mockSpatialModeChangeListener =
                mock(SpatialModeChangeListener.class);
        mRuntime.setSpatialModeChangeListener(mockSpatialModeChangeListener);
        ShadowSpatialState.extract(spatialState)
                .setSpatialCapabilities(ShadowSpatialCapabilities.createAll());
        ShadowSpatialState.extract(spatialState).setSceneParentTransform(new Mat4f(new float[16]));
        mRuntime.onSpatialStateChanged(spatialState);

        verify(mockSpatialModeChangeListener).onSpatialModeChanged(any(), any());
    }

    private void sendVisibilityState(ShadowXrExtensions shadowXrExtensions, int visibilityState) {
        sendVisibilityState(shadowXrExtensions, visibilityState, 1, 1);
    }

    private void sendVisibilityState(ShadowXrExtensions shadowXrExtensions, int width, int height) {
        sendVisibilityState(shadowXrExtensions, VisibilityState.FULLY_VISIBLE, width, height);
    }

    private void sendVisibilityState(
            ShadowXrExtensions shadowXrExtensions, int visibilityState, int width, int height) {
        shadowXrExtensions.sendVisibilityState(
                mActivity,
                new VisibilityState(visibilityState, new PerceivedResolution(width, height)));
    }

    @Test
    public void setSpatialVisibilityChangedListener_callsExtensions() {
        @SuppressWarnings(value = "unchecked")
        Consumer<SpatialVisibility> mockListener =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockListener);
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);

        // VISIBLE
        sendVisibilityState(shadowXrExtensions, VisibilityState.FULLY_VISIBLE);
        verify(mockListener).accept(new SpatialVisibility(SpatialVisibility.WITHIN_FOV));

        // PARTIALLY_VISIBLE
        sendVisibilityState(shadowXrExtensions, VisibilityState.PARTIALLY_VISIBLE);
        verify(mockListener).accept(new SpatialVisibility(SpatialVisibility.PARTIALLY_WITHIN_FOV));

        // OUTSIDE_OF_FOV
        sendVisibilityState(shadowXrExtensions, VisibilityState.NOT_VISIBLE);
        verify(mockListener).accept(new SpatialVisibility(SpatialVisibility.OUTSIDE_FOV));

        // UNKNOWN
        sendVisibilityState(shadowXrExtensions, VisibilityState.UNKNOWN);
        verify(mockListener).accept(new SpatialVisibility(SpatialVisibility.UNKNOWN));
    }

    @Test
    public void setSpatialVisibilityChangedListener_replacesExistingListenerOnSecondCall() {
        @SuppressWarnings(value = "unchecked")
        Consumer<SpatialVisibility> mockListener1 =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        @SuppressWarnings(value = "unchecked")
        Consumer<SpatialVisibility> mockListener2 =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);

        // Listener 1 is set and called once.
        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockListener1);
        sendVisibilityState(shadowXrExtensions, VisibilityState.FULLY_VISIBLE);
        verify(mockListener1).accept(new SpatialVisibility(SpatialVisibility.WITHIN_FOV));
        verify(mockListener2, never()).accept(new SpatialVisibility(SpatialVisibility.WITHIN_FOV));

        // Listener 2 is set and called once. Listener 1 is not called again.
        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockListener2);
        sendVisibilityState(shadowXrExtensions, VisibilityState.NOT_VISIBLE);
        verify(mockListener2).accept(new SpatialVisibility(SpatialVisibility.OUTSIDE_FOV));
        verify(mockListener1, never()).accept(new SpatialVisibility(SpatialVisibility.OUTSIDE_FOV));
    }

    @Test
    public void setSpatialVisibilityChangedListener_handlesException() {
        // the subscription method throws an exception if the executor or listener are null.
        // No assert needed, the test will fail if the exception is not handled.
        mRuntime.setSpatialVisibilityChangedListener(null, null);
    }

    @Test
    public void clearSpatialVisibilityChangedListener_stopsSpatialVisibilityCallbacks() {
        @SuppressWarnings(value = "unchecked")
        Consumer<SpatialVisibility> mockListener =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        // Verify that the callback is called once when the visibility changes.
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);
        sendVisibilityState(shadowXrExtensions, VisibilityState.FULLY_VISIBLE);
        verify(mockListener).accept(any());

        // Clear the listener and verify that the callback is not called a second time.
        mRuntime.clearSpatialVisibilityChangedListener();
        sendVisibilityState(shadowXrExtensions, VisibilityState.NOT_VISIBLE);
        sendVisibilityState(shadowXrExtensions, VisibilityState.PARTIALLY_VISIBLE);
        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isFalse();
        verify(mockListener).accept(any());
    }

    @Test
    public void clearSpatialVisibilityChangedListener_noOpWhenNoListener() {
        // SpatialVisibilityChangedListener is nullable.
        // No assert needed, the test will fail if an unhandled exception is thrown.
        mRuntime.clearSpatialVisibilityChangedListener();
    }

    @Test
    public void dispose_closesSpatialVisibilityAndPerceivedResolutionSubscription() {
        @SuppressWarnings(value = "unchecked")
        Consumer<SpatialVisibility> mockSpatialVisListener =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        @SuppressWarnings(value = "unchecked")
        Consumer<PixelDimensions> mockPerceivedResListener =
                (Consumer<PixelDimensions>) mock(Consumer.class);

        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockSpatialVisListener);
        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockPerceivedResListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        // Verify that the callback is called once when the visibility changes.
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);
        sendVisibilityState(shadowXrExtensions, VisibilityState.FULLY_VISIBLE);

        verify(mockSpatialVisListener).accept(any());
        verify(mockPerceivedResListener).accept(any());

        // Ensure dispose() clears the listener that the callbacks are not called a second time.
        mRuntime.dispose();

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isFalse();

        sendVisibilityState(shadowXrExtensions, VisibilityState.NOT_VISIBLE);
        sendVisibilityState(shadowXrExtensions, VisibilityState.PARTIALLY_VISIBLE);

        verify(mockSpatialVisListener).accept(any());
        verify(mockPerceivedResListener).accept(any());
    }

    @Test
    public void clearSpatialVisibilityChangedListener_doesNotStopPerceivedResolutionListener() {
        @SuppressWarnings("unchecked")
        Consumer<SpatialVisibility> mockSpatialListener =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockPerceivedResListener =
                (Consumer<PixelDimensions>) mock(Consumer.class);

        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockSpatialListener);
        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockPerceivedResListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        mRuntime.clearSpatialVisibilityChangedListener();

        // Perceived resolution listener is still active, so callback should remain registered.
        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);
        sendVisibilityState(shadowXrExtensions, SpatialVisibility.WITHIN_FOV, 10, 20);

        verify(mockSpatialListener, never()).accept(any());
        verify(mockPerceivedResListener).accept(any());
    }

    @Test
    public void addPerceivedResolutionChangedListener_registersCombinedCallbackFirstTime() {
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockListener = (Consumer<PixelDimensions>) mock(Consumer.class);
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isFalse();

        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        verify(mockListener, never()).accept(any());

        sendVisibilityState(shadowXrExtensions, 10, 20);

        verify(mockListener).accept(new PixelDimensions(10, 20));
    }

    @Test
    public void removePerceivedResolutionChangedListener_clearsCombinedCallbackIfLastListener() {
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockListener = (Consumer<PixelDimensions>) mock(Consumer.class);
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);

        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        sendVisibilityState(shadowXrExtensions, 10, 20);

        verify(mockListener).accept(new PixelDimensions(10, 20));

        mRuntime.removePerceivedResolutionChangedListener(mockListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isFalse();

        // It shouldn't be called a second time
        sendVisibilityState(shadowXrExtensions, 10, 20);

        verify(mockListener, times(1)).accept(new PixelDimensions(10, 20));
    }

    @Test
    public void removePerceivedResolutionChangedListener_doesNotStopSpatialListener() {
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);
        @SuppressWarnings("unchecked")
        Consumer<SpatialVisibility> mockSpatialListener =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockPerceivedResListener =
                (Consumer<PixelDimensions>) mock(Consumer.class);

        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockSpatialListener);
        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockPerceivedResListener);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        mRuntime.removePerceivedResolutionChangedListener(mockPerceivedResListener);

        // Spatial listener still active, so callback should remain registered.
        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        sendVisibilityState(shadowXrExtensions, SpatialVisibility.WITHIN_FOV, 10, 20);

        verify(mockSpatialListener).accept(any());
        verify(mockPerceivedResListener, never()).accept(any());
    }

    @Test
    public void removePerceivedResolutionChangedListener_doesNotStopAnotherPerceivedResListener() {
        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockListener1 = (Consumer<PixelDimensions>) mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockListener2 = (Consumer<PixelDimensions>) mock(Consumer.class);

        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockListener1);
        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockListener2);

        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        mRuntime.removePerceivedResolutionChangedListener(mockListener1);

        // mockListener2 still active, so callback should remain registered.
        assertThat(mRuntime.mIsExtensionVisibilityStateCallbackRegistered).isTrue();

        sendVisibilityState(shadowXrExtensions, 10, 20);

        verify(mockListener2).accept(any());
        verify(mockListener1, never()).accept(any());
    }

    @Test
    public void combinedCallback_dispatchesToBothListenersCorrectly() {
        @SuppressWarnings("unchecked")
        Consumer<SpatialVisibility> mockSpatialListener =
                (Consumer<SpatialVisibility>) mock(Consumer.class);
        @SuppressWarnings("unchecked")
        Consumer<PixelDimensions> mockPerceivedResListener =
                (Consumer<PixelDimensions>) mock(Consumer.class);

        mRuntime.setSpatialVisibilityChangedListener(directExecutor(), mockSpatialListener);
        mRuntime.addPerceivedResolutionChangedListener(directExecutor(), mockPerceivedResListener);

        ShadowXrExtensions shadowXrExtensions = ShadowXrExtensions.extract(mXrExtensions);
        sendVisibilityState(shadowXrExtensions, SpatialVisibility.OUTSIDE_FOV, 30, 40);

        verify(mockSpatialListener)
                .accept(eq(new SpatialVisibility(SpatialVisibility.OUTSIDE_FOV)));
        verify(mockPerceivedResListener).accept(eq(new PixelDimensions(30, 40)));
    }

    @Test
    public void requestHomeSpaceMode_callsExtensions() {
        mRuntime.requestHomeSpaceMode();
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getSpaceMode(mActivity))
                .isEqualTo(ShadowXrExtensions.SpaceMode.HOME_SPACE);
    }

    @Test
    public void requestFullSpaceMode_callsExtensions() {
        mRuntime.requestFullSpaceMode();
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getSpaceMode(mActivity))
                .isEqualTo(ShadowXrExtensions.SpaceMode.FULL_SPACE);
    }

    @Test
    public void setFullSpaceMode_callsExtensions() {
        Bundle bundle = Bundle.EMPTY;
        bundle = mRuntime.setFullSpaceMode(bundle);
        // TODO: b/440191514 - Change to assertThat(bundle).isNotEqualTo(Bundle.EMPTY);
        assertThat(bundle).isNotNull();
    }

    @Test
    public void setFullSpaceModeWithEnvironmentInherited_callsExtensions() {
        Bundle bundle = Bundle.EMPTY;
        bundle = mRuntime.setFullSpaceModeWithEnvironmentInherited(bundle);
        // TODO: b/440191514 - Change to assertThat(bundle).isNotEqualTo(Bundle.EMPTY);
        assertThat(bundle).isNotNull();
    }

    @Test
    public void setPreferredAspectRatio_callsExtensions() {
        mRuntime.setPreferredAspectRatio(mActivity, 1.23f);
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getPreferredAspectRatio(mActivity))
                .isEqualTo(1.23f);
    }

    @Test
    public void sceneRuntime_getSoundPoolExtensionsWrapper() {
        SoundPoolExtensionsWrapper extensions = mRuntime.getSoundPoolExtensionsWrapper();

        assertThat(extensions).isNotNull();
    }

    @Test
    public void sceneRuntime_getAudioTrackExtensionsWrapper() {
        AudioTrackExtensionsWrapper extensions = mRuntime.getAudioTrackExtensionsWrapper();

        assertThat(extensions).isNotNull();
    }

    @Test
    public void sceneRuntime_getMediaPlayerExtensionsWrapper() {
        MediaPlayerExtensionsWrapper extensions = mRuntime.getMediaPlayerExtensionsWrapper();

        assertThat(extensions).isNotNull();
    }

    @Test
    public void createInteractableComponent_returnsComponent() {
        InputEventListener mockConsumer = mock(InputEventListener.class);
        InteractableComponent interactableComponent =
                mRuntime.createInteractableComponent(directExecutor(), mockConsumer);
        assertThat(interactableComponent).isNotNull();
    }

    @Test
    public void createAnchorPlacement_returnsAnchorPlacement() {
        AnchorPlacement anchorPlacement =
                mRuntime.createAnchorPlacementForPlanes(
                        ImmutableSet.of(PlaneType.ANY), ImmutableSet.of(PlaneSemantic.ANY));
        assertThat(anchorPlacement).isNotNull();
    }

    @Test
    public void createMovableComponent_returnsComponent() {
        MovableComponent movableComponent =
                mRuntime.createMovableComponent(true, true, new HashSet<AnchorPlacement>(), true);
        assertThat(movableComponent).isNotNull();
    }

    @Test
    public void createResizableComponent_returnsComponent() {
        ResizableComponent resizableComponent =
                mRuntime.createResizableComponent(
                        new Dimensions(0f, 0f, 0f), new Dimensions(5f, 5f, 5f));
        assertThat(resizableComponent).isNotNull();
    }

    @Test
    public void createPointerCaptureComponent_returnsComponent() {
        PointerCaptureComponent pointerCaptureComponent =
                mRuntime.createPointerCaptureComponent(null, (inputEvent) -> {}, (state) -> {});
        assertThat(pointerCaptureComponent).isNotNull();
    }

    @Test
    public void createSpatialPointerComponent_returnsComponent() {
        SpatialPointerComponent pointerComponent = mRuntime.createSpatialPointerComponent();
        assertThat(pointerComponent).isNotNull();
    }
}
