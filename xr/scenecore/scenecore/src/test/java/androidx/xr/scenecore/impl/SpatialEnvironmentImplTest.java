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

package androidx.xr.scenecore.impl;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;

import androidx.xr.runtime.internal.MaterialResource;
import androidx.xr.runtime.internal.SpatialEnvironment;
import androidx.xr.runtime.internal.SpatialEnvironment.SetPassthroughOpacityPreferenceResult;
import androidx.xr.runtime.internal.SpatialEnvironment.SetSpatialEnvironmentPreferenceResult;
import androidx.xr.runtime.internal.SpatialEnvironment.SpatialEnvironmentPreference;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.testing.FakeImpressApi;
import androidx.xr.scenecore.testing.FakeImpressApi.MaterialData;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.environment.EnvironmentVisibilityState;
import com.android.extensions.xr.environment.PassthroughVisibilityState;
import com.android.extensions.xr.environment.ShadowEnvironmentVisibilityState;
import com.android.extensions.xr.environment.ShadowPassthroughVisibilityState;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.space.ShadowSpatialCapabilities;
import com.android.extensions.xr.space.ShadowSpatialState;
import com.android.extensions.xr.space.SpatialState;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Technically this doesn't need to be a Robolectric test, since it doesn't directly depend on
// any Android subsystems. However, we're currently using an Android test runner for consistency
// with other Android XR impl tests in this directory.
/**
 * Unit tests for the AndroidXR implementation of JXRCore's SpatialEnvironment module.
 *
 * <p>TODO(b/326748782): Update the FakeExtensions to support better asserts.
 */
@RunWith(RobolectricTestRunner.class)
@SuppressWarnings({"UnnecessarilyFullyQualified"}) // TODO(b/373435470): Remove
public final class SpatialEnvironmentImplTest {
    private static final int SUBSPACE_ID = 5;
    private static final int INVALID_SPLIT_ENGINE_ID = -1;
    private static final long WATER_MATERIAL_ID = 1;
    private final FakeImpressApi mFakeImpressApi = new FakeImpressApi();
    private ActivityController<Activity> mActivityController;
    private Activity mActivity;
    private XrExtensions mXrExtensions = null;
    private Node mSubspaceNode;
    private SubspaceNode mExpectedSubspace;
    private SpatialEnvironmentImpl mEnvironment = null;
    private SplitEngineSubspaceManager mSplitEngineSubspaceManager;

    @Before
    public void setUp() {
        mActivityController = Robolectric.buildActivity(Activity.class);
        mActivity = mActivityController.create().start().get();
        // Reset our state.
        mXrExtensions = XrExtensionsProvider.getXrExtensions();
        Node sceneRootNode = mXrExtensions.createNode();
        mSubspaceNode = mXrExtensions.createNode();
        mExpectedSubspace = new SubspaceNode(SUBSPACE_ID, mSubspaceNode);

        mSplitEngineSubspaceManager = Mockito.mock(SplitEngineSubspaceManager.class);
        when(mSplitEngineSubspaceManager.createSubspace(anyString(), anyInt()))
                .thenReturn(mExpectedSubspace);

        mEnvironment =
                new SpatialEnvironmentImpl(
                        mActivity,
                        mXrExtensions,
                        sceneRootNode,
                        this::getSpatialState,
                        /* useSplitEngine= */ true);
        mEnvironment.onSplitEngineReady(mSplitEngineSubspaceManager, mFakeImpressApi);
    }

    private void setupRuntimeWithoutSplitEngine() {
        Node sceneRootNode = mXrExtensions.createNode();

        mEnvironment =
                new SpatialEnvironmentImpl(
                        mActivity,
                        mXrExtensions,
                        sceneRootNode,
                        this::getSpatialState,
                        /* useSplitEngine= */ false);
    }

    @SuppressWarnings({"FutureReturnValueIgnored", "AndroidJdkLibsChecker"})
    private long fakeLoadEnvironment(String name) {
        try {
            return mFakeImpressApi.loadImageBasedLightingAsset(name).get();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return INVALID_SPLIT_ENGINE_ID;
        }
    }

    @SuppressWarnings({"FutureReturnValueIgnored", "AndroidJdkLibsChecker"})
    private long fakeLoadGltfAsset(String name) {
        try {
            return mFakeImpressApi.loadGltfAsset(name).get();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return INVALID_SPLIT_ENGINE_ID;
        }
    }

