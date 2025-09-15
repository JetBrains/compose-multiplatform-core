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

import android.app.Activity;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;
import androidx.concurrent.futures.ResolvableFuture;
import androidx.xr.runtime.math.Matrix3;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.runtime.math.Vector4;
import androidx.xr.scenecore.impl.extensions.XrExtensionsProvider;
import androidx.xr.scenecore.impl.impress.ImpressApi;
import androidx.xr.scenecore.impl.impress.ImpressApiImpl;
import androidx.xr.scenecore.impl.impress.KhronosPbrMaterial;
import androidx.xr.scenecore.impl.impress.Texture;
import androidx.xr.scenecore.impl.impress.WaterMaterial;
import androidx.xr.scenecore.internal.ExrImageResource;
import androidx.xr.scenecore.internal.GltfModelResource;
import androidx.xr.scenecore.internal.KhronosPbrMaterialSpec;
import androidx.xr.scenecore.internal.MaterialResource;
import androidx.xr.scenecore.internal.RenderingEntityFactory;
import androidx.xr.scenecore.internal.RenderingRuntime;
import androidx.xr.scenecore.internal.SceneRuntime;
import androidx.xr.scenecore.internal.TextureResource;
import androidx.xr.scenecore.internal.TextureSampler;

import com.android.extensions.xr.XrExtensions;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.ar.imp.view.splitengine.ImpSplitEngine;
import com.google.ar.imp.view.splitengine.ImpSplitEngineRenderer;
import com.google.common.util.concurrent.ListenableFuture;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Implementation of [RenderingRuntime] for devices that support the [Feature.SPATIAL] system
 * feature.
 */
class SpatialRenderingRuntime implements RenderingRuntime {
    private static final String SPLIT_ENGINE_LIBRARY_NAME = "impress_api_jni";

    @SuppressWarnings("UnusedVariable")
    private final @NonNull RenderingEntityFactory mRenderingEntityFactory;

    private @Nullable Activity mActivity;

    @SuppressWarnings("UnusedVariable")
    private final XrExtensions mExtensions;

    private final ImpressApi mImpressApi;
    private SplitEngineSubspaceManager mSplitEngineSubspaceManager;
    private ImpSplitEngineRenderer mSplitEngineRenderer;
    private boolean mIsDisposed = false;
    private boolean mFrameLoopStarted;

    private SpatialRenderingRuntime(
            @NonNull SceneRuntime sceneRuntime,
            @NonNull Activity activity,
            @NonNull XrExtensions extensions,
            @NonNull ImpressApi impressApi,
            @NonNull SplitEngineSubspaceManager subspaceManager,
            @NonNull ImpSplitEngineRenderer renderer) {
        if (!(sceneRuntime instanceof RenderingEntityFactory)) {
            throw new IllegalArgumentException(
                    "Expected sceneRuntime to be a RenderingEntityFactory");
        }
        mRenderingEntityFactory = (RenderingEntityFactory) sceneRuntime;
        mActivity = activity;
        mExtensions = extensions;
        mImpressApi = impressApi;
        mSplitEngineRenderer = renderer;
        mSplitEngineSubspaceManager = subspaceManager;

        startRenderer();
    }

    /** Create a new @c RenderingRuntime. */
    @VisibleForTesting
    static @NonNull SpatialRenderingRuntime create(
            @NonNull SceneRuntime sceneRuntime,
            @NonNull Activity activity,
            @Nullable ImpressApi impressApi,
            @Nullable SplitEngineSubspaceManager splitEngineSubspaceManager,
            @Nullable ImpSplitEngineRenderer splitEngineRenderer) {
        XrExtensions extensions = XrExtensionsProvider.getXrExtensions();
        if (extensions == null) throw new IllegalStateException("XrExtensions is null");
        if (impressApi == null) impressApi = new ImpressApiImpl();
        if (splitEngineRenderer == null) {
            ImpSplitEngine.SplitEngineSetupParams impApiSetupParams =
                    new ImpSplitEngine.SplitEngineSetupParams();
            impApiSetupParams.jniLibraryName = SPLIT_ENGINE_LIBRARY_NAME;
            splitEngineRenderer =
                    ImpSplitEngineRenderer.create(activity, impApiSetupParams, extensions);
        }
        if (splitEngineSubspaceManager == null) {
            splitEngineSubspaceManager =
                    new SplitEngineSubspaceManager(
                            splitEngineRenderer, extensions, null, null, SPLIT_ENGINE_LIBRARY_NAME);
        }
        impressApi.setup(splitEngineRenderer.getView());
        return new SpatialRenderingRuntime(
                sceneRuntime,
                activity,
                extensions,
                impressApi,
                splitEngineSubspaceManager,
                splitEngineRenderer);
    }

