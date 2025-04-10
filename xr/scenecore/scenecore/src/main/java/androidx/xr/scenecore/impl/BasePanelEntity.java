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

import static java.lang.Math.min;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.runtime.internal.PanelEntity;
import androidx.xr.runtime.internal.PixelDimensions;
import androidx.xr.runtime.math.Vector3;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;

import java.util.concurrent.ScheduledExecutorService;

/** BasePanelEntity provides implementations of capabilities common to PanelEntities. */
abstract class BasePanelEntity extends AndroidXrEntity implements PanelEntity {
    private static final float DEFAULT_CORNER_RADIUS_DP = 32.0f;
    protected PixelDimensions mPixelDimensions;
    private float mCornerRadius;

    BasePanelEntity(
            Node node,
            XrExtensions extensions,
            EntityManager entityManager,
            ScheduledExecutorService executor) {
        super(node, extensions, entityManager, executor);
    }

    protected float getDefaultPixelDensity() {
        return mExtensions
                .getConfig()
                .defaultPixelsPerMeter(Resources.getSystem().getDisplayMetrics().density);
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(34)
    protected float getDefaultCornerRadiusInMeters() {
        // Get the width and height of the panel in DP.
        float widthDp =
                TypedValue.deriveDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        mPixelDimensions.width,
                        Resources.getSystem().getDisplayMetrics());
        float heightDp =
                TypedValue.deriveDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        mPixelDimensions.height,
                        Resources.getSystem().getDisplayMetrics());
        float radiusDp = DEFAULT_CORNER_RADIUS_DP;

        // If the pixel dimensions are smaller than the default corner radius, use the smaller of
        // the
        // two dimensions as the corner radius.
        if (mPixelDimensions != null
                && (widthDp < DEFAULT_CORNER_RADIUS_DP * 2
                        || heightDp < DEFAULT_CORNER_RADIUS_DP * 2)) {
            radiusDp = min(widthDp / 2, heightDp / 2);
        }

        // Convert the updated corner radius to pixels.
        float radiusPixels =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        radiusDp,
                        Resources.getSystem().getDisplayMetrics());

        // Convert the pixel radius to meters.
        return radiusPixels / getDefaultPixelDensity();
    }

    @NonNull
    @Override
    @Deprecated
    public Vector3 getPixelDensity() {
        Vector3 scale = getWorldSpaceScale();
        float defaultPixelDensity = getDefaultPixelDensity();
        return new Vector3(
                defaultPixelDensity / scale.getX(),
                defaultPixelDensity / scale.getY(),
                defaultPixelDensity / scale.getZ());
    }

    @NonNull
    @Override
    public Dimensions getSize() {
        float pixelDensity = getDefaultPixelDensity();
        return new Dimensions(
                mPixelDimensions.width / pixelDensity, mPixelDimensions.height / pixelDensity, 0);
    }

    @Override
    public void setSize(@NonNull Dimensions dimensions) {
        float pixelDensity = getDefaultPixelDensity();
        setSizeInPixels(
                new PixelDimensions(
                        (int) (dimensions.width * pixelDensity),
                        (int) (dimensions.height * pixelDensity)));
    }

    @NonNull
    @Override
    public PixelDimensions getSizeInPixels() {
        return mPixelDimensions;
    }

    @Override
    public void setSizeInPixels(@NonNull PixelDimensions dimensions) {
        mPixelDimensions = dimensions;
    }

    @Override
    public void setCornerRadius(float value) {
        if (value < 0.0f) {
            throw new IllegalArgumentException("Corner radius can't be negative: " + value);
        }
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setCornerRadius(mNode, value).apply();
            mCornerRadius = value;
        }
    }

    // Sets just the value of the corner radius, without updating the node. This should be only be
    // used when constructing the entity so that the stored value is consistent with the value set
    // in
    // the node transaction.
    public void setCornerRadiusValue(float value) {
        mCornerRadius = value;
    }

    @Override
    public float getCornerRadius() {
        return mCornerRadius;
    }
}
