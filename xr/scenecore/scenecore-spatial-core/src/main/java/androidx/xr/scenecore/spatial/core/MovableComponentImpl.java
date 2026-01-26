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

import androidx.xr.runtime.math.Pose;
import androidx.xr.runtime.math.Ray;
import androidx.xr.runtime.math.Vector3;
import androidx.xr.scenecore.runtime.Dimensions;
import androidx.xr.scenecore.runtime.Entity;
import androidx.xr.scenecore.runtime.InputEvent;
import androidx.xr.scenecore.runtime.InputEventListener;
import androidx.xr.scenecore.runtime.MovableComponent;
import androidx.xr.scenecore.runtime.MoveEvent;
import androidx.xr.scenecore.runtime.MoveEventListener;
import androidx.xr.scenecore.runtime.Space;

import com.android.extensions.xr.function.Consumer;
import com.android.extensions.xr.node.ReformEvent;
import com.android.extensions.xr.node.ReformOptions;
import com.android.extensions.xr.node.Vec3;

import org.jspecify.annotations.NonNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/** Implementation of MovableComponent. */
@SuppressWarnings("BanConcurrentHashMap")
class MovableComponentImpl implements MovableComponent {
    private final boolean mSystemMovable;
    private final boolean mScaleInZ;
    private final ActivitySpaceImpl mActivitySpaceImpl;
    private final PanelShadowRenderer mPanelShadowRenderer;
    private final ScheduledExecutorService mRuntimeExecutor;
    private final ConcurrentHashMap<MoveEventListener, Executor> mMoveEventListenersMap =
            new ConcurrentHashMap<>();
    private final boolean mUserAnchorable;
    // Visible for testing.
    Consumer<ReformEvent> mReformEventConsumer;
    private final InputEventListener mInputEventListener;
    private volatile Entity mEntity;
    private Entity mInitialParent;
    private Pose mLastPose = new Pose();
    private Vector3 mLastScale = new Vector3(1f, 1f, 1f);
    private Ray mInitialRay;
    private Dimensions mCurrentSize;
    private boolean mIsMoving = false;
    @ScaleWithDistanceMode private int mScaleWithDistanceMode = ScaleWithDistanceMode.DEFAULT;
    private float mHitPointToOriginDistance = 0f;
    private Vector3 mGrabPointToCenterOffset = Vector3.Zero;

    MovableComponentImpl(
            boolean systemMovable,
            boolean scaleInZ,
            boolean userAnchorable,
            ActivitySpaceImpl activitySpaceImpl,
            PanelShadowRenderer panelShadowRenderer,
            ScheduledExecutorService runtimeExecutor) {
        mSystemMovable = systemMovable;
        mScaleInZ = scaleInZ;
        mActivitySpaceImpl = activitySpaceImpl;
        mPanelShadowRenderer = panelShadowRenderer;
        mRuntimeExecutor = runtimeExecutor;
        mUserAnchorable = userAnchorable;
        mReformEventConsumer =
                reformEvent -> {
                    if (reformEvent.getType() != ReformEvent.REFORM_TYPE_MOVE) {
                        return;
                    }
                    if (reformEvent.getState() == ReformEvent.REFORM_STATE_START) {
                        final Entity entity = mEntity;
                        mInitialParent =
                                (entity != null && entity.getParent() != null)
                                        ? mEntity.getParent()
                                        : mActivitySpaceImpl;
                        mIsMoving = true;
                    } else if (reformEvent.getState() == ReformEvent.REFORM_STATE_END) {
                        mIsMoving = false;
                        mPanelShadowRenderer.destroy();
                    }

                    Pose newPose =
                            RuntimeUtils.getPose(
                                    reformEvent.getProposedPosition(),
                                    reformEvent.getProposedOrientation());
                    Vector3 newScale =
                            mScaleInZ
                                    ? RuntimeUtils.getVector3(reformEvent.getProposedScale())
                                    : mLastScale;

                    mMoveEventListenersMap.forEach(
                            (listener, listenerExecutor) ->
                                    listenerExecutor.execute(
                                            () ->
                                                    listener.onMoveEvent(
                                                            getMoveEvent(
                                                                    reformEvent,
                                                                    newPose,
                                                                    newScale))));
                    mLastPose = newPose;
                    mLastScale = newScale;
                };

        mInputEventListener =
                inputEvent ->
                        mMoveEventListenersMap.forEach(
                                (listener, executor) ->
                                        executor.execute(
                                                () -> {
                                                    listener.onMoveEvent(getMoveEvent(inputEvent));
                                                }));
    }

