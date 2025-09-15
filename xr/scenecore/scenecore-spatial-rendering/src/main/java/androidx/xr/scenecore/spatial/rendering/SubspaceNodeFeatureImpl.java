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

import androidx.annotation.RestrictTo;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.impl.impress.ImpressApi;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.internal.SubspaceNodeFeature;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;

import com.google.androidxr.splitengine.SplitEngineSubspaceManager;

import org.jspecify.annotations.NonNull;

/**
 * Represents an entity that manages a subspace node.
 *
 * <p>This class manages the pose, scale, alpha, size and visibility of the subspace node enclosed
 * by this entity, and allows the entity to be user interactable. This entity doesn't have access to
 * underlying impress nodes like the [SurfaceEntityImpl], so it treats the subspace node as sibling
 * disjointed from scene graph and applies all transformations to it explicitly.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
final class SubspaceNodeFeatureImpl extends BaseRenderingFeature implements SubspaceNodeFeature {
    private final Node mSubspaceNode;
    private Dimensions mSize;
    private Vector3 mScale = Vector3.One;

    SubspaceNodeFeatureImpl(
            ImpressApi impressApi,
            SplitEngineSubspaceManager splitEngineSubspaceManager,
            XrExtensions extensions,
            Node subspaceNode,
            Dimensions size) {
        super(impressApi, splitEngineSubspaceManager, extensions);
        mSubspaceNode = subspaceNode;
    }

    @Override
    public void setPose(@NonNull Pose pose) {
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction
                    .setPosition(
                            mSubspaceNode,
                            pose.getTranslation().getX(),
                            pose.getTranslation().getY(),
                            pose.getTranslation().getZ())
                    .setOrientation(
                            mSubspaceNode,
                            pose.getRotation().getX(),
                            pose.getRotation().getY(),
                            pose.getRotation().getZ(),
                            pose.getRotation().getW())
                    .apply();
        }
    }

    @Override
    public void setScale(@NonNull Vector3 scaleActivity) {
        mScale = scaleActivity;
        Dimensions size =
                new Dimensions(
                        mSize.width * mScale.getX(),
                        mSize.height * mScale.getY(),
                        mSize.depth * mScale.getZ());
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setScale(mSubspaceNode, size.width, size.height, size.depth).apply();
        }
    }

    @Override
    public void setAlpha(float alpha) {
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setAlpha(mSubspaceNode, alpha).apply();
        }
    }

    @Override
    public void setSize(@NonNull Dimensions size) {
        mSize = size;
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction
                    .setScale(
                            mSubspaceNode,
                            size.width * mScale.getX(),
                            size.height * mScale.getY(),
                            size.depth * mScale.getZ())
                    .apply();
        }
    }

    @Override
    public @NonNull Dimensions getSize() {
        return mSize;
    }

    @Override
    public void setHidden(boolean hidden) {
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setVisibility(mSubspaceNode, !hidden).apply();
        }
    }

    @Override
    public void dispose() {
    }
}
