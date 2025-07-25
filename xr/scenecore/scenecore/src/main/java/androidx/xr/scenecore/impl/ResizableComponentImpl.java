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
import androidx.xr.runtime.internal.Dimensions;
import androidx.xr.runtime.internal.Entity;
import androidx.xr.runtime.internal.ResizableComponent;
import androidx.xr.runtime.internal.ResizeEvent;
import androidx.xr.runtime.internal.ResizeEventListener;
import androidx.xr.runtime.internal.Space;

import com.android.extensions.xr.XrExtensions;
import com.android.extensions.xr.function.Consumer;
import com.android.extensions.xr.node.NodeTransaction;
import com.android.extensions.xr.node.ReformEvent;
import com.android.extensions.xr.node.ReformOptions;
import com.android.extensions.xr.node.Vec3;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/** Implementation of ResizableComponent. */
@SuppressWarnings({"BanConcurrentHashMap"})
class ResizableComponentImpl implements ResizableComponent {

    private static final String TAG = "ResizableComponentImpl";

    private final XrExtensions mExtensions;
    private final ExecutorService mExecutor;
    private final ConcurrentHashMap<ResizeEventListener, Executor> mResizeEventListenerMap =
            new ConcurrentHashMap<>();
    // Visible for testing.
    Consumer<ReformEvent> mReformEventConsumer;
    private Entity mEntity;
    private Dimensions mCurrentSize;
    private Dimensions mMinSize;
    private Dimensions mMaxSize;
    private float mFixedAspectRatio = 0.0f;
    private boolean mAutoHideContent = true;
    private boolean mAutoUpdateSize = true;
    private boolean mForceShowResizeOverlay = false;

    ResizableComponentImpl(
            ExecutorService executor,
            XrExtensions extensions,
            Dimensions minSize,
            Dimensions maxSize) {
        mMinSize = minSize;
        mMaxSize = maxSize;
        mExtensions = extensions;
        mExecutor = executor;
    }

    @Override
    public boolean onAttach(@NonNull Entity entity) {
        if (mEntity != null) {
            Log.e(TAG, "Already attached to entity " + mEntity);
            return false;
        }
        mEntity = entity;
        ReformOptions reformOptions = ((AndroidXrEntity) entity).getReformOptions();
        ReformOptions unused =
                reformOptions.setEnabledReform(
                        reformOptions.getEnabledReform() | ReformOptions.ALLOW_RESIZE);

        // Update the current size if it's not set.
        // TODO: b/348037292 - Remove this special case for PanelEntityImpl.
        if (entity instanceof PanelEntityImpl && mCurrentSize == null) {
            mCurrentSize = ((PanelEntityImpl) entity).getSize();
            // TODO: b/350563642 - Add checks that size is within minsize and maxsize.
        }
        if (mCurrentSize != null) {
            unused =
                    reformOptions.setCurrentSize(
                            new Vec3(mCurrentSize.width, mCurrentSize.height, mCurrentSize.depth));
        }
        unused =
                reformOptions
                        .setMinimumSize(new Vec3(mMinSize.width, mMinSize.height, mMinSize.depth))
                        .setMaximumSize(new Vec3(mMaxSize.width, mMaxSize.height, mMaxSize.depth))
                        .setFixedAspectRatio(mFixedAspectRatio)
                        .setForceShowResizeOverlay(mForceShowResizeOverlay);
        ((AndroidXrEntity) entity).updateReformOptions();
        if (mReformEventConsumer != null) {
            ((AndroidXrEntity) entity).addReformEventConsumer(mReformEventConsumer, mExecutor);
        }
        return true;
    }

    @Override
    public void onDetach(@NonNull Entity entity) {
        ReformOptions reformOptions = ((AndroidXrEntity) entity).getReformOptions();
        ReformOptions unused =
                reformOptions.setEnabledReform(
                        reformOptions.getEnabledReform() & ~ReformOptions.ALLOW_RESIZE);
        ((AndroidXrEntity) entity).updateReformOptions();
        if (mReformEventConsumer != null) {
            ((AndroidXrEntity) entity).removeReformEventConsumer(mReformEventConsumer);
        }
        mEntity = null;
    }

    @Override
    public Dimensions getSize() {
        return mCurrentSize;
    }