    /**
     * Create a new @c SpatialRenderingRuntime.
     *
     * @param sceneRuntime The SceneRuntime provide basic function for creating entities.
     * @param activity The Activity to use.
     * @return A new SpatialRenderingRuntime.
     */
    static @NonNull SpatialRenderingRuntime create(
            @NonNull SceneRuntime sceneRuntime, @NonNull Activity activity) {
        return SpatialRenderingRuntime.create(sceneRuntime, activity, null, null, null);
    }

    private static TextureResourceImpl getTextureResourceFromToken(long token) {
        return new TextureResourceImpl(token);
    }

    private static MaterialResourceImpl getMaterialResourceFromToken(long token) {
        return new MaterialResourceImpl(token);
    }

    private static GltfModelResourceImpl getModelResourceFromToken(long token) {
        return new GltfModelResourceImpl(token);
    }

    private static ExrImageResourceImpl getExrImageResourceFromToken(long token) {
        return new ExrImageResourceImpl(token);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private @Nullable ListenableFuture<GltfModelResource> loadGltfAsset(
            Supplier<ListenableFuture<Long>> modelLoader) {
        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("This method must be called on the main thread.");
        }

        ResolvableFuture<GltfModelResource> gltfModelResourceFuture = ResolvableFuture.create();

        ListenableFuture<Long> gltfTokenFuture;
        try {
            gltfTokenFuture = modelLoader.get();
        } catch (RuntimeException e) {
            return null;
        }

        gltfTokenFuture.addListener(
                () -> {
                    try {
                        long gltfToken = gltfTokenFuture.get();
                        gltfModelResourceFuture.set(getModelResourceFromToken(gltfToken));
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        gltfModelResourceFuture.setException(e);
                    }
                },
                mActivity::runOnUiThread);

        return gltfModelResourceFuture;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private @Nullable ListenableFuture<ExrImageResource> loadExrImage(
            Supplier<ListenableFuture<Long>> assetLoader) {
        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("This method must be called on the main thread.");
        }

        ResolvableFuture<ExrImageResource> exrImageResourceFuture = ResolvableFuture.create();

        ListenableFuture<Long> exrImageTokenFuture;
        try {
            exrImageTokenFuture = assetLoader.get();
        } catch (RuntimeException e) {
            return null;
        }

        exrImageTokenFuture.addListener(
                () -> {
                    try {
                        long exrImageToken = exrImageTokenFuture.get();
                        exrImageResourceFuture.set(getExrImageResourceFromToken(exrImageToken));
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        exrImageResourceFuture.setException(e);
                    }
                },
                mActivity::runOnUiThread);

        return exrImageResourceFuture;
    }

    // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for classes
    // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
    // warning, however, we get a build error - go/bugpattern/RestrictTo.
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    @Override
    public @NonNull ListenableFuture<GltfModelResource> loadGltfByAssetName(@NonNull String name) {
        return loadGltfAsset(() -> mImpressApi.loadGltfAsset(name));
    }

    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    @Override
    public @NonNull ListenableFuture<GltfModelResource> loadGltfByByteArray(
            byte @NonNull [] assetData, @NonNull String assetKey) {
        return loadGltfAsset(() -> mImpressApi.loadGltfAsset(assetData, assetKey));
    }

    // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for classes
    // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
    // warning, however, we get a build error - go/bugpattern/RestrictTo.
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    @Override
    public @NonNull ListenableFuture<ExrImageResource> loadExrImageByAssetName(
            @NonNull String assetName) {
        return loadExrImage(() -> mImpressApi.loadImageBasedLightingAsset(assetName));
    }

    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    @Override
    public @NonNull ListenableFuture<ExrImageResource> loadExrImageByByteArray(
            byte @NonNull [] assetData, @NonNull String assetKey) {
        return loadExrImage(() -> mImpressApi.loadImageBasedLightingAsset(assetData, assetKey));
    }

    // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for classes
    // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
    // warning, however, we get a build error - go/bugpattern/RestrictTo.
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    @Override
    public @NonNull ListenableFuture<TextureResource> loadTexture(@NonNull String path) {
        ResolvableFuture<TextureResource> textureResourceFuture = ResolvableFuture.create();
        // TODO:b/374216912 - Consider calling setFuture() here to catch if the application calls
        // cancel() on the return value from this function, so we can propagate the cancelation
        // message to the Impress API.

        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("This method must be called on the main thread.");
        }

