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

import androidx.xr.runtime.internal.ExrImageResource;
import androidx.xr.runtime.internal.GltfModelResource;
import androidx.xr.runtime.internal.MaterialResource;
import androidx.xr.runtime.internal.SceneRuntime;
import androidx.xr.runtime.internal.SceneRuntimeFactory;
import androidx.xr.runtime.testing.FakeSceneRuntimeFactory;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.impress.FakeImpressApiImpl;
import androidx.xr.scenecore.testing.FakeScheduledExecutorService;

import com.android.extensions.xr.ShadowXrExtensions;
import com.android.extensions.xr.XrExtensions;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
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
public class SpatialRenderingRuntimeTest {
    private static final int OPEN_XR_REFERENCE_SPACE_TYPE = 1;
    private SceneRuntime mSceneRuntime;
    private SpatialRenderingRuntime mRuntime;

    private final FakeScheduledExecutorService mFakeExecutor = new FakeScheduledExecutorService();
    Activity mActivity;
    private final FakeImpressApiImpl mFakeImpressApi = new FakeImpressApiImpl();
    SplitEngineSubspaceManager mSplitEngineSubspaceManager =
            Mockito.mock(SplitEngineSubspaceManager.class);
    ImpSplitEngineRenderer mSplitEngineRenderer = Mockito.mock(ImpSplitEngineRenderer.class);
    private final @NonNull XrExtensions mXrExtensions =
            Objects.requireNonNull(XrExtensionsProvider.getXrExtensions());

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).create().start().get();
        ShadowXrExtensions.extract(mXrExtensions)
                .setOpenXrWorldSpaceType(OPEN_XR_REFERENCE_SPACE_TYPE);
        SceneRuntimeFactory sceneFactory = new FakeSceneRuntimeFactory();
        mSceneRuntime = sceneFactory.create(mActivity);
        mRuntime =
                SpatialRenderingRuntime.create(
                        mSceneRuntime,
                        mActivity,
                        mFakeImpressApi,
                        mSplitEngineSubspaceManager,
                        mSplitEngineRenderer);
    }

    @After
    public void tearDown() {
        // Dispose the runtime between test cases to clean up lingering references.
        try {
            mRuntime.dispose();
            mSceneRuntime.dispose();
        } catch (NullPointerException e) {
            // Tests which already call dispose will cause a NPE here due to Activity being null
            // when detaching from the scene.
        }
        mRuntime = null;
        mSceneRuntime = null;
    }

    MaterialResource createWaterMaterial() throws Exception {
        ListenableFuture<MaterialResource> materialFuture =
                mRuntime.createWaterMaterial(/* isAlphaMapVersion= */ false);
        assertThat(materialFuture).isNotNull();
        // This resolves the transformation of the Future from a SplitEngine token to the JXR
        // Texture.  This is a hidden detail from the API surface's perspective.
        mFakeExecutor.runAll();
        return materialFuture.get();
    }

    @Test
    public void loadGltfByAssetName_returnsModel() throws Exception {
        ListenableFuture<GltfModelResource> modelFuture =
                mRuntime.loadGltfByAssetName("FakeAsset.glb");

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
                mRuntime.loadGltfByByteArray(new byte[] {1, 2, 3}, "FakeAsset.glb");

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
                mRuntime.loadExrImageByAssetName("FakeAsset.zip");

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
                mRuntime.loadExrImageByByteArray(new byte[] {1, 2, 3}, "FakeAsset.zip");

        assertThat(imageFuture).isNotNull();

        ExrImageResource image = imageFuture.get();
        assertThat(image).isNotNull();
        ExrImageResourceImpl imageImpl = (ExrImageResourceImpl) image;
        assertThat(imageImpl).isNotNull();
        long token = imageImpl.getExtensionImageToken();
        assertThat(token).isEqualTo(1);
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
    public void startAndStopRenderer_statusUpdated() {
        mRuntime.startRenderer();
        assertThat(mRuntime.isFrameLoopStarted()).isTrue();
        mRuntime.stopRenderer();
        assertThat(mRuntime.isFrameLoopStarted()).isFalse();
        mRuntime.startRenderer();
        assertThat(mRuntime.isFrameLoopStarted()).isTrue();
    }

}
