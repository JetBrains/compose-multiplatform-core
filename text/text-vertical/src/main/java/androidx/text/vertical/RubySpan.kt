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

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.style.ReplacementSpan

/**
 * A span used to specify ruby text for a portion of the text.
 *
 * The text covered by this span is known as the "base text", and the ruby text is stored in [text].
 *
 * Ruby text cannot be nested (i.e., ruby text cannot contain further ruby text). Ruby spans also
 * cannot overlap each other.
 *
 * More information on [ruby characters](https://en.wikipedia.org/wiki/Ruby_character) and
 * [span styling](https://developer.android.com/guide/topics/text/spans).
 *
 * This span is designed for use with [VerticalTextLayout], `media3-ui` library, and a bit more
 * general use cases.
 *
 * @property text The ruby text to be displayed adjacent to the base text.
 * @property position The position of ruby text relative to the base text. Defaults to
 *   [AnnotationPosition.BEFORE].
 * @property orientation The text orientation of the ruby text. Defaults to [TextOrientation.MIXED].
 * @property textScale The text scale ratio of the ruby text relative to the base text. Defaults to
 *   0.5f.
 */
@Suppress("BanParcelableUsage")
public class RubySpan
@JvmOverloads
constructor(
    @JvmField public val text: CharSequence,
    @JvmField public val position: AnnotationPosition = DEFAULT_POSITION,
    @param:OrientationMode public val orientation: Int = DEFAULT_ORIENTATION,
    public val textScale: Float = 0.5f,
) : ReplacementSpan(), Parcelable {

    private val impl by lazy {
        HorizontalSpanImpl(
            { paint, text, start, end -> LayoutKey(start, end, text) },
            { paint, bodyText, start, end ->
                HorizontalRubySpanLayout(bodyText, start, end, text, paint, textScale)
            },
        )
    }

    // TODO(b/493693386): remove Builder
    /**
     * Builder class for creating [RubySpan] instances.
     *
     * @param text The ruby text to be displayed adjacent to the base text.
     */
    public class Builder(private val text: CharSequence) {
        private var _orientation: Int = DEFAULT_ORIENTATION
        private var _textScale: Float = DEFAULT_TEXT_SCALE

        /**
         * Sets the text orientation for the ruby text.
         *
         * By default, [TextOrientation.MIXED] is used.
         *
         * @param orientation The text orientation to set.
         * @return This [Builder] instance for method chaining.
         */
        public fun setOrientation(@OrientationMode orientation: Int): Builder = apply {
            _orientation = orientation
        }

        /**
         * Sets the text scale for the ruby text.
         *
         * By default, 0.5f is used, meaning the ruby text will be half the size of the base text.
         *
         * @param textScale The text scale to set.
         * @return This [Builder] instance for method chaining.
         */
        public fun setTextScale(textScale: Float): Builder = apply { _textScale = textScale }

        /**
         * Builds and returns a new [RubySpan] instance.
         *
         * @return A new [RubySpan] instance.
         */
        public fun build(): RubySpan = RubySpan(text, DEFAULT_POSITION, _orientation, _textScale)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int = impl.getSize(paint, text, start, end, fm)

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        impl.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        // Delegate to Bundle for robust forward compatibility.
        // By wrapping the data in a Bundle, we ensure that if we add new fields in future
        // versions of this library, older clients can still read the Parcel without crashing,
        // as Bundle automatically ignores unknown keys.
        val bundle =
            Bundle().apply {
                putCharSequence(FIELD_TEXT, text)
                putInt(FIELD_POSITION, position.value)
                putInt(FIELD_ORIENTATION, orientation)
                putFloat(FIELD_TEXT_SCALE, textScale)
            }
        parcel.writeBundle(bundle)
    }

    override fun describeContents(): Int = 0

    public companion object {
        // Keys for Bundle delegation
        private const val FIELD_TEXT: String = "text"
        private const val FIELD_POSITION: String = "position"
        private const val FIELD_ORIENTATION: String = "orientation"
        private const val FIELD_TEXT_SCALE: String = "textScale"

        private val DEFAULT_POSITION: AnnotationPosition = AnnotationPosition.BEFORE
        private const val DEFAULT_ORIENTATION: Int = TextOrientation.MIXED
        private const val DEFAULT_TEXT_SCALE: Float = 0.5f

        @JvmField
        public val CREATOR: Parcelable.Creator<RubySpan> =
            object : Parcelable.Creator<RubySpan> {
                override fun createFromParcel(source: Parcel): RubySpan {
                    // Read the data as a Bundle to safely handle version skew.
                    // If the source is an older library version, some keys might be missing;
                    // we default them to safe values (empty strings/lists) to prevent runtime
                    // crashes.
                    val bundle = source.readBundle(RubySpan::class.java.classLoader)

                    // Explicitly set the ClassLoader to ensure the inner Parcelable ArrayList
                    // (updates) can be unparceled correctly.
                    bundle?.classLoader = RubySpan::class.java.classLoader

                    return RubySpan(
                        text = bundle?.getCharSequence(FIELD_TEXT) ?: "",
                        position =
                            AnnotationPosition.fromInt(
                                bundle?.getInt(FIELD_POSITION) ?: DEFAULT_POSITION.value
                            ),
                        orientation = bundle?.getInt(FIELD_ORIENTATION) ?: DEFAULT_ORIENTATION,
                        textScale = bundle?.getFloat(FIELD_TEXT_SCALE) ?: DEFAULT_TEXT_SCALE,
                    )
                }

                override fun newArray(size: Int): Array<RubySpan?> = arrayOfNulls(size)
            }
    }
}
