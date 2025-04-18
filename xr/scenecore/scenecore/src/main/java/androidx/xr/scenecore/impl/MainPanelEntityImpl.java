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

import android.app.Activity;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.runtime.internal.PanelEntity;
import androidx.xr.runtime.internal.PixelDimensions;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;

import java.util.concurrent.ScheduledExecutorService;

/**
 * MainPanelEntity is a special instance of a PanelEntity that is backed by the WindowLeash CPM
 * node. The content of this PanelEntity is assumed to have been previously defined and associated
 * with the Window Leash Node.
 */
final class MainPanelEntityImpl extends BasePanelEntity implements PanelEntity {
    Activity mRuntimeActivity;

    // Note that we expect the Node supplied here to be the WindowLeash node.
    MainPanelEntityImpl(
            Activity activity,
            Node node,
            XrExtensions extensions,
            EntityManager entityManager,
            ScheduledExecutorService executor) {
        super(node, extensions, entityManager, executor);
        mRuntimeActivity = activity;

        // Read the Pixel dimensions for the primary panel off the Activity's WindowManager.
        //   Note that this requires MinAPI 30.
        // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
        Rect bounds = getBoundsFromWindowManager();
        super.setSizeInPixels(new PixelDimensions(bounds.width(), bounds.height()));
        float cornerRadius = getDefaultCornerRadiusInMeters();
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction.setCornerRadius(node, cornerRadius).apply();
        }
        setCornerRadiusValue(cornerRadius);
    }

    private Rect getBoundsFromWindowManager() {
        return mRuntimeActivity.getWindowManager().getCurrentWindowMetrics().getBounds();
    }

    @NonNull
    @Override
    public Dimensions getSize() {
        // The main panel bounds can change in HSM without JXRCore. Always read the bounds from the
        // WindowManager.
        Rect bounds = getBoundsFromWindowManager();
        float pixelDensity = getDefaultPixelDensity();
        return new Dimensions(bounds.width() / pixelDensity, bounds.height() / pixelDensity, 0);
    }

    @NonNull
    @Override
    public PixelDimensions getSizeInPixels() {
        // The main panel bounds can change in HSM without JXRCore. Always read the bounds from the
        // WindowManager.
        Rect bounds = getBoundsFromWindowManager();
        return new PixelDimensions(bounds.width(), bounds.height());
    }

    @Override
    public void setSizeInPixels(@NonNull PixelDimensions dimensions) {
        // TODO: b/376126162 - Consider calling setPixelDimensions() either when setMainWindowSize's
        // callback is called, or when the next spatial state callback with the expected size is
        // called.
        super.setSizeInPixels(dimensions);
        // TODO: b/376934871 - Check async results.
        mExtensions.setMainWindowSize(
                mRuntimeActivity,
                dimensions.width,
                dimensions.height,
                (result) -> {},
                Runnable::run);
    }
}
