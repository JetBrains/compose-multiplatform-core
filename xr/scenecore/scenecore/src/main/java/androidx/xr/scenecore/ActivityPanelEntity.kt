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

package androidx.xr.scenecore

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.ActivityPanelEntity as RtActivityPanelEntity
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.math.Pose

/**
 * ActivityPanelEntity creates a spatial panel for embedding an Activity in Android XR. Users can
 * either use an intent to launch an activity in the given panel or provide an instance of activity
 * to move into this panel. Calling dispose() on this entity will destroy the underlying activity.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ActivityPanelEntity
private constructor(
    private val rtActivityPanelEntity: RtActivityPanelEntity,
    entityManager: EntityManager,
) : PanelEntity(rtActivityPanelEntity, entityManager) {

    /**
     * Launches an activity in the given panel. Subsequent calls to this method will replace the
     * already existing activity in the panel with the new one. If the intent fails to launch the
     * activity, the panel will not be visible. Note this will not update the dimensions of the
     * surface underlying the panel. The Activity will be letterboxed as required to fit the size of
     * the panel. The underlying surface can be resized by calling setSizeInPixels().
     *
     * @param intent Intent to launch the activity.
     * @param bundle Bundle to pass to the activity, can be null.
     */
    @JvmOverloads
    public fun launchActivity(intent: Intent, bundle: Bundle? = null) {
        rtActivityPanelEntity.launchActivity(intent, bundle)
    }

    /**
     * Moves the given activity into this panel. Note this will not update the dimensions of the
     * surface underlying the panel. The Activity will be letterboxed as required to fit the size of
     * the panel. The underlying surface can be resized by calling setSizeInPixels().
     *
     * @param activity Activity to move into this panel.
     */
    public fun moveActivity(activity: Activity) {
        rtActivityPanelEntity.moveActivity(activity)
    }

    public companion object {
        /**
         * Factory method for ActivityPanelEntity.
         *
         * @param adapter JxrPlatformAdapter to use.
         * @param windowBoundsPx Bounds for the underlying surface for the given view.
         * @param name Name of this panel.
         * @param hostActivity Activity which created this panel.
         * @param pose Pose for this panel, relative to its parent.
         */
        internal fun create(
            adapter: JxrPlatformAdapter,
            entityManager: EntityManager,
            windowBoundsPx: PixelDimensions,
            name: String,
            hostActivity: Activity,
            pose: Pose = Pose.Identity,
        ): ActivityPanelEntity =
            ActivityPanelEntity(
                adapter.createActivityPanelEntity(
                    pose,
                    windowBoundsPx.toRtPixelDimensions(),
                    name,
                    hostActivity,
                    adapter.activitySpaceRootImpl,
                ),
                entityManager,
            )

        // TODO(b/352629832): Update windowBoundsPx to be a PixelDimensions
        /**
         * Public factory function for a spatial ActivityPanelEntity.
         *
         * @param session Session to create the ActivityPanelEntity in.
         * @param windowBoundsPx Bounds for the panel window in pixels.
         * @param name Name of the panel.
         * @param pose Pose of this entity relative to its parent, default value is Identity.
         * @return an ActivityPanelEntity instance.
         */
        @JvmOverloads
        @JvmStatic
        public fun create(
            session: Session,
            windowBoundsPx: Rect,
            name: String,
            pose: Pose = Pose.Identity,
        ): ActivityPanelEntity =
            ActivityPanelEntity.create(
                session.platformAdapter,
                session.scene.entityManager,
                PixelDimensions(windowBoundsPx.width(), windowBoundsPx.height()),
                name,
                session.activity,
                pose,
            )
    }
}