    private static int translateScaleWithDistanceMode(@ScaleWithDistanceMode int scale) {
        if (scale == ScaleWithDistanceMode.DMM) {
            return ReformOptions.SCALE_WITH_DISTANCE_MODE_DMM;
        }
        return ReformOptions.SCALE_WITH_DISTANCE_MODE_DEFAULT;
    }

    private MoveEvent getMoveEvent(ReformEvent reformEvent, Pose newPose, Vector3 newScale) {
        return new MoveEvent(
                reformEvent.getState(),
                new Ray(
                        RuntimeUtils.getVector3(reformEvent.getInitialRayOrigin()),
                        RuntimeUtils.getVector3(reformEvent.getInitialRayDirection())),
                new Ray(
                        RuntimeUtils.getVector3(reformEvent.getCurrentRayOrigin()),
                        RuntimeUtils.getVector3(reformEvent.getCurrentRayDirection())),
                mLastPose,
                newPose,
                mLastScale,
                newScale,
                mInitialParent,
                null,
                null);
    }

    private MoveEvent getMoveEvent(InputEvent inputEvent) {
        int moveState = -1;

        Entity parent =
                (mEntity != null && mEntity.getParent() != null)
                        ? mEntity.getParent()
                        : mActivitySpaceImpl;

        Vector3 originInParentSpace =
                mActivitySpaceImpl.transformPositionTo(inputEvent.getOrigin(), parent);
        Vector3 directionInParentSpace =
                mActivitySpaceImpl.transformDirectionTo(inputEvent.getDirection(), parent);
        Ray currentRay = new Ray(originInParentSpace, directionInParentSpace);

        switch (inputEvent.getAction()) {
            case InputEvent.Action.DOWN:
                moveState = MoveEvent.MoveState.MOVE_STATE_START;
                mInitialRay = new Ray(inputEvent.getOrigin(), inputEvent.getDirection());
                mInitialParent = parent;
                if (!inputEvent.getHitInfoList().isEmpty()) {
                    Vector3 hitPosition = inputEvent.getHitInfoList().get(0).getHitPosition();
                    mHitPointToOriginDistance = hitPosition.minus(originInParentSpace).getLength();
                    mGrabPointToCenterOffset =
                            mEntity.getPose().getTranslation().minus(hitPosition);
                }
                break;
            case InputEvent.Action.MOVE:
                moveState = MoveEvent.MoveState.MOVE_STATE_ONGOING;
                break;
            case InputEvent.Action.UP:
                moveState = MoveEvent.MoveState.MOVE_STATE_END;
                break;
        }

        Vector3 grabPoint =
                originInParentSpace.plus(
                        directionInParentSpace.toNormalized().times(mHitPointToOriginDistance));
        Vector3 proposedTranslation = grabPoint.plus(mGrabPointToCenterOffset);
        Pose proposedPose = new Pose(proposedTranslation, mEntity.getPose().getRotation());

        MoveEvent moveEvent =
                new MoveEvent(
                        moveState,
                        mInitialRay,
                        currentRay,
                        mLastPose,
                        proposedPose,
                        mLastScale,
                        mEntity.getScale(),
                        mInitialParent,
                        null,
                        null);
        mLastPose = mEntity.getPose();
        mLastScale = mEntity.getScale();
        return moveEvent;
    }

    @Override
    public boolean onAttach(@NonNull Entity entity) {
        if (mEntity != null) {
            return false;
        }
        mEntity = entity;
        mLastPose = entity.getPose(Space.PARENT);
        mLastScale = entity.getScale(Space.PARENT);

        if (entity instanceof GltfEntityImpl) {
            ((GltfEntityImpl) entity)
                    .setReformAffordanceEnabled(
                            /* enabled */ true, mSystemMovable && !mUserAnchorable);
            entity.addInputEventListener(mRuntimeExecutor, mInputEventListener);
            return true;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) entity).getReformOptions();
        int reformFlags = ReformOptions.FLAG_POSE_RELATIVE_TO_PARENT;
        reformFlags =
                (mSystemMovable && !mUserAnchorable)
                        ? reformFlags | ReformOptions.FLAG_ALLOW_SYSTEM_MOVEMENT
                        : reformFlags;
        reformFlags =
                mScaleInZ ? reformFlags | ReformOptions.FLAG_SCALE_WITH_DISTANCE : reformFlags;
        reformOptions.setFlags(reformFlags);
        reformOptions
                .setEnabledReform(reformOptions.getEnabledReform() | ReformOptions.ALLOW_MOVE)
                .setScaleWithDistanceMode(translateScaleWithDistanceMode(mScaleWithDistanceMode));

