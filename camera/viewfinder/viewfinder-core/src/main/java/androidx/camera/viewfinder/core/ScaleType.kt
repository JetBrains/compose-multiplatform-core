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
package androidx.camera.viewfinder.core

import androidx.annotation.RestrictTo

/** Options for scaling the input frames vis-à-vis its container viewfinder. */
enum class ScaleType(@get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) val id: Int) {
    /**
     * Scale the input frames, maintaining the source aspect ratio, so it fills the entire
     * viewfinder, and align it to the start of the viewfinder, which is the top left corner in a
     * left-to-right (LTR) layout, or the top right corner in a right-to-left (RTL) layout.
     *
     * This may cause the input frames to be cropped if the input frame aspect ratio does not match
     * that of its container viewfinder.
     */
    FILL_START(0),

    /**
     * Scale the input frames, maintaining the source aspect ratio, so it fills the entire
     * viewfinder, and center it in the viewfinder.
     *
     * This may cause the input frames to be cropped if the input frame aspect ratio does not match
     * that of its container viewfinder.
     */
    FILL_CENTER(1),

    /**
     * Scale the input frames, maintaining the source aspect ratio, so it fills the entire
     * viewfinder, and align it to the end of the viewfinder, which is the bottom right corner in a
     * left-to-right (LTR) layout, or the bottom left corner in a right-to-left (RTL) layout.
     *
     * This may cause the input frames to be cropped if the input frame aspect ratio does not match
     * that of its container viewfinder.
     */
    FILL_END(2),

    /**
     * Scale the input frames, maintaining the source aspect ratio, so it is entirely contained
     * within the viewfinder, and align it to the start of the viewfinder, which is the top left
     * corner in a left-to-right (LTR) layout, or the top right corner in a right-to-left (RTL)
     * layout. The background area not covered by the input frames will be black or the background
     * color of the viewfinder.
     *
     * Both dimensions of the input frames will be equal or less than the corresponding dimensions
     * of its container viewfinder.
     */
    FIT_START(3),

    /**
     * Scale the input frames, maintaining the source aspect ratio, so it is entirely contained
     * within the viewfinder, and center it inside the viewfinder. The background area not covered
     * by the input frames will be black or the background color of the viewfinder.
     *
     * Both dimensions of the input frames will be equal or less than the corresponding dimensions
     * of its container viewfinder.
     */
    FIT_CENTER(4),

    /**
     * Scale the input frames, maintaining the source aspect ratio, so it is entirely contained
     * within the viewfinder, and align it to the end of the viewfinder, which is the bottom right
     * corner in a left-to-right (LTR) layout, or the bottom left corner in a right-to-left (RTL)
     * layout. The background area not covered by the input frames will be black or the background
     * color of the viewfinder.
     *
     * Both dimensions of the input frames will be equal or less than the corresponding dimensions
     * of its container viewfinder.
     */
    FIT_END(5);

    companion object {
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        fun fromId(id: Int): ScaleType {
            for (scaleType in values()) {
                if (scaleType.id == id) {
                    return scaleType
                }
            }
            throw IllegalArgumentException("Unknown scale type id $id")
        }
    }
}
