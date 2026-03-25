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

package androidx.text.vertical

/**
 * Properties of a text annotation (i.e. ruby and emphasis marks).
 *
 * @property value The integer value
 */
public enum class AnnotationPosition private constructor(@JvmField public val value: Int) {
    /** The text annotation position is unknown. */
    UNKNOWN(-1),
    /**
     * For horizontal text, the text annotation should be positioned above the base text.
     *
     * For vertical text, it should be positioned to the right. See the
     * [tts:rubyPosition](https://www.w3.org/TR/ttml2/#style-attribute-rubyPosition) attribute in
     * TTML2 for more information.
     */
    BEFORE(0),
    /**
     * For horizontal text, the text annotation should be positioned below the base text.
     *
     * For vertical text, it should be positioned to the left, See the
     * [tts:rubyPosition](https://www.w3.org/TR/ttml2/#style-attribute-rubyPosition) attribute in
     * TTML2 for more information.
     */
    AFTER(1);

    public companion object {
        @JvmStatic
        public fun fromInt(value: Int): AnnotationPosition =
            when (value) {
                BEFORE.value -> BEFORE
                AFTER.value -> AFTER
                else -> UNKNOWN
            }
    }
}
