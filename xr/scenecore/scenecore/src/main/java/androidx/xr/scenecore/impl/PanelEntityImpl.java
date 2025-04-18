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
import android.content.Context;
import android.os.Binder;
import android.util.Log;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.runtime.internal.PanelEntity;
import androidx.xr.runtime.internal.PixelDimensions;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;
import com.android.extensions.xr.node.NodeTransaction;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Implementation of PanelEntity.
 *
 * <p>(Requires API Level 30)
 *
 * <p>This entity shows 2D view on spatial panel.
 */
final class PanelEntityImpl extends BasePanelEntity implements PanelEntity {
    private static final String TAG = "PanelEntity";
    private final SurfaceControlViewHost mSurfaceControlViewHost;

    PanelEntityImpl(
            @NonNull Context context,
            Node node,
            @NonNull View view,
            XrExtensions extensions,
            EntityManager entityManager,
            PixelDimensions surfaceDimensionsPx,
            @NonNull String name,
            ScheduledExecutorService executor) {
        super(node, extensions, entityManager, executor);
        mSurfaceControlViewHost =
                new SurfaceControlViewHost(
                        context, Objects.requireNonNull(context.getDisplay()), new Binder());
        setupSurfaceControlViewHostAndCornerRadius(view, surfaceDimensionsPx, name);
        setDefaultOnBackInvokedCallback(view);
    }

    PanelEntityImpl(
            @NonNull Context context,
            Node node,
            @NonNull View view,
            XrExtensions extensions,
            EntityManager entityManager,
            Dimensions surfaceDimensions,
            @NonNull String name,
            ScheduledExecutorService executor) {
        super(node, extensions, entityManager, executor);
        float unscaledPixelDensity = getDefaultPixelDensity();
        PixelDimensions surfaceDimensionsPx =
                new PixelDimensions(
                        (int) (surfaceDimensions.width * unscaledPixelDensity),
                        (int) (surfaceDimensions.height * unscaledPixelDensity));
        mSurfaceControlViewHost =
                new SurfaceControlViewHost(
                        context, Objects.requireNonNull(context.getDisplay()), new Binder());
        setupSurfaceControlViewHostAndCornerRadius(view, surfaceDimensionsPx, name);
        setDefaultOnBackInvokedCallback(view);
    }

    // TODO(b/352827267): Enforce minSDK API strategy - go/androidx-api-guidelines#compat-newapi
    private void setupSurfaceControlViewHostAndCornerRadius(
            @NonNull View view,
            @NonNull PixelDimensions surfaceDimensionsPx,
            @NonNull String name) {
        mSurfaceControlViewHost.setView(
                view, surfaceDimensionsPx.width, surfaceDimensionsPx.height);

        SurfacePackage surfacePackage =
                Objects.requireNonNull(mSurfaceControlViewHost.getSurfacePackage());

        // We need to manually inform our base class of the pixelDimensions, even though the
        // Extensions
        // are initialized in the factory method. (ext.setWindowBounds, etc)
        super.setSizeInPixels(surfaceDimensionsPx);
        float cornerRadius = getDefaultCornerRadiusInMeters();
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction
                    .setName(mNode, name)
                    .setSurfacePackage(mNode, surfacePackage)
                    .setWindowBounds(
                            surfacePackage, surfaceDimensionsPx.width, surfaceDimensionsPx.height)
                    .setVisibility(mNode, true)
                    .setCornerRadius(mNode, cornerRadius)
                    .apply();
        } finally {
            surfacePackage.release();
        }
        super.setCornerRadiusValue(cornerRadius);
    }

    @SuppressWarnings("deprecation") // TODO: b/398052385 - Replace deprecate onBackPressed.
    private void setDefaultOnBackInvokedCallback(View view) {
        OnBackInvokedCallback onBackInvokedCallback =
                () -> {
                    if (view.getContext() instanceof Activity) {
                        ((Activity) view.getContext()).onBackPressed();
                    }
                };
        OnBackInvokedDispatcher backDispatcher = view.findOnBackInvokedDispatcher();
        if (backDispatcher != null) {
            backDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, onBackInvokedCallback);
        }
    }

    @Override
    public void setSizeInPixels(@NonNull PixelDimensions dimensions) {
        super.setSizeInPixels(dimensions);

        SurfacePackage surfacePackage =
                Objects.requireNonNull(mSurfaceControlViewHost.getSurfacePackage());

        mSurfaceControlViewHost.relayout(dimensions.width, dimensions.height);
        try (NodeTransaction transaction = mExtensions.createNodeTransaction()) {
            transaction
                    .setWindowBounds(surfacePackage, dimensions.width, dimensions.height)
                    .apply();
        }
        surfacePackage.release();
    }

    @SuppressWarnings("ObjectToString")
    @Override
    public void dispose() {
        Log.i(TAG, "Disposing " + this);
        mSurfaceControlViewHost.release();
        super.dispose();
    }
}
