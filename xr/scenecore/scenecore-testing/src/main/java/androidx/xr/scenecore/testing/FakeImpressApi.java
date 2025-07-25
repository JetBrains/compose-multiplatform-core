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

package androidx.xr.scenecore.testing;

import android.content.res.Resources.NotFoundException;
import android.graphics.SurfaceTexture;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.concurrent.futures.ResolvableFuture;

import com.google.ar.imp.apibindings.ImpressApi;
import com.google.ar.imp.apibindings.Texture;
import com.google.ar.imp.apibindings.TextureSampler;
import com.google.ar.imp.apibindings.WaterMaterial;
import com.google.ar.imp.view.View;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fake implementation of the JNI API for communicating with the Impress Split Engine instance for
 * testing purposes.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FakeImpressApi implements ImpressApi {

    // ResolvableFuture is marked as RestrictTo(LIBRARY_GROUP_PREFIX), which is intended for classes
    // within AndroidX. We're in the process of migrating to AndroidX. Without suppressing this
    // warning, however, we get a build error - go/bugpattern/RestrictTo.
    @SuppressWarnings("RestrictTo")
    static class AnimationInProgress {
        public String name;
        public ResolvableFuture<Void> fireOnDone;
    }

    /** Test bookkeeping data for a Android Surface */
    @SuppressWarnings({"ParcelCreator", "ParcelNotFinal"})
    public static class TestSurface extends Surface {
        public int id;

        public TestSurface(int id) {
            super(new SurfaceTexture(id));
        }
    }

    /** Test bookkeeping data for a StereoSurfaceEntity */
    public static class StereoSurfaceEntityData {
        /** Enum representing the different canvas shapes that can be created. */
        public enum CanvasShape {
            QUAD,
            VR_360_SPHERE,
            VR_180_HEMISPHERE
        }

        int mImpressNode;
        Surface mSurface;
        @StereoMode int mStereoMode;

        // This is a union of the CanvasShape parameters
        float mWidth;
        float mHeight;
        float mRadius;
        CanvasShape mCanvasShape;
        float mFeatherRadiusX;
        float mFeatherRadiusY;

        @Nullable
        public Surface getSurface() {
            return mSurface;
        }

        @StereoMode
        public int getStereoMode() {
            return mStereoMode;
        }

        public float getWidth() {
            return mWidth;
        }

        public float getHeight() {
            return mHeight;
        }

        public float getRadius() {
            return mRadius;
        }

        public float getFeatherRadiusX() {
            return mFeatherRadiusX;
        }

        public float getFeatherRadiusY() {
            return mFeatherRadiusY;
        }

        @Nullable
        public CanvasShape getCanvasShape() {
            return mCanvasShape;
        }
    }

    /** Test bookkeeping data for a Material */
    public static class MaterialData {
        /** Enum representing the different built-in material types that can be created. */
        public enum Type {
            GENERIC,
            WATER,
            WATER_ALPHA
        }

        @NonNull public Type type;
        public long materialHandle;

        public MaterialData(@NonNull Type type, long materialHandle) {
            this.type = type;
            this.materialHandle = materialHandle;
        }
    }

    /** Test bookkeeping data for a Gltf gltfToken */
    public static class GltfNodeData {
        public int entityId;
        @Nullable public MaterialData materialOverride;

        public void setEntityId(int entityId) {
            this.entityId = entityId;
        }

        public void setMaterialOverride(@Nullable MaterialData materialOverride) {
            this.materialOverride = materialOverride;
        }
    }

    // Vector of image based lighting asset tokens.
    private final List<Long> mImageBasedLightingAssets = new ArrayList<>();

    // Map of model tokens to the list of impress nodes that are instances of that model.
    private final Map<Long, List<Integer>> mGltfModels = new HashMap<>();

    // Map of impress nodes to their parent impress nodes.
    private final Map<GltfNodeData, GltfNodeData> mImpressNodes = new HashMap<>();

    // Map of impress nodes and animations that are currently playing (non looping)
    final Map<Integer, AnimationInProgress> mImpressAnimatedNodes = new HashMap<>();

    // Map of impress nodes and animations that are currently playing (looping)
    final Map<Integer, AnimationInProgress> mImpressLoopAnimatedNodes = new HashMap<>();

    // Map of impress entity nodes to their associated StereoSurfaceEntityData
    final Map<Integer, StereoSurfaceEntityData> mStereoSurfaceEntities = new HashMap<>();

    // Map of texture image tokens to their associated Texture object
    public final Map<Long, Texture> mTextureImages = new HashMap<>();

    // Map of material tokens to their associated MaterialData object
    public final Map<Long, MaterialData> mMaterials = new HashMap<>();

    private int mNextImageBasedLightingAssetId = 1;
    private int mNextModelId = 1;
    private int mNextNodeId = 1;
    private long mNextTextureId = 1;
    private long mNextMaterialId = 1;
    private long mCurrentEnvironmentLightId = -1;

    @NonNull
    public Map<Integer, StereoSurfaceEntityData> getStereoSurfaceEntities() {
        return mStereoSurfaceEntities;
    }

    @Override
    public void setup(@NonNull View view) {}

    @Override
    public void onResume() {}

    @Override
    public void onPause() {}

    @Override
    public void releaseImageBasedLightingAsset(long iblToken) {
        if (!mImageBasedLightingAssets.contains(iblToken)) {
            throw new NotFoundException("Image based lighting asset token not found");
        }
        mImageBasedLightingAssets.remove(iblToken);
    }

    @Override
    @NonNull
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    public ListenableFuture<Long> loadImageBasedLightingAsset(@NonNull String path) {
        long imageBasedLightingAssetToken = mNextImageBasedLightingAssetId++;
        mImageBasedLightingAssets.add(imageBasedLightingAssetToken);
        // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
        ResolvableFuture<Long> ret = ResolvableFuture.create();
        ret.set(imageBasedLightingAssetToken);

        return ret;
    }

    @Override
    @NonNull
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    public ListenableFuture<Long> loadImageBasedLightingAsset(
            @NonNull byte[] data, @NonNull String key) {
        long imageBasedLightingAssetToken = mNextImageBasedLightingAssetId++;
        mImageBasedLightingAssets.add(imageBasedLightingAssetToken);
        // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
        ResolvableFuture<Long> ret = ResolvableFuture.create();
        ret.set(imageBasedLightingAssetToken);

        return ret;
    }

    @Override
    @NonNull
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    public ListenableFuture<Long> loadGltfAsset(@NonNull String path) {
        long gltfToken = mNextModelId++;
        mGltfModels.put(gltfToken, new ArrayList<>());
        // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
        ResolvableFuture<Long> ret = ResolvableFuture.create();
        ret.set(gltfToken);

        return ret;
    }

    @Override
    @NonNull
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    public ListenableFuture<Long> loadGltfAsset(@NonNull byte[] data, @NonNull String key) {
        long gltfToken = mNextModelId++;
        mGltfModels.put(gltfToken, new ArrayList<>());
        // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
        ResolvableFuture<Long> ret = ResolvableFuture.create();
        ret.set(gltfToken);

        return ret;
    }

    @Override
    public void releaseGltfAsset(long gltfToken) {
        if (!mGltfModels.containsKey(gltfToken)) {
            throw new NotFoundException("Model token not found");
        }
        mGltfModels.remove(gltfToken);
    }

    @Override
    public int instanceGltfModel(long gltfToken) {
        return instanceGltfModel(gltfToken, true);
    }

    @Override
    public int instanceGltfModel(long gltfToken, boolean enableCollider) {
        if (!mGltfModels.containsKey(gltfToken)) {
            throw new IllegalArgumentException("Model token not found");
        }
        int entityId = mNextNodeId++;
        mGltfModels.get(gltfToken).add(entityId);
        GltfNodeData gltfNodeData = new GltfNodeData();
        gltfNodeData.setEntityId(entityId);
        mImpressNodes.put(gltfNodeData, null);
        return entityId;
    }

    @Override
    public void setGltfModelColliderEnabled(int impressNode, boolean enableCollider) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    @NonNull
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    public ListenableFuture<Void> animateGltfModel(
            int impressNode, @Nullable String animationName, boolean loop) {
        ResolvableFuture<Void> future = ResolvableFuture.create();
        if (getGltfNodeData(impressNode) == null) {
            future.setException(new IllegalArgumentException("Impress node not found"));
            return future;
        }

        AnimationInProgress animationInProgress = new AnimationInProgress();
        animationInProgress.name = animationName;
        animationInProgress.fireOnDone = future;
        if (loop) {
            mImpressLoopAnimatedNodes.put(impressNode, animationInProgress);
        } else {
            mImpressAnimatedNodes.put(impressNode, animationInProgress);
        }
        return future;
    }

    @Override
    public void stopGltfModelAnimation(int impressNode) {
        if (getGltfNodeData(impressNode) == null) {
            throw new IllegalArgumentException("Impress node not found");
        } else if (!mImpressAnimatedNodes.containsKey(impressNode)
                && !mImpressLoopAnimatedNodes.containsKey(impressNode)) {
            throw new IllegalArgumentException("Impress node is not animating");
        } else if (mImpressAnimatedNodes.containsKey(impressNode)) {
            mImpressAnimatedNodes.remove(impressNode);
        } else if (mImpressLoopAnimatedNodes.containsKey(impressNode)) {
            mImpressLoopAnimatedNodes.remove(impressNode);
        }
    }

    @Override
    public int createImpressNode() {
        int entityId = mNextNodeId++;
        GltfNodeData gltfNodeData = new GltfNodeData();
        gltfNodeData.setEntityId(entityId);
        mImpressNodes.put(gltfNodeData, null);
        return entityId;
    }

    @Override
    public void destroyImpressNode(int impressNode) {
        GltfNodeData gltfNodeData = getGltfNodeData(impressNode);
        if (gltfNodeData == null) {
            throw new IllegalArgumentException("Impress node not found");
        }
        for (Map.Entry<Long, List<Integer>> pair : mGltfModels.entrySet()) {
            if (pair.getValue().contains(impressNode)) {
                pair.getValue().remove(Integer.valueOf(impressNode));
            }
        }
        for (Map.Entry<GltfNodeData, GltfNodeData> pair : mImpressNodes.entrySet()) {
            if (pair.getValue() != null && pair.getValue().equals(gltfNodeData)) {
                pair.setValue(null);
            }
        }
        mImpressNodes.remove(gltfNodeData);

        if (mStereoSurfaceEntities.containsKey(impressNode)) {
            mStereoSurfaceEntities.remove(impressNode);
        }
    }

    @Override
    public void setImpressNodeParent(int impressNodeChild, int impressNodeParent) {
        GltfNodeData childGltfNodeData = getGltfNodeData(impressNodeChild);
        GltfNodeData parentGltfNodeData = getGltfNodeData(impressNodeParent);
        if (childGltfNodeData == null || parentGltfNodeData == null) {
            throw new IllegalArgumentException("Impress node(s) not found");
        }
        mImpressNodes.put(childGltfNodeData, parentGltfNodeData);
    }

    /** Gets the impress nodes for glTF models that match the given token. */
    @NonNull
    public List<Integer> getImpressNodesForToken(long gltfToken) {
        return mGltfModels.get(gltfToken);
    }

    /** Returns true if the given impress node has a parent. */
    public boolean impressNodeHasParent(int impressNode) {
        GltfNodeData gltfNodeData = getGltfNodeData(impressNode);
        if (gltfNodeData == null) {
            return false;
        }
        return mImpressNodes.get(gltfNodeData) != null;
    }

    /** Returns the parent impress node for the given impress node. */
    public int getImpressNodeParent(int impressNode) {
        GltfNodeData gltfNodeData = getGltfNodeData(impressNode);
        if (gltfNodeData == null) {
            return -1;
        }
        return mImpressNodes.get(gltfNodeData).entityId;
    }

    /** Returns the number of impress nodes that are currently animating. */
    public int impressNodeAnimatingSize() {
        return mImpressAnimatedNodes.size();
    }

    /** Returns the number of impress nodes that looping animations. */
    public int impressNodeLoopAnimatingSize() {
        return mImpressLoopAnimatedNodes.size();
    }

    @Override
    public int createStereoSurface(@StereoMode int stereoMode) {
        StereoSurfaceEntityData data = new StereoSurfaceEntityData();
        data.mImpressNode = createImpressNode();
        data.mSurface = new TestSurface(data.mImpressNode);
        data.mStereoMode = stereoMode;
        data.mCanvasShape = null;
        mStereoSurfaceEntities.put(data.mImpressNode, data);
        return data.mImpressNode;
    }

    /**
     * This method sets the canvas shape of a StereoSurfaceEntity using its Impress ID.
     *
     * @param impressNode The Impress node which hosts the StereoSurfaceEntity to be updated.
     * @param width The width in local spatial units to set the quad to.
     * @param height The height in local spatial units to set the quad to.
     */
    @Override
    public void setStereoSurfaceEntityCanvasShapeQuad(int impressNode, float width, float height) {
        if (!mStereoSurfaceEntities.containsKey(impressNode)) {
            throw new IllegalArgumentException("Couldn't find stereo surface entity!");
        }
        StereoSurfaceEntityData data = mStereoSurfaceEntities.get(impressNode);
        data.mCanvasShape = StereoSurfaceEntityData.CanvasShape.QUAD;
        data.mWidth = width;
        data.mHeight = height;
    }

    /**
     * This method sets the canvas shape of a StereoSurfaceEntity using its Impress ID.
     *
     * @param impressNode The Impress node which hosts the StereoSurfaceEntity to be updated.
     * @param radius The radius in local spatial units to set the sphere to.
     */
    @Override
    public void setStereoSurfaceEntityCanvasShapeSphere(int impressNode, float radius) {
        if (!mStereoSurfaceEntities.containsKey(impressNode)) {
            throw new IllegalArgumentException("Couldn't find stereo surface entity!");
        }
        StereoSurfaceEntityData data = mStereoSurfaceEntities.get(impressNode);
        data.mCanvasShape = StereoSurfaceEntityData.CanvasShape.VR_360_SPHERE;
        data.mRadius = radius;
    }

    /**
     * This method sets the canvas shape of a StereoSurfaceEntity using its Impress ID.
     *
     * @param impressNode The Impress node which hosts the StereoSurfaceEntity to be updated.
     * @param radius The radius in local spatial units of the hemisphere.
     */
    @Override
    public void setStereoSurfaceEntityCanvasShapeHemisphere(int impressNode, float radius) {
        StereoSurfaceEntityData data = mStereoSurfaceEntities.get(impressNode);
        data.mCanvasShape = StereoSurfaceEntityData.CanvasShape.VR_180_HEMISPHERE;
        data.mRadius = radius;
    }

    @Override
    @NonNull
    public Surface getSurfaceFromStereoSurface(int panelImpressNode) {
        if (!mStereoSurfaceEntities.containsKey(panelImpressNode)) {
            // TODO: b/387323937 - the Native code currently CHECK fails in this case
            throw new IllegalArgumentException("Couldn't find stereo surface entity!");
        }
        return mStereoSurfaceEntities.get(panelImpressNode).mSurface;
    }

    @Override
    public void setFeatherRadiusForStereoSurface(
            int panelImpressNode, float radiusX, float radiusY) {
        if (!mStereoSurfaceEntities.containsKey(panelImpressNode)) {
            // TODO: b/387323937 - the Native code currently CHECK fails in this case
            throw new IllegalArgumentException("Couldn't find stereo surface entity!");
        }
        mStereoSurfaceEntities.get(panelImpressNode).mFeatherRadiusX = radiusX;
        mStereoSurfaceEntities.get(panelImpressNode).mFeatherRadiusY = radiusY;
    }

    @Override
    public void setStereoModeForStereoSurface(int panelImpressNode, @StereoMode int mode) {
        if (!mStereoSurfaceEntities.containsKey(panelImpressNode)) {
            // TODO: b/387323937 - the Native code currently CHECK fails in this case
            throw new IllegalArgumentException("Couldn't find stereo surface entity!");
        }
        mStereoSurfaceEntities.get(panelImpressNode).mStereoMode = mode;
    }

    @Override
    @NonNull
    @SuppressWarnings({"RestrictTo", "AsyncSuffixFuture"})
    public ListenableFuture<Texture> loadTexture(
            @NonNull String path, @NonNull TextureSampler sampler) {
        long textureImageToken = mNextTextureId++;
        Texture texture =
                new Texture.Builder()
                        .setImpressApi(this)
                        .setNativeTexture(textureImageToken)
                        .setTextureSampler(new TextureSampler.Builder().build())
                        .build();
        mTextureImages.put(textureImageToken, texture);
        // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
        ResolvableFuture<Texture> ret = ResolvableFuture.create();
        ret.set(texture);

        return ret;
    }

    @Override
    @NonNull
    public Texture borrowReflectionTexture() {
        long textureImageToken = mNextTextureId++;
        return new Texture.Builder()
                .setNativeTexture(textureImageToken)
                .setTextureSampler(new TextureSampler.Builder().build())
                .build();
    }

    @Override
    @NonNull
    public Texture getReflectionTextureFromIbl(long iblToken) {
        long textureImageToken = mNextTextureId++;
        return new Texture.Builder()
                .setNativeTexture(textureImageToken)
                .setTextureSampler(new TextureSampler.Builder().build())
                .build();
    }

    @Override
    @SuppressWarnings("RestrictTo")
    @NonNull
    public ListenableFuture<WaterMaterial> createWaterMaterial(boolean isAlphaMapVersion) {
        long materialToken = mNextMaterialId++;
        WaterMaterial material =
                new WaterMaterial.Builder()
                        .setImpressApi(this)
                        .setNativeMaterial(materialToken)
                        .build();
        mMaterials.put(materialToken, new MaterialData(MaterialData.Type.WATER, materialToken));
        ResolvableFuture<WaterMaterial> ret = ResolvableFuture.create();
        ret.set(material);
        return ret;
    }

    @Override
    public void setReflectionCubeOnWaterMaterial(long nativeMaterial, long reflectionCube) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setNormalMapOnWaterMaterial(long nativeMaterial, long normalMap) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setNormalTilingOnWaterMaterial(long nativeMaterial, float normalTiling) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setNormalSpeedOnWaterMaterial(long nativeMaterial, float normalSpeed) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setAlphaStepMultiplierOnWaterMaterial(
            long nativeMaterial, float alphaStepMultiplier) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setAlphaMapOnWaterMaterial(long nativeWaterMaterial, long alphaMap) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setNormalZOnWaterMaterial(long nativeWaterMaterial, float normalZ) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setNormalBoundaryOnWaterMaterial(long nativeWaterMaterial, float normalBoundary) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setAlphaStepUOnWaterMaterial(
            long nativeWaterMaterial, float x, float y, float z, float w) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setAlphaStepVOnWaterMaterial(
            long nativeWaterMaterial, float x, float y, float z, float w) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void destroyNativeObject(long nativeHandle) {
        if (mMaterials.containsKey(nativeHandle)) {
            mMaterials.remove(nativeHandle);
        }
        if (mTextureImages.containsKey(nativeHandle)) {
            mTextureImages.remove(nativeHandle);
        }
    }

    @Override
    public void setMaterialOverride(
            int impressNode, long nativeMaterial, @NonNull String meshName) {
        GltfNodeData gltfNodeData = getGltfNodeData(impressNode);
        if (gltfNodeData == null) {
            throw new IllegalArgumentException("Impress node not found");
        }
        gltfNodeData.setMaterialOverride(mMaterials.get(nativeMaterial));
    }

    @Override
    public void setPreferredEnvironmentLight(long iblToken) {
        mCurrentEnvironmentLightId = iblToken;
    }

    @Override
    public void clearPreferredEnvironmentIblAsset() {
        mCurrentEnvironmentLightId = -1;
    }

    @Override
    public void setPrimaryAlphaMaskForStereoSurface(int impressNode, long alphaMask) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void setAuxiliaryAlphaMaskForStereoSurface(int impressNode, long alphaMask) {
        throw new IllegalArgumentException("not implemented");
    }

    @Override
    public void disposeAllResources() {
        mImageBasedLightingAssets.clear();
        mImpressNodes.clear();
        mGltfModels.clear();
        mTextureImages.clear();
        mMaterials.clear();
    }

    /** Returns the map of texture image tokens to their associated Texture object. */
    @NonNull
    public Map<Long, Texture> getTextureImages() {
        return mTextureImages;
    }

    /** Returns the map of material tokens to their associated MaterialData object. */
    @NonNull
    public Map<Long, MaterialData> getMaterials() {
        return mMaterials;
    }

    /** Returns the map of impress nodes to their parent impress nodes. */
    @NonNull
    public Map<GltfNodeData, GltfNodeData> getImpressNodes() {
        return mImpressNodes;
    }

    // Returns the list of image based lighting assets that have been loaded.
    @NonNull
    public List<Long> getImageBasedLightingAssets() {
        return mImageBasedLightingAssets;
    }

    // Returns the map of glTF model tokens to their associated impress nodes.
    @NonNull
    public Map<Long, List<Integer>> getGltfModels() {
        return mGltfModels;
    }

    /** Returns the current environment light token. */
    public long getCurrentEnvironmentLight() {
        return mCurrentEnvironmentLightId;
    }

    @Nullable
    private GltfNodeData getGltfNodeData(int impressNode) {
        for (Map.Entry<GltfNodeData, GltfNodeData> pair : mImpressNodes.entrySet()) {
            if (pair.getKey().entityId == impressNode) {
                return pair.getKey();
            }
        }
        return null;
    }
}
