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

import android.app.Activity;
import android.view.Surface;

import androidx.xr.runtime.SubspaceNodeHolder;
import androidx.xr.runtime.internal.SceneRuntimeFactory;
import androidx.xr.runtime.math.FloatSize2d;
import androidx.xr.runtime.math.Pose;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.internal.ExrImageResource;
import androidx.xr.scenecore.internal.GltfEntity;
import androidx.xr.scenecore.internal.GltfFeature;
import androidx.xr.scenecore.internal.GltfModelResource;
import androidx.xr.scenecore.internal.MaterialResource;
import androidx.xr.scenecore.internal.RenderingEntityFactory;
import androidx.xr.scenecore.internal.SceneRuntime;
import androidx.xr.scenecore.internal.SubspaceNodeEntity;
import androidx.xr.scenecore.internal.SurfaceEntity;
import androidx.xr.scenecore.testing.FakeSceneRuntimeFactory;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;


import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;
import com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer;
import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.util.Objects;

/** Tests for {@link SpatialRenderingRuntime}. */
@RunWith(RobolectricTestRunner.class)
// TODO: b/441552980 - add unit tests for gltf animations
public class SpatialRenderingRuntimeTest {
    private static final int OPEN_XR_REFERENCE_SPACE_TYPE = 1;
    private SceneRuntime mSceneRuntime;
    private SpatialRenderingRuntime mRenderingRuntime;

    private RenderingEntityFactory mRenderingEntityFactory;

    private final FakeScheduledExecutorService mFakeExecutor = new FakeScheduledExecutorService();
    Activity mActivity;
    private final FakeImpressApiImpl mFakeImpressApi = new FakeImpressApiImpl();
    SplitEngineSubspaceManager mSplitEngineSubspaceManager =
            Mockito.mock(SplitEngineSubspaceManager.class);
    ImpSplitEngineRenderer mSplitEngineRenderer = Mockito.mock(ImpSplitEngineRenderer.class);
    private final @NonNull XrExtensions mXrExtensions =
            Objects.requireNonNull(XrExtensionsProvider.getXrExtensions());