    @SuppressWarnings({"FutureReturnValueIgnored", "AndroidJdkLibsChecker"})
    private MaterialResource fakeLoadMaterial(boolean isAlphaMapVersion) {
        try {
            return new MaterialResourceImpl(
                    mFakeImpressApi.createWaterMaterial(isAlphaMapVersion).get().getNativeHandle());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private SpatialState getSpatialState() {
        return mXrExtensions.getSpatialState(mActivity);
    }

    @Test
    public void setPassthroughOpacityPreference() {
        mEnvironment.setPassthroughOpacityPreference(null);
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isNull();

        mEnvironment.setPassthroughOpacityPreference(0.1f);
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isEqualTo(0.1f);
    }

    @Test
    public void setPassthroughOpacityPreferenceNearOrUnderZero_getsZeroOpacity() {
        // Opacity values below 1% should be treated as zero.
        mEnvironment.setPassthroughOpacityPreference(0.009f);
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isEqualTo(0.0f);

        mEnvironment.setPassthroughOpacityPreference(-0.1f);
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isEqualTo(0.0f);
    }

    @Test
    public void setPassthroughOpacityPreferenceNearOrOverOne_getsFullOpacity() {
        // Opacity values above 99% should be treated as full opacity.
        mEnvironment.setPassthroughOpacityPreference(0.991f);
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isEqualTo(1.0f);

        mEnvironment.setPassthroughOpacityPreference(1.1f);
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isEqualTo(1.0f);
    }

    @Test
    public void setPassthroughOpacityPreference_returnsAccordingToSpatialCapabilities() {
        // Change should be applied if the spatial capabilities allow it, otherwise should be
        // pending.
        SpatialState state = mXrExtensions.getSpatialState(mActivity);
        ShadowSpatialState.extract(state)
                .setSpatialCapabilities(ShadowSpatialCapabilities.createAll());
        assertThat(mEnvironment.setPassthroughOpacityPreference(0.5f))
                .isEqualTo(SetPassthroughOpacityPreferenceResult.CHANGE_APPLIED);

        ShadowSpatialState.extract(state)
                .setSpatialCapabilities(ShadowSpatialCapabilities.create());
        assertThat(mEnvironment.setPassthroughOpacityPreference(0.6f))
                .isEqualTo(SetPassthroughOpacityPreferenceResult.CHANGE_PENDING);
    }

    @Test
    public void getCurrentPassthroughOpacity_returnsZeroInitially() {
        assertThat(mEnvironment.getCurrentPassthroughOpacity()).isEqualTo(0.0f);
    }

    @Test
    public void onPassthroughOpacityChangedListener_firesOnPassthroughOpacityChange() {
        @SuppressWarnings(value = "unchecked")
        Consumer<Float> listener1 = (Consumer<Float>) mock(Consumer.class);
        @SuppressWarnings(value = "unchecked")
        Consumer<Float> listener2 = (Consumer<Float>) mock(Consumer.class);

        mEnvironment.addOnPassthroughOpacityChangedListener(listener1);
        mEnvironment.addOnPassthroughOpacityChangedListener(listener2);

        mEnvironment.firePassthroughOpacityChangedEvent(0.5f);
        verify(listener1).accept(0.5f);
        verify(listener2).accept(0.5f);

        mEnvironment.removeOnPassthroughOpacityChangedListener(listener1);
        mEnvironment.firePassthroughOpacityChangedEvent(0.0f);
        verify(listener1)
                .accept(any()); // Verify the removed listener was called exactly once total
        verify(listener2).accept(0.0f); // Verify the active listener was called again with false
    }

    @Test
    public void getSpatialEnvironmentPreference_returnsSetSpatialEnvironmentPreference() {
        SpatialEnvironmentPreference preference = new SpatialEnvironmentPreference(null, null);
        mEnvironment.setSpatialEnvironmentPreference(preference);
        assertThat(mEnvironment.getSpatialEnvironmentPreference()).isEqualTo(preference);
    }

    @Test
    public void
            setSpatialEnvironmentPreference_throwsWhenSplitEngineDisabledIfSkyboxAndGeometryAreNotNull() {
        setupRuntimeWithoutSplitEngine();
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");

        assertThrows(
                UnsupportedOperationException.class,
                () ->
                        mEnvironment.setSpatialEnvironmentPreference(
                                new SpatialEnvironmentPreference(
                                        new ExrImageResourceImpl(exr),
                                        new GltfModelResourceImpl(gltf))));
    }

    @Test
    public void
            setSpatialEnvironmentPreference_doesNotThrowWhenSplitEngineDisabledIfSkyboxAndGeometryAreNull() {
        setupRuntimeWithoutSplitEngine();

        mEnvironment.setSpatialEnvironmentPreference(new SpatialEnvironmentPreference(null, null));

        // System sets the skybox to black without throwing an exception and the environment node is
        // still created.
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNotNull();
    }

    @Test
    public void setSpatialEnvironmentPreference_returnsAppliedWhenCapable() {
        // Change should be applied if the spatial capabilities allow it, otherwise should be
        // pending.
        SpatialState state = mXrExtensions.getSpatialState(mActivity);
        ShadowSpatialState.extract(state)
                .setSpatialCapabilities(ShadowSpatialCapabilities.createAll());
        SpatialEnvironmentPreference preference = new SpatialEnvironmentPreference(null, null);
        assertThat(mEnvironment.setSpatialEnvironmentPreference(preference))
                .isEqualTo(SetSpatialEnvironmentPreferenceResult.CHANGE_APPLIED);

        ShadowSpatialState.extract(state)
                .setSpatialCapabilities(ShadowSpatialCapabilities.create());
        preference = mock(SpatialEnvironment.class).getSpatialEnvironmentPreference();
        assertThat(mEnvironment.setSpatialEnvironmentPreference(preference))
                .isEqualTo(SetSpatialEnvironmentPreferenceResult.CHANGE_PENDING);
    }

    @Test
    public void setSpatialEnvironmentPreferenceNull_removesEnvironment() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr), new GltfModelResourceImpl(gltf)));

        long initialSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> geometryNodes = mFakeImpressApi.getImpressNodesForToken(gltf);
        Map<Long, MaterialData> materials = mFakeImpressApi.getMaterials();
        int animatingNodes = mFakeImpressApi.impressNodeAnimatingSize();
        int loopingAnimatingNodes = mFakeImpressApi.impressNodeLoopAnimatingSize();

        assertThat(initialSkybox).isNotEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(geometryNodes).isNotEmpty();
        assertThat(materials).isEmpty();
        assertThat(animatingNodes).isEqualTo(0);
        assertThat(loopingAnimatingNodes).isEqualTo(0);

        assertThat(mFakeImpressApi.impressNodeHasParent(geometryNodes.get(0))).isTrue();

        // Ensure environment is removed
        mEnvironment.setSpatialEnvironmentPreference(null);

        long finalSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        assertThat(finalSkybox).isEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNull();
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceWithNullSkyboxAndNullGeometry_doesNotDetachEnvironment() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr), new GltfModelResourceImpl(gltf)));

        long initialSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> geometryNodes = mFakeImpressApi.getImpressNodesForToken(gltf);

        assertThat(initialSkybox).isNotEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(geometryNodes).isNotEmpty();

        assertThat(mFakeImpressApi.impressNodeHasParent(geometryNodes.get(0))).isTrue();

        // Ensure environment is not removed if both skybox and geometry are updated to null.
        mEnvironment.setSpatialEnvironmentPreference(new SpatialEnvironmentPreference(null, null));

        long finalSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        assertThat(finalSkybox).isEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNotNull();
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceWithSkyboxAndGeometryWithMeshAndAnimation_doesNotDetachEnvironment() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        // Create dummy regular version of the water material.
        MaterialResource material = fakeLoadMaterial(false);
        String meshName = "fakeMesh";
        String animationName = "fakeAnimation";

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr),
                        new GltfModelResourceImpl(gltf),
                        material,
                        meshName,
                        animationName));

        long initialSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> geometryNodes = mFakeImpressApi.getImpressNodesForToken(gltf);
        Map<Long, MaterialData> materials = mFakeImpressApi.getMaterials();
        int animatingNodes = mFakeImpressApi.impressNodeAnimatingSize();
        int loopingAnimatingNodes = mFakeImpressApi.impressNodeLoopAnimatingSize();

        assertThat(initialSkybox).isNotEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(geometryNodes).isNotEmpty();
        assertThat(mFakeImpressApi.impressNodeHasParent(geometryNodes.get(0))).isTrue();
        assertThat(materials).isNotEmpty();
        assertThat(materials.keySet().toArray()[0]).isEqualTo(WATER_MATERIAL_ID);
        assertThat(materials.get(WATER_MATERIAL_ID).type).isEqualTo(MaterialData.Type.WATER);
        assertThat(animatingNodes).isEqualTo(0);
        assertThat(loopingAnimatingNodes).isEqualTo(1);

        // Ensure environment is not removed if both skybox and geometry are updated to null.
        mEnvironment.setSpatialEnvironmentPreference(new SpatialEnvironmentPreference(null, null));

        long finalSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        assertThat(finalSkybox).isEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNotNull();
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceFromNullPreferenceToNullSkyboxAndGeometry_doesNotDetachEnvironment() {
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(null);

        long initialSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> geometryNodes = mFakeImpressApi.getImpressNodesForToken(gltf);

        assertThat(initialSkybox).isEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(geometryNodes).isEmpty();

        // Ensure environment is not removed if both skybox and geometry are updated to null.
        mEnvironment.setSpatialEnvironmentPreference(new SpatialEnvironmentPreference(null, null));

        long finalSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        assertThat(finalSkybox).isEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNotNull();
    }

    @Test
    public void setNewSpatialEnvironmentPreference_replacesOldSpatialEnvironmentPreference() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long newExr = fakeLoadEnvironment("newFakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        long newGltf = fakeLoadGltfAsset("newFakeGltfAsset");

        // Ensure that an environment is set a first time.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr), new GltfModelResourceImpl(gltf)));

        long initialSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> geometryNodes = mFakeImpressApi.getImpressNodesForToken(gltf);

        // Ensure that an environment is set a second time.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(newExr), new GltfModelResourceImpl(newGltf)));

        long newSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> newGeometryNodes = mFakeImpressApi.getImpressNodesForToken(newGltf);

        // None of the nodes should be null.
        assertThat(initialSkybox).isNotEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(geometryNodes).isNotEmpty();
        assertThat(newSkybox).isNotEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(newGeometryNodes).isNotEmpty();

        // Only the new nodes should have a parent.
        // TODO: b/354711945 - Uncomment when we can test the SetGeometrySplitEngine(null) path.
        // assertThat(fakeImpressApi.impressNodeHasParent(geometryNodes.get(0))).isFalse();
        assertThat(mFakeImpressApi.impressNodeHasParent(newGeometryNodes.get(0))).isTrue();

        // The resources should be different.
        assertThat(initialSkybox).isNotEqualTo(newSkybox);
        assertThat(geometryNodes.get(0)).isNotEqualTo(newGeometryNodes.get(0));

        // The environment node should still be attached.
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNotNull();
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceGeometryWithMaterialAndMeshName_materialIsOverriden() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        // Create dummy regular version of the water material.
        MaterialResource material = fakeLoadMaterial(false);
        String meshName = "fakeMesh";
        String animationName = "fakeAnimation";

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr),
                        new GltfModelResourceImpl(gltf),
                        material,
                        meshName,
                        animationName));

        Map<Long, MaterialData> materials = mFakeImpressApi.getMaterials();
        int loopingAnimatingNodes = mFakeImpressApi.impressNodeLoopAnimatingSize();

        assertThat(
                        mFakeImpressApi.getImpressNodes().keySet().stream()
                                .filter(
                                        node ->
                                                node.materialOverride != null
                                                        && node.materialOverride.type
                                                                == MaterialData.Type.WATER)
                                .toArray())
                .hasLength(1); // 1 glTF node that should be overridden with the water material.

        assertThat(materials).isNotEmpty();
        assertThat(materials.keySet().toArray()[0]).isEqualTo(WATER_MATERIAL_ID);
        assertThat(materials.get(WATER_MATERIAL_ID).type).isEqualTo(MaterialData.Type.WATER);
        assertThat(loopingAnimatingNodes).isEqualTo(1);
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceGeometryWithMaterialAndNoMeshName_materialIsNotOverriden() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        // Create dummy regular version of the water material.
        MaterialResource material = fakeLoadMaterial(false);
        String animationName = "fakeAnimation";

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr),
                        new GltfModelResourceImpl(gltf),
                        material,
                        null,
                        animationName));

        Map<Long, MaterialData> materials = mFakeImpressApi.getMaterials();

        assertThat(
                        mFakeImpressApi.getImpressNodes().keySet().stream()
                                .filter(node -> node.materialOverride == null)
                                .toArray())
                .hasLength(2); // 2 nodes are subspace (parent) and glTF (child) used for the
        // environment. Both
        // have no material override so we expect the length of the filter to be 2.

        assertThat(materials).isNotEmpty();
        assertThat(materials.keySet().toArray()[0]).isEqualTo(WATER_MATERIAL_ID);
        assertThat(materials.get(WATER_MATERIAL_ID).type).isEqualTo(MaterialData.Type.WATER);
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceGeometryWithNoMaterialAndMeshName_materialIsNotOverriden() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        String meshName = "fakeMesh";
        String animationName = "fakeAnimation";

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr),
                        new GltfModelResourceImpl(gltf),
                        null,
                        meshName,
                        animationName));

        Map<Long, MaterialData> materials = mFakeImpressApi.getMaterials();

        assertThat(
                        mFakeImpressApi.getImpressNodes().keySet().stream()
                                .filter(node -> node.materialOverride == null)
                                .toArray())
                .hasLength(2); // 2 nodes are subspace (parent) and glTF (child) used for the
        // environment. Both
        // have no material override so we expect the length of the filter to be 2.

        assertThat(materials).isEmpty();
    }

    @Test
    public void
            setSpatialEnvironmentPreferenceGeometryWithNoAnimationName_geometryIsNotAnimating() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        String animationName = "fakeAnimation";

        // Ensure that an environment is set.
        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr),
                        new GltfModelResourceImpl(gltf),
                        null,
                        null,
                        animationName));

        int loopingAnimatingNodes = mFakeImpressApi.impressNodeLoopAnimatingSize();
        Map<Long, MaterialData> materials = mFakeImpressApi.getMaterials();

        assertThat(loopingAnimatingNodes).isEqualTo(1);

        assertThat(
                        mFakeImpressApi.getImpressNodes().keySet().stream()
                                .filter(node -> node.materialOverride == null)
                                .toArray())
                .hasLength(2); // 2 nodes are subspace (parent) and glTF (child) used for the
        // environment. Both
        // have no material override so we expect the length of the filter to be 2.
        assertThat(materials).isEmpty();
    }

    @Test
    public void isSpatialEnvironmentPreferenceActive_defaultsToFalse() {
        assertThat(mEnvironment.isSpatialEnvironmentPreferenceActive()).isFalse();
    }

    @Test
    public void onSpatialEnvironmentChangedListener_firesOnEnvironmentChange() {
        @SuppressWarnings(value = "unchecked")
        Consumer<Boolean> listener1 = (Consumer<Boolean>) mock(Consumer.class);
        @SuppressWarnings(value = "unchecked")
        Consumer<Boolean> listener2 = (Consumer<Boolean>) mock(Consumer.class);

        mEnvironment.addOnSpatialEnvironmentChangedListener(listener1);
        mEnvironment.addOnSpatialEnvironmentChangedListener(listener2);

        mEnvironment.fireOnSpatialEnvironmentChangedEvent(true);
        verify(listener1).accept(true);
        verify(listener2).accept(true);

        mEnvironment.removeOnSpatialEnvironmentChangedListener(listener1);
        mEnvironment.fireOnSpatialEnvironmentChangedEvent(false);
        verify(listener1)
                .accept(any()); // Verify the removed listener was called exactly once total
        verify(listener2).accept(false); // Verify the active listener was called again with false
    }

    @Test
    public void dispose_clearsSpatialEnvironmentPreferenceListeners() {
        @SuppressWarnings(value = "unchecked")
        Consumer<Boolean> listener = (Consumer<Boolean>) mock(Consumer.class);
        mEnvironment.addOnSpatialEnvironmentChangedListener(listener);

        mEnvironment.fireOnSpatialEnvironmentChangedEvent(true);
        verify(listener).accept(true);

        mEnvironment.dispose();
        mEnvironment.fireOnSpatialEnvironmentChangedEvent(false);
        verify(listener, never()).accept(false);
    }

    @Test
    public void dispose_clearsPassthroughOpacityPreferenceListeners() {
        @SuppressWarnings(value = "unchecked")
        Consumer<Float> listener = (Consumer<Float>) mock(Consumer.class);
        mEnvironment.addOnPassthroughOpacityChangedListener(listener);

        mEnvironment.firePassthroughOpacityChangedEvent(1.0f);
        verify(listener).accept(1.0f);

        // Ensure the listener is called exactly once, even if the event is fired after dispose.
        mEnvironment.dispose();
        mEnvironment.firePassthroughOpacityChangedEvent(0.5f);
        verify(listener).accept(any());
    }

    @Test
    public void dispose_clearsResources() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        SpatialState spatialState = ShadowSpatialState.create();
        ShadowSpatialState.extract(spatialState)
                .setEnvironmentVisibilityState(
                        /* environmentVisibilityState= */ ShadowEnvironmentVisibilityState.create(
                                EnvironmentVisibilityState.APP_VISIBLE));
        ShadowSpatialState.extract(spatialState)
                .setPassthroughVisibilityState(
                        /* passthroughVisibilityState= */ ShadowPassthroughVisibilityState.create(
                                PassthroughVisibilityState.APP, 0.5f));

        mEnvironment.setSpatialState(spatialState);

        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr), new GltfModelResourceImpl(gltf)));
        mEnvironment.setPassthroughOpacityPreference(0.5f);

        long initialSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        List<Integer> geometryNodes = mFakeImpressApi.getImpressNodesForToken(gltf);

        assertThat(initialSkybox).isNotEqualTo(INVALID_SPLIT_ENGINE_ID);
        assertThat(geometryNodes).isNotEmpty();

        assertThat(mFakeImpressApi.impressNodeHasParent(geometryNodes.get(0))).isTrue();

        assertThat(mEnvironment.getSpatialEnvironmentPreference()).isNotNull();
        assertThat(mEnvironment.isSpatialEnvironmentPreferenceActive()).isTrue();

        assertThat(mEnvironment.getPassthroughOpacityPreference()).isNotNull();
        assertThat(mEnvironment.getCurrentPassthroughOpacity()).isEqualTo(0.5f);

        mEnvironment.dispose();

        long finalSkybox = mFakeImpressApi.getCurrentEnvironmentLight();
        assertThat(finalSkybox).isEqualTo(INVALID_SPLIT_ENGINE_ID);
        // TODO: b/354711945 - Uncomment when we can test the SetGeometrySplitEngine(null) path.
        // assertThat(fakeImpressApi.impressNodeHasParent(geometryNodes.get(0))).isFalse();
        assertThat(ShadowXrExtensions.extract(mXrExtensions).getEnvironmentNode(mActivity))
                .isNull();
        assertThat(mEnvironment.getSpatialEnvironmentPreference()).isNull();
        assertThat(mEnvironment.isSpatialEnvironmentPreferenceActive()).isFalse();
        assertThat(mEnvironment.getPassthroughOpacityPreference()).isNull();
        assertThat(mEnvironment.getCurrentPassthroughOpacity()).isEqualTo(0.0f);
    }

    @Test
    public void dispose_disposesImpressApi() {
        long exr = fakeLoadEnvironment("fakeEnvironment");
        long gltf = fakeLoadGltfAsset("fakeGltfAsset");
        // Create dummy regular version of the water material.
        MaterialResource material = fakeLoadMaterial(false);
        String meshName = "fakeMesh";
        String animationName = "fakeAnimation";

        mEnvironment.setSpatialEnvironmentPreference(
                new SpatialEnvironmentPreference(
                        new ExrImageResourceImpl(exr),
                        new GltfModelResourceImpl(gltf),
                        material,
                        meshName,
                        animationName));

        assertThat(mFakeImpressApi.getImageBasedLightingAssets()).isNotEmpty();
        assertThat(mFakeImpressApi.getImpressNodes()).isNotEmpty();
        assertThat(mFakeImpressApi.getGltfModels()).isNotEmpty();
        assertThat(mFakeImpressApi.getMaterials()).isNotEmpty();

        mEnvironment.dispose();

        assertThat(mFakeImpressApi.getImageBasedLightingAssets()).isEmpty();
        assertThat(mFakeImpressApi.getImpressNodes()).isEmpty();
        assertThat(mFakeImpressApi.getGltfModels()).isEmpty();
        assertThat(mFakeImpressApi.getMaterials()).isEmpty();
    }
}
