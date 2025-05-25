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

package androidx.window.embedding

import androidx.window.RequiresWindowSdkExtension

/**
 * Parameters to be used for window transition animations for embedding activities.
 *
 * @property animationBackground the animation background to use during the animation of the split
 *   involving this `EmbeddingAnimationParams` object if the animation requires a background. The
 *   default is to use the current theme window background color.
 * @see Builder
 * @see SplitAttributes.animationParams
 * @see EmbeddingAnimationBackground
 * @see EmbeddingAnimationBackground.createColorBackground
 * @see EmbeddingAnimationBackground.DEFAULT
 */
class EmbeddingAnimationParams
private constructor(
    val animationBackground: EmbeddingAnimationBackground = EmbeddingAnimationBackground.DEFAULT,
) {
    /**
     * Returns a hash code for this `EmbeddingAnimationParams` object.
     *
     * @return the hash code for this object.
     */
    override fun hashCode(): Int {
        var result = animationBackground.hashCode()
        return result
    }

    /**
     * Determines whether this object has the same animation parameters as the compared object.
     *
     * @param other the object to compare to this object.
     * @return true if the objects have the same animation parameters, false otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingAnimationParams) return false
        return animationBackground == other.animationBackground
    }

    /**
     * A string representation of this `EmbeddingAnimationParams` object.
     *
     * @return the string representation of the object.
     */
    override fun toString(): String =
        "${EmbeddingAnimationParams::class.java.simpleName}:" +
            "{animationBackground=$animationBackground }"

    /** Builder for creating an instance of [EmbeddingAnimationParams]. */
    class Builder {
        private var animationBackground = EmbeddingAnimationBackground.DEFAULT

        /**
         * Sets the animation background.
         *
         * The default is to use the current theme window background color.
         *
         * This can be supported only if the Window Extensions version of the target device is
         * equals or higher than required API level. Otherwise, it would be no-op.
         *
         * @param background the animation background.
         * @return this `Builder`.
         * @see EmbeddingAnimationBackground
         */
        @RequiresWindowSdkExtension(5)
        fun setAnimationBackground(background: EmbeddingAnimationBackground): Builder = apply {
            this.animationBackground = background
        }

        /**
         * Builds an `EmbeddingAnimationParams` instance with the attributes specified by the
         * builder's setters.
         *
         * @return the new `EmbeddingAnimationParams` instance.
         */
        fun build(): EmbeddingAnimationParams = EmbeddingAnimationParams(animationBackground)
    }
}
