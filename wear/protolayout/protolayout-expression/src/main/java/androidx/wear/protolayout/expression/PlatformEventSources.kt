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

package androidx.wear.protolayout.expression

import androidx.wear.protolayout.expression.DynamicBuilders.DynamicBool

/** Dynamic types for platform events */
public object PlatformEventSources {

    /** A [DynamicBool] which receives the current visibility status from platform. */
    @RequiresSchemaVersion(major = 1, minor = 500)
    private val isLayoutVisible: DynamicBool = DynamicBool.from(Keys.LAYOUT_VISIBILITY)

    /**
     * A [DynamicBool] which receives the current state of any pending updates for the current
     * layout.
     */
    @ProtoLayoutExperimental
    @RequiresSchemaVersion(major = 1, minor = 600)
    private val isLayoutUpdatePending: DynamicBool = DynamicBool.from(Keys.LAYOUT_UPDATE_PENDING)

    /**
     * Returns a [DynamicBool] which receives the current visibility status from platform.
     *
     * The visibility status value is `true` when layout is visible, and `false` when invisible.
     */
    @JvmStatic
    @RequiresSchemaVersion(major = 1, minor = 500)
    public fun isLayoutVisible(): DynamicBool = isLayoutVisible

    /**
     * Returns a [DynamicBool] which receives the current state of any pending updates for the
     * current layout.
     *
     * The layout update pending status is `true` from when a new layout is requested until it is
     * received and inflated or the request fails. In all other cases, it is false.
     */
    @JvmStatic
    @ProtoLayoutExperimental
    @RequiresSchemaVersion(major = 1, minor = 600)
    public fun isLayoutUpdatePending(): DynamicBool = isLayoutUpdatePending

    /** Data sources keys for platform event. */
    public object Keys {
        /**
         * The data source key for visibility status from platform sources. The visibility status
         * value is `true` when layout is visible, and `false` when invisible.
         */
        @JvmField
        public val LAYOUT_VISIBILITY: PlatformDataKey<DynamicBool> =
            PlatformDataKey<DynamicBool>("VisibilityStatus")

        /**
         * The data source key for layout update pending status from platform sources. The layout
         * update pending status is `true` from when a new layout is requested until it is received
         * and inflated or the request fails. In all other cases, it is false.
         */
        @ProtoLayoutExperimental
        @JvmField
        public val LAYOUT_UPDATE_PENDING: PlatformDataKey<DynamicBool> =
            PlatformDataKey<DynamicBool>("LayoutUpdatePending")
    }
}