    @Override
    public void setSize(@NonNull Dimensions size) {
        // TODO: b/350821054 - Implement synchronization policy around Entity/Component updates.
        mCurrentSize = size;
        if (mEntity == null) {
            Log.e(TAG, "This component isn't attached to an entity.");
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        ReformOptions unused =
                reformOptions.setCurrentSize(new Vec3(size.width, size.height, size.depth));
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    public Dimensions getMinimumSize() {
        return mMinSize;
    }

    @Override
    public void setMinimumSize(@NonNull Dimensions minSize) {
        mMinSize = minSize;
        if (mEntity == null) {
            Log.e(TAG, "This component isn't attached to an entity.");
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        ReformOptions unused =
                reformOptions.setMinimumSize(
                        new Vec3(minSize.width, minSize.height, minSize.depth));
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    public Dimensions getMaximumSize() {
        return mMaxSize;
    }

    @Override
    public void setMaximumSize(@NonNull Dimensions maxSize) {
        mMaxSize = maxSize;
        if (mEntity == null) {
            Log.e(TAG, "This component isn't attached to an entity.");
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        ReformOptions unused =
                reformOptions.setMaximumSize(
                        new Vec3(maxSize.width, maxSize.height, maxSize.depth));
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    public float getFixedAspectRatio() {
        return mFixedAspectRatio;
    }

    @Override
    public void setFixedAspectRatio(float fixedAspectRatio) {
        mFixedAspectRatio = fixedAspectRatio;
        if (mEntity == null) {
            Log.e(TAG, "This component isn't attached to an entity.");
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        ReformOptions unused = reformOptions.setFixedAspectRatio(fixedAspectRatio);
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    public boolean getAutoHideContent() {
        return mAutoHideContent;
    }

    @Override
    public void setAutoHideContent(boolean autoHideContent) {
        mAutoHideContent = autoHideContent;
    }

    @Override
    public boolean getAutoUpdateSize() {
        return mAutoUpdateSize;
    }

    @Override
    public void setAutoUpdateSize(boolean autoUpdateSize) {
        mAutoUpdateSize = autoUpdateSize;
    }

    @Override
    public boolean getForceShowResizeOverlay() {
        return mForceShowResizeOverlay;
    }

    @Override
    public void setForceShowResizeOverlay(boolean show) {
        mForceShowResizeOverlay = show;
        if (mEntity == null) {
            Log.e(TAG, "This component isn't attached to an entity.");
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        ReformOptions unused = reformOptions.setForceShowResizeOverlay(show);
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    public void addResizeEventListener(
            @NonNull Executor resizeExecutor, @NonNull ResizeEventListener resizeEventListener) {
        mResizeEventListenerMap.put(resizeEventListener, resizeExecutor);
        if (mReformEventConsumer != null) {
            return;
        }
        mReformEventConsumer =
                reformEvent -> {
                    if (reformEvent.getType() != ReformEvent.REFORM_TYPE_RESIZE) {
                        return;
                    }
                    // Set the alpha to 0 when the resize starts and restore when resize ends, to
                    // hide the
                    // entity content while it's being resized.
                    switch (reformEvent.getState()) {
                        case ReformEvent.REFORM_STATE_START:
                            if (mAutoHideContent) {
                                try (NodeTransaction transaction =
                                        mExtensions.createNodeTransaction()) {
                                    transaction
                                            .setAlpha(((AndroidXrEntity) mEntity).getNode(), 0f)
                                            .apply();
                                }
                            }
                            break;
                        case ReformEvent.REFORM_STATE_END:
                            if (mAutoHideContent) {
                                mEntity.setAlpha(mEntity.getAlpha(Space.PARENT), Space.PARENT);
                            }
                            break;
                        default:
                            break;
                    }
                    Dimensions newSize =
                            new Dimensions(
                                    reformEvent.getProposedSize().x,
                                    reformEvent.getProposedSize().y,
                                    reformEvent.getProposedSize().z);
                    if (mAutoUpdateSize) {
                        // Update the resize affordance size.
                        setSize(newSize);
                    }
                    mResizeEventListenerMap.forEach(
                            (listener, listenerExecutor) ->
                                    listenerExecutor.execute(
                                            () ->
                                                    listener.onResizeEvent(
                                                            new ResizeEvent(
                                                                    RuntimeUtils
                                                                            .getResizeEventState(
                                                                                    reformEvent
                                                                                            .getState()),
                                                                    newSize))));
                };
        if (mEntity == null) {
            Log.e(TAG, "This component isn't attached to an entity.");
            return;
        }
        ((AndroidXrEntity) mEntity).addReformEventConsumer(mReformEventConsumer, mExecutor);
    }

    @Override
    public void removeResizeEventListener(@NonNull ResizeEventListener resizeEventListener) {
        mResizeEventListenerMap.remove(resizeEventListener);
        if (mResizeEventListenerMap.isEmpty()) {
            ((AndroidXrEntity) mEntity).removeReformEventConsumer(mReformEventConsumer);
        }
    }
}
