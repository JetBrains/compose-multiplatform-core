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

import android.util.Log
import androidx.annotation.RestrictTo
import androidx.xr.runtime.Session
import androidx.xr.runtime.internal.JxrPlatformAdapter
import androidx.xr.runtime.internal.SpatialPointerIcon as RtSpatialPointerIcon

/**
 * Component that modifies the pointer icon. Adding or removing this component sets the entity's
 * pointer to use the default, system-determined icon.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SpatialPointerComponent
private constructor(private val platformAdapter: JxrPlatformAdapter) : Component {
    private val TAG = "SpatialPointerComponent"

    private val rtComponent by lazy { platformAdapter.createSpatialPointerComponent() }

    private var entity: Entity? = null

    /**
     * The [SpatialPointerIcon] that will be rendered on the component's entity. A `null` value
     * indicates the default pointer icon should be used.
     */
    public var spatialPointerIcon: SpatialPointerIcon?
        get() = rtComponent.getSpatialPointerIcon().toSpatialPointerIcon()
        set(value) =
            rtComponent.setSpatialPointerIcon(
                if (value == null) RtSpatialPointerIcon.TYPE_DEFAULT
                else value.toRtSpatialPointerIcon()
            )

    override fun onAttach(entity: Entity): Boolean {
        if (this.entity != null) {
            Log.e(TAG, "Already attached to entity ${this.entity}")
            return false
        }
        if ((entity as BaseEntity<*>).rtEntity.addComponent(rtComponent)) {
            this.entity = entity
            spatialPointerIcon = null
            return true
        } else {
            return false
        }
    }

    override fun onDetach(entity: Entity) {
        spatialPointerIcon = null
        (entity as BaseEntity<*>).rtEntity.removeComponent(rtComponent)
        this.entity = null
    }

    public companion object {

        /**
         * Creates a new [SpatialPointerComponent].
         *
         * @param session The session to use for creating the component.
         * @return A new [SpatialPointerComponent].
         */
        @JvmStatic
        public fun create(session: Session): SpatialPointerComponent {
            return SpatialPointerComponent(session.platformAdapter)
        }
    }
}
