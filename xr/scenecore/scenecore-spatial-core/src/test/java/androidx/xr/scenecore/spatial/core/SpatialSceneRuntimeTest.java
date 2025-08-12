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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;

import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.SpatialCapabilities;
import androidx.xr.runtime.math.Pose;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.perception.PerceptionLibrary;
import androidx.xr.scenecore.impl.perception.Session;
import androidx.xr.scenecore.impl.perception.exceptions.FailedToInitializeException;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeRepository;
import com.android.extensions.xr.space.ShadowSpatialCapabilities;
import com.android.extensions.xr.space.ShadowSpatialState;
import com.android.extensions.xr.space.SpatialState;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Objects;

/** Tests for {@link SpatialSceneRuntimeFactory}. */
@RunWith(RobolectricTestRunner.class)
public class SpatialSceneRuntimeTest {
    private static final int OPEN_XR_REFERENCE_SPACE_TYPE = 1;
    Activity mActivity;
    private SpatialSceneRuntime mRuntime;
    private final EntityManager mEntityManager = new EntityManager();
    private final PerceptionLibrary mPerceptionLibrary = mock(PerceptionLibrary.class);
    private final Session mSession = mock(Session.class);

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
}