        ListenableFuture<Texture> textureFuture;
        textureFuture = mImpressApi.loadTexture(path);

        textureFuture.addListener(
                () -> {
                    try {
                        Texture texture = textureFuture.get();
                        textureResourceFuture.set(
                                getTextureResourceFromToken(texture.getNativeHandle()));
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        textureResourceFuture.setException(e);
                    }
                },
                // It's convenient for the main application for us to dispatch their listeners on
                // the main thread, because they are required to call back to Impress from there,
                // and it's likely that they will want to call back into the SDK to create entities
                // from within a listener. We defensively post to the main thread here, but in
                // practice this should not cause a thread hop because the Impress API already
                // dispatches its callbacks to the main thread.
                mActivity::runOnUiThread);
        return textureResourceFuture;
    }

    @Override
    public @Nullable TextureResource borrowReflectionTexture() {
        Texture texture = mImpressApi.borrowReflectionTexture();
        if (texture == null) {
            return null;
        }
        return getTextureResourceFromToken(texture.getNativeHandle());
    }

    @Override
    public void destroyTexture(@NonNull TextureResource texture) {
        TextureResourceImpl textureResource = (TextureResourceImpl) texture;
        mImpressApi.destroyNativeObject(textureResource.getTextureToken());
    }

    @Override
    public @Nullable TextureResource getReflectionTextureFromIbl(
            @NonNull ExrImageResource iblToken) {
        ExrImageResourceImpl exrImageResource = (ExrImageResourceImpl) iblToken;
        Texture texture =
                mImpressApi.getReflectionTextureFromIbl(exrImageResource.getExtensionImageToken());
        if (texture == null) {
            return null;
        }
        return texture;
    }

    // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for classes
    // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
    // warning, however, we get a build error - go/bugpattern/RestrictTo.
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    @Override
    public @NonNull ListenableFuture<MaterialResource> createWaterMaterial(
            boolean isAlphaMapVersion) {
        ResolvableFuture<MaterialResource> materialResourceFuture = ResolvableFuture.create();
        // TODO:b/374216912 - Consider calling setFuture() here to catch if the application calls
        // cancel() on the return value from this function, so we can propagate the cancelation
        // message to the Impress API.

        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("This method must be called on the main thread.");
        }

        ListenableFuture<WaterMaterial> materialFuture;
        materialFuture = mImpressApi.createWaterMaterial(isAlphaMapVersion);

        materialFuture.addListener(
                () -> {
                    try {
                        WaterMaterial material = materialFuture.get();
                        materialResourceFuture.set(
                                getMaterialResourceFromToken(material.getNativeHandle()));
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        materialResourceFuture.setException(e);
                    }
                },
                // It's convenient for the main application for us to dispatch their listeners on
                // the main thread, because they are required to call back to Impress from there,
                // and it's likely that they will want to call back into the SDK to create entities
                // from within a listener. We defensively post to the main thread here, but in
                // practice this should not cause a thread hop because the Impress API already
                // dispatches its callbacks to the main thread.
                mActivity::runOnUiThread);
        return materialResourceFuture;
    }

    @Override
    public void destroyWaterMaterial(@NonNull MaterialResource material) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.destroyNativeObject(((MaterialResourceImpl) material).getMaterialToken());
    }

    @Override
    public void setReflectionMapOnWaterMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource reflectionMap,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(reflectionMap instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setReflectionMapOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) reflectionMap).getTextureToken(),
                sampler);
    }

    @Override
    public void setNormalMapOnWaterMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource normalMap,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(normalMap instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setNormalMapOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) normalMap).getTextureToken(),
                sampler);
    }

    @Override
    public void setNormalTilingOnWaterMaterial(
            @NonNull MaterialResource material, float normalTiling) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setNormalTilingOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), normalTiling);
    }

    @Override
    public void setNormalSpeedOnWaterMaterial(
            @NonNull MaterialResource material, float normalSpeed) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setNormalSpeedOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), normalSpeed);
    }

    @Override
    public void setAlphaStepMultiplierOnWaterMaterial(
            @NonNull MaterialResource material, float alphaStepMultiplier) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setAlphaStepMultiplierOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), alphaStepMultiplier);
    }

    @Override
    public void setAlphaMapOnWaterMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource alphaMap,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(alphaMap instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setAlphaMapOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) alphaMap).getTextureToken(),
                sampler);
    }

    @Override
    public void setNormalZOnWaterMaterial(@NonNull MaterialResource material, float normalZ) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setNormalZOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), normalZ);
    }

    @Override
    public void setNormalBoundaryOnWaterMaterial(
            @NonNull MaterialResource material, float normalBoundary) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setNormalBoundaryOnWaterMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), normalBoundary);
    }

    @SuppressWarnings("AsyncSuffixFuture")
    @Override
    public @NonNull ListenableFuture<MaterialResource> createKhronosPbrMaterial(
            @NonNull KhronosPbrMaterialSpec spec) {
        ResolvableFuture<MaterialResource> materialResourceFuture = ResolvableFuture.create();
        // TODO:b/374216912 - Consider calling setFuture() here to catch if the application calls
        // cancel() on the return value from this function, so we can propagate the cancelation
        // message to the Impress API.

        if (!Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("This method must be called on the main thread.");
        }

        ListenableFuture<KhronosPbrMaterial> materialFuture;
        try {
            materialFuture = mImpressApi.createKhronosPbrMaterial(spec);
        } catch (RuntimeException e) {
            throw new RuntimeException("KhronosPbr Material couldn't be created");
        }

        materialFuture.addListener(
                () -> {
                    try {
                        KhronosPbrMaterial material = materialFuture.get();
                        materialResourceFuture.set(
                                getMaterialResourceFromToken(material.getNativeHandle()));
                    } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        materialResourceFuture.setException(e);
                    }
                },
                // It's convenient for the main application for us to dispatch their listeners on
                // the main thread, because they are required to call back to Impress from there,
                // and it's likely that they will want to call back into the SDK to create entities
                // from within a listener. We defensively post to the main thread here, but in
                // practice this should not cause a thread hop because the Impress API already
                // dispatches its callbacks to the main thread.
                mActivity::runOnUiThread);
        return materialResourceFuture;
    }

    @Override
    public void destroyKhronosPbrMaterial(@NonNull MaterialResource material) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.destroyNativeObject(((MaterialResourceImpl) material).getMaterialToken());
    }

    @Override
    public void setBaseColorTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource baseColor,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(baseColor instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setBaseColorTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) baseColor).getTextureToken(),
                sampler);
    }

    @Override
    public void setBaseColorUvTransformOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Matrix3 uvTransform) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        float[] data = uvTransform.getData();
        mImpressApi.setBaseColorUvTransformOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                data[0],
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7],
                data[8]);
    }

    @Override
    public void setBaseColorFactorsOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Vector4 factors) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setBaseColorFactorsOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                factors.getX(),
                factors.getY(),
                factors.getZ(),
                factors.getW());
    }

    @Override
    public void setMetallicRoughnessTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource metallicRoughness,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(metallicRoughness instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setMetallicRoughnessTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) metallicRoughness).getTextureToken(),
                sampler);
    }

    @Override
    public void setMetallicRoughnessUvTransformOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Matrix3 uvTransform) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        float[] data = uvTransform.getData();
        mImpressApi.setMetallicRoughnessUvTransformOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                data[0],
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7],
                data[8]);
    }

    @Override
    public void setMetallicFactorOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float factor) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setMetallicFactorOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), factor);
    }

    @Override
    public void setRoughnessFactorOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float factor) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setRoughnessFactorOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), factor);
    }

    @Override
    public void setNormalTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource normal,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(normal instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setNormalTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) normal).getTextureToken(),
                sampler);
    }

    @Override
    public void setNormalUvTransformOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Matrix3 uvTransform) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        float[] data = uvTransform.getData();
        mImpressApi.setNormalUvTransformOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                data[0],
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7],
                data[8]);
    }

    @Override
    public void setNormalFactorOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float factor) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setNormalFactorOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), factor);
    }

    @Override
    public void setAmbientOcclusionTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource ambientOcclusion,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(ambientOcclusion instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setAmbientOcclusionTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) ambientOcclusion).getTextureToken(),
                sampler);
    }

    @Override
    public void setAmbientOcclusionUvTransformOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Matrix3 uvTransform) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        float[] data = uvTransform.getData();
        mImpressApi.setAmbientOcclusionUvTransformOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                data[0],
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7],
                data[8]);
    }

    @Override
    public void setAmbientOcclusionFactorOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float factor) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setAmbientOcclusionFactorOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), factor);
    }

    @Override
    public void setEmissiveTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource emissive,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(emissive instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setEmissiveTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) emissive).getTextureToken(),
                sampler);
    }

    @Override
    public void setEmissiveUvTransformOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Matrix3 uvTransform) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        float[] data = uvTransform.getData();
        mImpressApi.setEmissiveUvTransformOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                data[0],
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7],
                data[8]);
    }

    @Override
    public void setEmissiveFactorsOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Vector3 factors) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setEmissiveFactorsOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                factors.getX(),
                factors.getY(),
                factors.getZ());
    }

    @Override
    public void setClearcoatTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource clearcoat,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(clearcoat instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setClearcoatTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) clearcoat).getTextureToken(),
                sampler);
    }

    @Override
    public void setClearcoatNormalTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource clearcoatNormal,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(clearcoatNormal instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setClearcoatNormalTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) clearcoatNormal).getTextureToken(),
                sampler);
    }

    @Override
    public void setClearcoatRoughnessTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource clearcoatRoughness,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(clearcoatRoughness instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setClearcoatRoughnessTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) clearcoatRoughness).getTextureToken(),
                sampler);
    }

    @Override
    public void setClearcoatFactorsOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float intensity, float roughness, float normal) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setClearcoatFactorsOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), intensity, roughness, normal);
    }

    @Override
    public void setSheenColorTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource sheenColor,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(sheenColor instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setSheenColorTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) sheenColor).getTextureToken(),
                sampler);
    }

    @Override
    public void setSheenColorFactorsOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Vector3 factors) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setSheenColorFactorsOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                factors.getX(),
                factors.getY(),
                factors.getZ());
    }

    @Override
    public void setSheenRoughnessTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource sheenRoughness,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(sheenRoughness instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setSheenRoughnessTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) sheenRoughness).getTextureToken(),
                sampler);
    }

    @Override
    public void setSheenRoughnessFactorOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float factor) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setSheenRoughnessFactorOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), factor);
    }

    @Override
    public void setTransmissionTextureOnKhronosPbrMaterial(
            @NonNull MaterialResource material,
            @NonNull TextureResource transmission,
            @NonNull TextureSampler sampler) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        if (!(transmission instanceof TextureResourceImpl)) {
            throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
        }
        mImpressApi.setTransmissionTextureOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                ((TextureResourceImpl) transmission).getTextureToken(),
                sampler);
    }

    @Override
    public void setTransmissionUvTransformOnKhronosPbrMaterial(
            @NonNull MaterialResource material, @NonNull Matrix3 uvTransform) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        float[] data = uvTransform.getData();
        mImpressApi.setTransmissionUvTransformOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(),
                data[0],
                data[1],
                data[2],
                data[3],
                data[4],
                data[5],
                data[6],
                data[7],
                data[8]);
    }

    @Override
    public void setTransmissionFactorOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float factor) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setTransmissionFactorOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), factor);
    }

    @Override
    public void setIndexOfRefractionOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float indexOfRefraction) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setIndexOfRefractionOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), indexOfRefraction);
    }

    @Override
    public void setAlphaCutoffOnKhronosPbrMaterial(
            @NonNull MaterialResource material, float alphaCutoff) {
        if (!(material instanceof MaterialResourceImpl)) {
            throw new IllegalArgumentException("MaterialResource is not a MaterialResourceImpl");
        }
        mImpressApi.setAlphaCutoffOnKhronosPbrMaterial(
                ((MaterialResourceImpl) material).getMaterialToken(), alphaCutoff);
    }

    @Override
    public void startRenderer() {
        if (mSplitEngineRenderer == null || mFrameLoopStarted) {
            return;
        }
        mFrameLoopStarted = true;
        mSplitEngineRenderer.startFrameLoop();
    }

    @Override
    public void stopRenderer() {
        if (mSplitEngineRenderer == null || !mFrameLoopStarted) {
            return;
        }
        mFrameLoopStarted = false;
        mSplitEngineRenderer.stopFrameLoop();
    }

    @Override
    public void dispose() {
        if (mIsDisposed) {
            return;
        }
        stopRenderer();
        mImpressApi.clearPreferredEnvironmentIblAsset();
        mImpressApi.disposeAllResources();

        mActivity = null;
        if (mSplitEngineRenderer != null && mSplitEngineSubspaceManager != null) {
            mSplitEngineSubspaceManager.destroy();
            mSplitEngineRenderer.destroy();
            mSplitEngineSubspaceManager = null;
            mSplitEngineRenderer = null;
        }
        mIsDisposed = true;
    }

    @VisibleForTesting
    boolean isFrameLoopStarted() {
        return mFrameLoopStarted;
    }
}
