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

import android.content.Context;

import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.internal.Dimensions;
import androidx.xr.scenecore.internal.Space;
import androidx.xr.scenecore.internal.SpaceValue;
import androidx.xr.scenecore.internal.SubspaceNodeEntity;
import androidx.xr.scenecore.internal.SubspaceNodeFeature;

import com.android.extensions.xr.XrExtensions;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Represents an entity that manages a subspace node.
 *
 * <p>This class manages the pose, scale, alpha, size and visibility of the subspace node enclosed
 * by this entity, and allows the entity to be user interactable. This entity doesn't have access to
 * underlying impress nodes like the [SurfaceEntityImpl], so it treats the subspace node as sibling
 * disjointed from scene graph and applies all transformations to it explicitly.
 */
final class SubspaceNodeEntityImpl extends BaseRenderingEntity implements SubspaceNodeEntity {
    private final SubspaceNodeFeature mFeature;

    SubspaceNodeEntityImpl(
            Context context,
            SubspaceNodeFeature feature,
            XrExtensions extensions,
            EntityManager entityManager,
            ScheduledExecutorService executor) {
        super(context, feature, extensions, entityManager, executor);
        mFeature = feature;
    }

    @Override
    public void setPose(@NonNull Pose pose, @SpaceValue int relativeTo) {
        super.setPose(pose, relativeTo);
        mFeature.setPose(pose);
    }

    @Override
    public void setScale(@NonNull Vector3 scale, @SpaceValue int relativeTo) {
        super.setScale(scale, relativeTo);
        mFeature.setScale(super.getScale(Space.ACTIVITY));
    }

    @Override
    public void setAlpha(float alpha, @SpaceValue int relativeTo) {
        super.setAlpha(alpha, relativeTo);
        mFeature.setAlpha(alpha);
    }

    @Override
    public void setSize(@NonNull Dimensions size) {
        mFeature.setSize(size);
    }

    @Override
    public @NonNull Dimensions getSize() {
        return mFeature.getSize();
    }

    @Override
    public void setHidden(boolean hidden) {
        super.setHidden(hidden);
        mFeature.setHidden(hidden);
    }
}
