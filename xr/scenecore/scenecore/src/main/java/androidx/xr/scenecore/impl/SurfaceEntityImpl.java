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

import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.SurfaceEntity;
import androidx.xr.runtime.internal.SurfaceEntity.CanvasShape;
import androidx.xr.runtime.internal.TextureResource;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.NodeTransaction;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;
import com.google.androidxr.splitengine.SubspaceNode;
import com.google.ar.imp.apibindings.ImpressApi;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Implementation of a RealityCore StereoSurfaceEntitySplitEngine.
 *
 * <p>This is used to create an entity that contains a StereoSurfacePanel using the Split Engine
 * route.
 */
final class SurfaceEntityImpl extends AndroidXrEntity implements SurfaceEntity {
    private final ImpressApi mImpressApi;
    private final SplitEngineSubspaceManager mSplitEngineSubspaceManager;
    private final SubspaceNode mSubspace;
    // TODO: b/362520810 - Wrap impress nodes w/ Java class.
    private final int mEntityImpressNode;
    private final int mSubspaceImpressNode;
    @StereoMode private int mStereoMode = SurfaceEntity.StereoMode.SIDE_BY_SIDE;
    private CanvasShape mCanvasShape;
    private float mFeatherRadiusX = 0.0f;
    private float mFeatherRadiusY = 0.0f;

    SurfaceEntityImpl(
            Entity parentEntity,
            ImpressApi impressApi,
            SplitEngineSubspaceManager splitEngineSubspaceManager,
            XrExtensions extensions,
            EntityManager entityManager,
            ScheduledExecutorService executor,
            @StereoMode int stereoMode,
            CanvasShape canvasShape) {
        super(extensions.createNode(), extensions, entityManager, executor);
        mImpressApi = impressApi;
        mSplitEngineSubspaceManager = splitEngineSubspaceManager;
        mStereoMode = stereoMode;
        mCanvasShape = canvasShape;
        setParent(parentEntity);

        // TODO(b/377906324): - Punt this logic to the UI thread, so that applications can create
        // StereoSurface entities from any thread.

        // System will only render Impress nodes that are parented by this subspace node.
        mSubspaceImpressNode = impressApi.createImpressNode();
        String subspaceName = "stereo_surface_panel_entity_subspace_" + mSubspaceImpressNode;

        mSubspace = splitEngineSubspaceManager.createSubspace(subspaceName, mSubspaceImpressNode);

        try (NodeTransaction transaction = extensions.createNodeTransaction()) {
            // Make the Entity node a parent of the subspace node.
            transaction.setParent(mSubspace.getSubspaceNodeActual(), mNode).apply();
        }

        // This is broken up into two steps to limit the size of the Impress Surface
        mEntityImpressNode = mImpressApi.createStereoSurface(stereoMode);
        setCanvasShape(mCanvasShape);

        // The CPM node hierarchy is: Entity CPM node --- parent of ---> Subspace CPM node.
        // The Impress node hierarchy is: Subspace Impress node --- parent of ---> Entity Impress
        // node.
        impressApi.setImpressNodeParent(mEntityImpressNode, mSubspaceImpressNode);
    }

    @Override
    public CanvasShape getCanvasShape() {
        return mCanvasShape;
    }

    @Override
    public void setCanvasShape(CanvasShape canvasShape) {
        // TODO(b/377906324): - Punt this logic to the UI thread, so that applications can call this
        // method from any thread.
        mCanvasShape = canvasShape;

        if (mCanvasShape instanceof CanvasShape.Quad) {
            CanvasShape.Quad q = (CanvasShape.Quad) mCanvasShape;
            mImpressApi.setStereoSurfaceEntityCanvasShapeQuad(
                    mEntityImpressNode, q.getWidth(), q.getHeight());
        } else if (mCanvasShape instanceof CanvasShape.Vr360Sphere) {
            CanvasShape.Vr360Sphere s = (CanvasShape.Vr360Sphere) mCanvasShape;
            mImpressApi.setStereoSurfaceEntityCanvasShapeSphere(mEntityImpressNode, s.getRadius());
        } else if (mCanvasShape instanceof CanvasShape.Vr180Hemisphere) {
            CanvasShape.Vr180Hemisphere h = (CanvasShape.Vr180Hemisphere) mCanvasShape;
            mImpressApi.setStereoSurfaceEntityCanvasShapeHemisphere(
                    mEntityImpressNode, h.getRadius());
        } else {
            throw new IllegalArgumentException("Unsupported canvas shape: " + mCanvasShape);
        }
    }

    @SuppressWarnings("ObjectToString")
    @Override
    public void dispose() {
        // TODO(b/377906324): - Punt this logic to the UI thread, so that applications can destroy
        // StereoSurface entities from any thread.
        // The subspace impress node will be destroyed when the subspace is deleted.
        mSplitEngineSubspaceManager.deleteSubspace(mSubspace.subspaceId);
        super.dispose();
    }

    @Override
    public void setStereoMode(@StereoMode int mode) {
        mStereoMode = mode;
        mImpressApi.setStereoModeForStereoSurface(mEntityImpressNode, mode);
    }

    @Override
    public Dimensions getDimensions() {
        return mCanvasShape.getDimensions();
    }

    @Override
    @StereoMode
    public int getStereoMode() {
        return mStereoMode;
    }

    @Override
    public void setPrimaryAlphaMaskTexture(@Nullable TextureResource alphaMask) {
        long alphaMaskToken = -1;
        if (alphaMask != null) {
            if (!(alphaMask instanceof TextureResourceImpl)) {
                throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
            }
            alphaMaskToken = ((TextureResourceImpl) alphaMask).getTextureToken();
        }
        mImpressApi.setPrimaryAlphaMaskForStereoSurface(mEntityImpressNode, alphaMaskToken);
    }

    @Override
    public void setAuxiliaryAlphaMaskTexture(@Nullable TextureResource alphaMask) {
        long alphaMaskToken = -1;
        if (alphaMask != null) {
            if (!(alphaMask instanceof TextureResourceImpl)) {
                throw new IllegalArgumentException("TextureResource is not a TextureResourceImpl");
            }
            alphaMaskToken = ((TextureResourceImpl) alphaMask).getTextureToken();
        }
        mImpressApi.setAuxiliaryAlphaMaskForStereoSurface(mEntityImpressNode, alphaMaskToken);
    }

    @Override
    public Surface getSurface() {
        // TODO(b/377906324) - Either cache the surface in the constructor, or change this interface
        // to
        // return a Future.
        return mImpressApi.getSurfaceFromStereoSurface(mEntityImpressNode);
    }

    @Override
    public void setFeatherRadiusX(float radius) {
        mFeatherRadiusX = radius;
        // For now, we set both the left/right and top/bottom feather radius at the same time.
        mImpressApi.setFeatherRadiusForStereoSurface(
                mEntityImpressNode, mFeatherRadiusX, mFeatherRadiusY);
    }

    @Override
    public float getFeatherRadiusX() {
        return mFeatherRadiusX;
    }

    @Override
    public void setFeatherRadiusY(float radius) {
        mFeatherRadiusY = radius;
        // For now, we set both the left/right and top/bottom feather radius at the same time.
        mImpressApi.setFeatherRadiusForStereoSurface(
                mEntityImpressNode, mFeatherRadiusX, mFeatherRadiusY);
    }

    @Override
    public float getFeatherRadiusY() {
        return mFeatherRadiusY;
    }

    // Note this returns the Impress node for the entity, not the subspace. The subspace Impress
    // node
    // is the parent of the entity Impress node.
    int getEntityImpressNode() {
        return mEntityImpressNode;
    }
}