    private static final int SUBSPACE_ID = 5;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).create().start().get();
        ShadowXrExtensions.extract(mXrExtensions)
                .setOpenXrWorldSpaceType(OPEN_XR_REFERENCE_SPACE_TYPE);
        SceneRuntimeFactory sceneFactory = new FakeSceneRuntimeFactory();
        mSceneRuntime = (SceneRuntime) sceneFactory.create(mActivity);
        mRenderingRuntime =
                SpatialRenderingRuntime.create(
                        mSceneRuntime,
                        mActivity,
                        mFakeImpressApi,
                        mSplitEngineSubspaceManager,
                        mSplitEngineRenderer);
        mRenderingEntityFactory = (RenderingEntityFactory) mSceneRuntime;
    }

    @After
    public void tearDown() {
        // Dispose the runtime between test cases to clean up lingering references.
        try {
            mRenderingRuntime.dispose();
            mSceneRuntime.dispose();
        } catch (NullPointerException e) {
            // Tests which already call dispose will cause a NPE here due to Activity being null
            // when detaching from the scene.
        }
        mRenderingRuntime = null;
        mSceneRuntime = null;
    }

    private GltfEntity createGltfEntity() throws Exception {
        return createGltfEntity(new Pose());
    }

    private GltfEntity createGltfEntity(Pose pose) throws Exception {
        ListenableFuture<GltfModelResource> modelFuture =
                mRenderingRuntime.loadGltfByAssetName("FakeAsset.glb");
        assertThat(modelFuture).isNotNull();
        // This resolves the transformation of the Future from a SplitEngine token to the JXR
        // GltfModelResource.  This is a hidden detail from the API surface's perspective.
        mFakeExecutor.runAll();
        GltfModelResource model = modelFuture.get();

        GltfFeature feature = new GltfFeatureImpl((GltfModelResourceImpl) model, mFakeImpressApi,
                mSplitEngineSubspaceManager, mXrExtensions);
        return mRenderingEntityFactory.createGltfEntity(feature, pose,
                mSceneRuntime.getActivitySpace());
    }

    MaterialResource createWaterMaterial() throws Exception {
        ListenableFuture<MaterialResource> materialFuture =
                mRenderingRuntime.createWaterMaterial(/* isAlphaMapVersion= */ false);
        assertThat(materialFuture).isNotNull();
        // This resolves the transformation of the Future from a SplitEngine token to the JXR
        // Texture.  This is a hidden detail from the API surface's perspective.
        mFakeExecutor.runAll();
        return materialFuture.get();
    }

    @Test
    public void loadGltfByAssetName_returnsModel() throws Exception {
        ListenableFuture<GltfModelResource> modelFuture =
                mRenderingRuntime.loadGltfByAssetName("FakeAsset.glb");

        assertThat(modelFuture).isNotNull();

        GltfModelResource model = modelFuture.get();
        assertThat(model).isNotNull();
        GltfModelResourceImpl modelImpl = (GltfModelResourceImpl) model;
        assertThat(modelImpl).isNotNull();
        long token = modelImpl.getExtensionModelToken();
        assertThat(token).isEqualTo(1);
    }

    @Test
    public void loadGltfByByteArray_returnsModel() throws Exception {
        ListenableFuture<GltfModelResource> modelFuture =
                mRenderingRuntime.loadGltfByByteArray(new byte[] {1, 2, 3}, "FakeAsset.glb");

        assertThat(modelFuture).isNotNull();

        GltfModelResource model = modelFuture.get();
        assertThat(model).isNotNull();
        GltfModelResourceImpl modelImpl = (GltfModelResourceImpl) model;
        assertThat(modelImpl).isNotNull();
        long token = modelImpl.getExtensionModelToken();
        assertThat(token).isEqualTo(1);
    }

    @Test
    public void loadExrImageByAssetName_returnsModel() throws Exception {
        ListenableFuture<ExrImageResource> imageFuture =
                mRenderingRuntime.loadExrImageByAssetName("FakeAsset.zip");

        assertThat(imageFuture).isNotNull();

        ExrImageResource image = imageFuture.get();
        assertThat(image).isNotNull();
        ExrImageResourceImpl imageImpl = (ExrImageResourceImpl) image;
        assertThat(imageImpl).isNotNull();
        long token = imageImpl.getExtensionImageToken();
        assertThat(token).isEqualTo(1);
    }

    @Test
    public void loadExrImageByByteArray_returnsModel() throws Exception {
        ListenableFuture<ExrImageResource> imageFuture =
                mRenderingRuntime.loadExrImageByByteArray(new byte[] {1, 2, 3}, "FakeAsset.zip");

        assertThat(imageFuture).isNotNull();

        ExrImageResource image = imageFuture.get();
        assertThat(image).isNotNull();
        ExrImageResourceImpl imageImpl = (ExrImageResourceImpl) image;
        assertThat(imageImpl).isNotNull();
        long token = imageImpl.getExtensionImageToken();
        assertThat(token).isEqualTo(1);
    }

    @Test
    public void createGltfEntity_returnsEntity() throws Exception {
        assertThat(createGltfEntity()).isNotNull();
    }

    @Test
    public void createSurfaceEntity_returnsStereoSurface() {
        final float kTestWidth = 14.0f;
        final float kTestHeight = 28.0f;
        final float kTestSphereRadius = 7.0f;
        final float kTestHemisphereRadius = 11.0f;

        SurfaceEntity surfaceEntityQuad =
                mRenderingRuntime.createSurfaceEntity(
                        SurfaceEntity.StereoMode.SIDE_BY_SIDE,
                        new Pose(),
                        new SurfaceEntity.Shape.Quad(new FloatSize2d(kTestWidth, kTestHeight)),
                        SurfaceEntity.SurfaceProtection.NONE,
                        SurfaceEntity.SuperSampling.DEFAULT,
                        mSceneRuntime.getActivitySpace());

        assertThat(surfaceEntityQuad).isNotNull();

        SurfaceEntity surfaceEntitySphere =
                mRenderingRuntime.createSurfaceEntity(
                        SurfaceEntity.StereoMode.TOP_BOTTOM,
                        new Pose(),
                        new SurfaceEntity.Shape.Sphere(kTestSphereRadius),
                        SurfaceEntity.SurfaceProtection.NONE,
                        SurfaceEntity.SuperSampling.DEFAULT,
                        mSceneRuntime.getActivitySpace());

        assertThat(surfaceEntitySphere).isNotNull();

        SurfaceEntity surfaceEntityHemisphere =
                mRenderingRuntime.createSurfaceEntity(
                        SurfaceEntity.StereoMode.MONO,
                        new Pose(),
                        new SurfaceEntity.Shape.Hemisphere(kTestHemisphereRadius),
                        SurfaceEntity.SurfaceProtection.NONE,
                        SurfaceEntity.SuperSampling.DEFAULT,
                        mSceneRuntime.getActivitySpace());

        assertThat(surfaceEntityHemisphere).isNotNull();

        assertThat(mFakeImpressApi.getStereoSurfaceEntities()).hasSize(3);

        Surface surface = surfaceEntityQuad.getSurface();

        assertThat(surface).isNotNull();
    }

    @Test
    public void createWaterMaterial_returnsWaterMaterial() throws Exception {
        assertThat(createWaterMaterial()).isNotNull();
    }

    @Test
    public void destroyWaterMaterial_removesWaterMaterial() throws Exception {
        MaterialResourceImpl material = (MaterialResourceImpl) createWaterMaterial();
        int initialMaterialCount = mFakeImpressApi.getMaterials().size();

        mFakeImpressApi.destroyNativeObject(material.getMaterialToken());

        int finalMaterialCount = mFakeImpressApi.getMaterials().size();
        assertThat(finalMaterialCount).isEqualTo(initialMaterialCount - 1);
    }

    @Test
    public void createSubspaceNodeEntity_returnSubspaceNodeEntity() {
        Dimensions size = new Dimensions(1.0f, 2.0f, 3.0f);
        Node node = mXrExtensions.createNode();
        SubspaceNode subspaceNode = new SubspaceNode(SUBSPACE_ID + 1, node);
        SubspaceNodeHolder<?> holder = new SubspaceNodeHolder<>(subspaceNode, SubspaceNode.class);
        SubspaceNodeEntity entity = mRenderingRuntime.createSubspaceNodeEntity(holder, size);

        assertThat(entity).isNotNull();
    }

    @Test
    public void startAndStopRenderer_statusUpdated() {
        mRenderingRuntime.startRenderer();
        assertThat(mRenderingRuntime.isFrameLoopStarted()).isTrue();
        mRenderingRuntime.stopRenderer();
        assertThat(mRenderingRuntime.isFrameLoopStarted()).isFalse();
        mRenderingRuntime.startRenderer();
        assertThat(mRenderingRuntime.isFrameLoopStarted()).isTrue();
    }
}
