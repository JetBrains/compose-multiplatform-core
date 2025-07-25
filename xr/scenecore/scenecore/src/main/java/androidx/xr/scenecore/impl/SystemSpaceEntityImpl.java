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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.xr.runtime.internal.SystemSpaceEntity;
import androidx.xr.runtime.math.Matrix4;
import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Vector3;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.node.Node;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * A parentless system-controlled JXRCore Entity that defines its own coordinate space.
 *
 * <p>It is expected to be the soft root of its own parent-child entity hierarchy.
 */
abstract class SystemSpaceEntityImpl extends AndroidXrEntity implements SystemSpaceEntity {

    protected Pose mOpenXrReferenceSpacePose;
    protected Vector3 mWorldSpaceScale = new Vector3(1f, 1f, 1f);
    // Visible for testing.
    Closeable mNodeTransformCloseable;
    private OnSpaceUpdatedListener mSpaceUpdatedListener;
    private Executor mSpaceUpdatedExecutor;

    SystemSpaceEntityImpl(
            Node node,
            XrExtensions extensions,
            EntityManager entityManager,
            ScheduledExecutorService executor) {
        super(node, extensions, entityManager, executor);

        // The underlying CPM node is always expected to be updated in response to changes to
        // the coordinate space represented by a SystemSpaceEntityImpl so we subscribe at
        // construction.
        subscribeToNodeTransform(node, executor);
    }

    /** Called when the underlying space has changed. */
    public void onSpaceUpdated() {
        if (mSpaceUpdatedListener != null) {
            mSpaceUpdatedExecutor.execute(() -> mSpaceUpdatedListener.onSpaceUpdated());
        }
    }

    /** Registers the SDK layer / application's listener for space updates. */
    @Override
    public void setOnSpaceUpdatedListener(
            @Nullable OnSpaceUpdatedListener listener, @Nullable Executor executor) {
        mSpaceUpdatedListener = listener;
        mSpaceUpdatedExecutor = executor == null ? mExecutor : executor;
    }

    /**
     * Returns the pose relative to an OpenXR reference space.
     *
     * <p>The OpenXR reference space is the space returned by {@link
     * XrExtensions#getOpenXrActivitySpaceType()}
     */
    public Pose getPoseInOpenXrReferenceSpace() {
        return mOpenXrReferenceSpacePose;
    }

    /**
     * Sets the pose and scale of the entity in an OpenXR reference space and should call the
     * onSpaceUpdated() callback to signal a change in the underlying space.
     *
     * @param openXrReferenceSpaceTransform 4x4 transformation matrix of the entity in an OpenXR
     *     reference space. The OpenXR reference space is of the type defined by the {@link
     *     XrExtensions#getOpenXrActivitySpaceType()} method.
     */
    protected void setOpenXrReferenceSpacePose(Matrix4 openXrReferenceSpaceTransform) {
        // TODO: b/353511649 - Make SystemSpaceEntityImpl thread safe.
        mOpenXrReferenceSpacePose = Matrix4Ext.getUnscaled(openXrReferenceSpaceTransform).getPose();

        // TODO: b/367780918 - Consider using Matrix4.scale when it is fixed.
        // Retrieve the scale from the matrix. The scale can be retrieved from the matrix by getting
        // the magnitude of one of the rows of the matrix. Note that we are assuming uniform scale.
        // SpaceFlinger might apply a scale to the task node, for example if the user caused the
        // main
        // panel to scale in Homespace mode.
        float data00 = openXrReferenceSpaceTransform.getData()[0];
        float data01 = openXrReferenceSpaceTransform.getData()[1];
        float data02 = openXrReferenceSpaceTransform.getData()[2];
        float scale = (float) Math.sqrt(data00 * data00 + data01 * data01 + data02 * data02);
        mWorldSpaceScale = new Vector3(scale, scale, scale);
        this.setScaleInternal(new Vector3(scale, scale, scale));
        onSpaceUpdated();
    }

    /**
     * Subscribes to the node's transform update events and caches the pose by calling
     * setOpenXrReferenceSpacePose().
     *
     * @param node The node to subscribe to.
     * @param executor The executor to run the callback on.
     */
    private void subscribeToNodeTransform(Node node, ScheduledExecutorService executor) {
        mNodeTransformCloseable =
                node.subscribeToTransform(
                        (transform) ->
                                setOpenXrReferenceSpacePose(
                                        RuntimeUtils.getMatrix(transform.getTransform())),
                        executor);
    }

    @NonNull
    @Override
    public Vector3 getWorldSpaceScale() {
        return mWorldSpaceScale;
    }

    /** Unsubscribes from the node's transform update events. */
    private void unsubscribeFromNodeTransform() {
        try {
            mNodeTransformCloseable.close();
        } catch (Exception e) {
            Log.w(
                    "SystemSpaceEntity",
                    "Could not close node transform subscription with error: " + e.getMessage());
        }
    }

    @Override
    public void dispose() {
        unsubscribeFromNodeTransform();
        super.dispose();
    }
}
