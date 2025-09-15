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

package androidx.xr.scenecore.spatial.rendering;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl;
import androidx.xr.scenecore.impl.impress.ImpressApi;
import androidx.xr.scenecore.impl.impress.ImpressNode;
import androidx.xr.scenecore.impl.impress.Material;
import androidx.xr.scenecore.impl.impress.WaterMaterial;
import androidx.xr.scenecore.internal.GltfEntity;
import androidx.xr.scenecore.internal.GltfFeature;
import androidx.xr.scenecore.internal.MaterialResource;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;
import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

@RunWith(RobolectricTestRunner.class)
public class GltfFeatureImplTest {
    private static final int OPEN_XR_REFERENCE_SPACE_TYPE = 1;

    private final XrExtensions mXrExtensions = XrExtensionsProvider.getXrExtensions();
    private final FakeScheduledExecutorService mExecutor = new FakeScheduledExecutorService();
    private final ImpressApi mMockImpressApi = mock(ImpressApi.class);
    private final FakeImpressApiImpl mFakeImpressApi = new FakeImpressApiImpl();
    private final SplitEngineSubspaceManager mSplitEngineSubspaceManager =
            Mockito.mock(SplitEngineSubspaceManager.class);
    private ImpressNode mModelImpressNode;

    private static final int SUBSPACE_ID = 5;
    private final Node mSubspaceNode = Objects.requireNonNull(mXrExtensions).createNode();
    private final SubspaceNode mExpectedSubspace = new SubspaceNode(SUBSPACE_ID, mSubspaceNode);

    private GltfFeature mGltfFeature;

    @Before
    public void setUp() throws ExecutionException, InterruptedException {
        when(mSplitEngineSubspaceManager.createSubspace(anyString(), anyInt()))
                .thenReturn(mExpectedSubspace);

        assertThat(mXrExtensions).isNotNull();
        ShadowXrExtensions.extract(mXrExtensions)
                .setOpenXrWorldSpaceType(OPEN_XR_REFERENCE_SPACE_TYPE);
        mGltfFeature = createGltfFeature();
    }

    @After
    public void tearDown() {
        mGltfFeature.dispose();
    }

    private GltfFeature createGltfFeature()
            throws ExecutionException, InterruptedException {
        long modelToken = -1;
        ListenableFuture<Long> modelTokenFuture =
                mFakeImpressApi.loadGltfAsset("FakeGltfAsset.glb");
        modelToken = modelTokenFuture.get();
        GltfModelResourceImpl modelResource = new GltfModelResourceImpl(modelToken);
        mModelImpressNode =
                mFakeImpressApi.instanceGltfModel(modelResource.getExtensionModelToken());
        when(mMockImpressApi.createImpressNode()).thenReturn(mFakeImpressApi.createImpressNode());
        when(mMockImpressApi.instanceGltfModel(modelResource.getExtensionModelToken())).thenReturn(
                mModelImpressNode);

        return new GltfFeatureImpl(
                modelResource,
                mMockImpressApi,
                mSplitEngineSubspaceManager,
                mXrExtensions);
    }

    @Nullable
    private MaterialResource createWaterMaterial(boolean isAlphaMapVersion)
            throws ExecutionException, InterruptedException {
        ResolvableFuture<MaterialResource> materialResourceFuture = ResolvableFuture.create();
        ListenableFuture<WaterMaterial> materialFuture =
                mFakeImpressApi.createWaterMaterial(isAlphaMapVersion);

        WaterMaterial material = materialFuture.get();
        materialResourceFuture.set(material);

        return materialResourceFuture.get();
    }

    @Test
    public void startAnimation_startsAnimation() {
        String animationName = "test_animation";
        when(mMockImpressApi.animateGltfModel(
                mModelImpressNode, animationName, true)).thenReturn(
                mFakeImpressApi.animateGltfModel(mModelImpressNode, animationName, true));
        mGltfFeature.startAnimation(/* looping= */ true, animationName, mExecutor);

        assertThat(mGltfFeature.getAnimationState()).isEqualTo(GltfEntity.AnimationState.PLAYING);
        verify(mMockImpressApi).animateGltfModel(mModelImpressNode, animationName, true);
    }

    @Test
    public void stopAnimation_stopsAnimation() {
        String animationName = "test_animation";
        when(mMockImpressApi.animateGltfModel(
                mModelImpressNode, animationName, true)).thenReturn(
                mFakeImpressApi.animateGltfModel(mModelImpressNode, animationName, true));
        mGltfFeature.startAnimation(/* looping= */ true, animationName, mExecutor);

        assertThat(mGltfFeature.getAnimationState()).isEqualTo(GltfEntity.AnimationState.PLAYING);

        mGltfFeature.stopAnimation();
        mFakeImpressApi.stopGltfModelAnimation(mModelImpressNode);

        assertThat(mGltfFeature.getAnimationState()).isEqualTo(GltfEntity.AnimationState.STOPPED);
        verify(mMockImpressApi).stopGltfModelAnimation(mModelImpressNode);
    }

    @Test
    public void setMaterialOverrideGltfEntity_materialOverridesMesh() throws Exception {
        MaterialResource material = createWaterMaterial(/* isAlphaMapVersion= */ false);
        long nativeHandle = ((Material) material).getNativeHandle();

        assertThat(material).isNotNull();

        String meshName = "fake_mesh_name";
        mGltfFeature.setMaterialOverride(material, meshName);
        mFakeImpressApi.setMaterialOverride(mModelImpressNode, nativeHandle, meshName);

        verify(mMockImpressApi).setMaterialOverride(mModelImpressNode, nativeHandle, meshName);
        assertThat(
                        mFakeImpressApi.getImpressNodes().keySet().stream()
                                .filter(
                                        node ->
                                                node.getMaterialOverride() != null
                                                        && node.getMaterialOverride().getType()
                                                                == FakeImpressApiImpl.MaterialData
                                                                        .Type.WATER)
                                .toArray())
                .hasLength(1);
    }

    // TODO: b/426594104 provide a fake SplitEngineSubspaceManager and cover the dispose() method
}
