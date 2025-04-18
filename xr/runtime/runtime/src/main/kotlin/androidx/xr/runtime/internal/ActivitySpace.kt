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

package androidx.xr.runtime.internal

import androidx.annotation.RestrictTo

/**
 * Interface for a SceneCore activity space. There is one activity space and it is the ancestor for
 * all elements in the scene. The activity space does not have a parent.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface ActivitySpace : SystemSpaceEntity {
    /** Returns the bounds of this ActivitySpace. */
    public val bounds: Dimensions

    /**
     * Adds a listener to be called when the bounds of the primary Activity change. If the same
     * listener is added multiple times, it will only fire each event on time.
     *
     * @param listener The listener to register.
     */
    @Suppress("ExecutorRegistration")
    public fun addOnBoundsChangedListener(listener: OnBoundsChangedListener)

    /**
     * Removes a listener to be called when the bounds of the primary Activity change. If the given
     * listener was not added, this call does nothing.
     *
     * @param listener The listener to unregister.
     */
    @Suppress("ExecutorRegistration")
    public fun removeOnBoundsChangedListener(listener: OnBoundsChangedListener)

    /** Interface for a listener which receives changes to the bounds of the primary Activity. */
    public fun interface OnBoundsChangedListener {
        // Is called by the system when the bounds of the primary Activity change
        /**
         * Called by the system when the bounds of the primary Activity change.
         *
         * @param bounds The new bounds of the primary Activity in Meters
         */
        public fun onBoundsChanged(bounds: Dimensions)
    }
}