        // TODO: b/348037292 - Remove this special case for PanelEntityImpl.
        if (entity instanceof PanelEntityImpl && mCurrentSize == null) {
            mCurrentSize = ((PanelEntityImpl) entity).getSize();
        }
        if (mCurrentSize != null) {
            reformOptions.setCurrentSize(
                    new Vec3(mCurrentSize.width, mCurrentSize.height, mCurrentSize.depth));
        }
        ((AndroidXrEntity) entity).updateReformOptions();
        ((AndroidXrEntity) mEntity).addReformEventConsumer(mReformEventConsumer, mRuntimeExecutor);
        return true;
    }

    @Override
    public void onDetach(@NonNull Entity entity) {
        if (entity instanceof GltfEntityImpl) {
            ((GltfEntityImpl) entity)
                    .setReformAffordanceEnabled(
                            /* enabled */ false, mSystemMovable && !mUserAnchorable);
            entity.removeInputEventListener(mInputEventListener);
            mEntity = null;
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) entity).getReformOptions();
        reformOptions.setEnabledReform(
                reformOptions.getEnabledReform() & ~ReformOptions.ALLOW_MOVE);
        // Clear any flags that were set by this component.
        int reformFlags = reformOptions.getFlags();
        reformFlags =
                mSystemMovable
                        ? reformFlags & ~ReformOptions.FLAG_ALLOW_SYSTEM_MOVEMENT
                        : reformFlags;
        reformFlags =
                mScaleInZ ? reformFlags & ~ReformOptions.FLAG_SCALE_WITH_DISTANCE : reformFlags;
        reformOptions.setFlags(reformFlags);
        ((AndroidXrEntity) entity).updateReformOptions();
        ((AndroidXrEntity) entity).removeReformEventConsumer(mReformEventConsumer);
        mEntity = null;
    }

    @Override
    public @NonNull Dimensions getSize() {
        return mCurrentSize;
    }

    @Override
    public void setSize(@NonNull Dimensions dimensions) {
        mCurrentSize = dimensions;
        if (mEntity == null) {
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        reformOptions.setCurrentSize(
                new Vec3(dimensions.width, dimensions.height, dimensions.depth));
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    @ScaleWithDistanceMode
    public int getScaleWithDistanceMode() {
        return mScaleWithDistanceMode;
    }

    @Override
    public void setScaleWithDistanceMode(@ScaleWithDistanceMode int scaleWithDistanceMode) {
        mScaleWithDistanceMode = scaleWithDistanceMode;
        if (mEntity == null) {
            return;
        }
        ReformOptions reformOptions = ((AndroidXrEntity) mEntity).getReformOptions();
        reformOptions.setScaleWithDistanceMode(
                translateScaleWithDistanceMode(scaleWithDistanceMode));
        ((AndroidXrEntity) mEntity).updateReformOptions();
    }

    @Override
    public void addMoveEventListener(@NonNull MoveEventListener moveEventListener) {
        mMoveEventListenersMap.put(moveEventListener, mRuntimeExecutor);
    }

    @Override
    public void addMoveEventListener(
            @NonNull Executor executor, @NonNull MoveEventListener moveEventListener) {
        mMoveEventListenersMap.put(moveEventListener, executor);
    }

    @Override
    public void removeMoveEventListener(@NonNull MoveEventListener moveEventListener) {
        mMoveEventListenersMap.remove(moveEventListener);
    }

    private void tryRenderPlaneShadow(Pose proposedPose, Pose planePose) {
        if (!shouldRenderPlaneShadow()) {
            return;
        }
        mPanelShadowRenderer.updatePanelPose(proposedPose, planePose, (BasePanelEntity) mEntity);
    }

    private boolean shouldRenderPlaneShadow() {
        return mEntity instanceof BasePanelEntity && mUserAnchorable && mIsMoving;
    }

    @Override
    public void setPlanePoseForMoveUpdatePose(Pose planePose, @NonNull Pose moveUpdatePose) {
        if (planePose == null) {
            mPanelShadowRenderer.hidePlane();
        } else {
            tryRenderPlaneShadow(moveUpdatePose, planePose);
        }
    }
}
