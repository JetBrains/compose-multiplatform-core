/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.xr.runtime.interfaces

import androidx.annotation.RestrictTo

/** A device capability that determines how virtual content is added to the real world. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class DisplayBlendMode private constructor(private val value: Int) {
    public companion object {
        /** Blending is not supported. */
        public val NO_DISPLAY: DisplayBlendMode = DisplayBlendMode(0)
        /**
         * Virtual content is added to the real world by adding the pixel values for each of Red,
         * Green, and Blue components. Alpha is ignored. Black pixels will appear transparent.
         */
        public val ADDITIVE: DisplayBlendMode = DisplayBlendMode(1)
        /**
         * Virtual content is added to the real world by alpha blending the pixel values based on
         * the Alpha component.
         */
        public val ALPHA_BLEND: DisplayBlendMode = DisplayBlendMode(2)
    }
}
