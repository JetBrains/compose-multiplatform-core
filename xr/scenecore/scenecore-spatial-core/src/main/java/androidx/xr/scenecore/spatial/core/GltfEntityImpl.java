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

import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.GltfEntity;
import androidx.xr.runtime.internal.GltfFeature;
import androidx.xr.runtime.internal.MaterialResource;

import com.android.extensions.xr.XrExtensions;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Implementation of a SceneCore GltfEntity.
 *
 * <p>This is used to create an entity that contains a glTF object.
 */
class GltfEntityImpl extends BaseRenderingEntity implements GltfEntity {
    private final GltfFeature mFeature;

    GltfEntityImpl(
            Context context,
            GltfFeature feature,
            Entity parentEntity,
            XrExtensions extensions,
            EntityManager entityManager,
            ScheduledExecutorService executor) {
        super(context, feature, extensions, entityManager, executor);
        mFeature = feature;
        setParent(parentEntity);
    }

    @Override
    public void startAnimation(boolean looping, @Nullable String animationName) {
        mFeature.startAnimation(looping, animationName, mExecutor);
    }

    @Override
    public void stopAnimation() {
        mFeature.stopAnimation();
    }

    @Override
    @AnimationStateValue
    public int getAnimationState() {
        return mFeature.getAnimationState();
    }

    @Override
    public void setMaterialOverride(@NonNull MaterialResource material, @NonNull String meshName) {
        mFeature.setMaterialOverride(material, meshName);
    }

    @SuppressWarnings("ObjectToString")
    @Override
    public void dispose() {
        super.dispose();
    }

    public void setColliderEnabled(boolean enableCollider) {
        mFeature.setColliderEnabled(enableCollider);
    }
}
