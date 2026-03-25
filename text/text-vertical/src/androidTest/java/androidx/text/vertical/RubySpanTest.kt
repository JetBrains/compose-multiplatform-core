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

import android.graphics.Typeface
import android.os.Parcel
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RubySpanTest {
    private val RUBY_TEXT = "ABCDE"

    @Test
    fun rubySpan_Builder_BuildAndGetDefault() {
        RubySpan.Builder(RUBY_TEXT).build().run {
            assertThat(text).isEqualTo(RUBY_TEXT)
            assertThat(orientation).isEqualTo(TextOrientation.MIXED)
            assertThat(textScale).isEqualTo(0.5f)
        }
    }

    @Test
    fun rubySpan_Builder_BuildAndGetCustomize() {
        RubySpan.Builder(RUBY_TEXT)
            .setOrientation(TextOrientation.UPRIGHT)
            .setTextScale(0.3f)
            .build()
            .run {
                assertThat(text).isEqualTo(RUBY_TEXT)
                assertThat(orientation).isEqualTo(TextOrientation.UPRIGHT)
                assertThat(textScale).isEqualTo(0.3f)
            }
    }

    @Test
    fun rubySpan_Constructor_Position() {
        RubySpan(RUBY_TEXT, AnnotationPosition.AFTER).run {
            assertThat(text).isEqualTo(RUBY_TEXT)
            assertThat(position).isEqualTo(AnnotationPosition.AFTER)
        }

        RubySpan(RUBY_TEXT, AnnotationPosition.AFTER).run {
            assertThat(text).isEqualTo(RUBY_TEXT)
            assertThat(position).isEqualTo(AnnotationPosition.AFTER)
        }
    }

    @Test
    fun rubySpan_Parcelable() {
        val original =
            RubySpan(
                text = RUBY_TEXT,
                position = AnnotationPosition.AFTER,
                orientation = TextOrientation.UPRIGHT,
                textScale = 0.3f,
            )

        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = RubySpan.CREATOR.createFromParcel(parcel)

        assertThat(restored.text).isEqualTo(original.text)
        assertThat(restored.position).isEqualTo(original.position)
        assertThat(restored.orientation).isEqualTo(original.orientation)
        assertThat(restored.textScale).isEqualTo(original.textScale)

        parcel.recycle()
    }

    @Test
    fun rubySpan_Parcelable_preservesSpans() {
        // 1. Set up a SpannableString with a simple bold span on the first two characters
        val spannedText = SpannableString("ABCDE")
        spannedText.setSpan(StyleSpan(Typeface.BOLD), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        val original =
            RubySpan(
                text = spannedText,
                position = AnnotationPosition.AFTER,
                orientation = TextOrientation.UPRIGHT,
                textScale = 0.3f,
            )

        // 2. Parcel and unparcel
        val parcel = Parcel.obtain()
        original.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val restored = RubySpan.CREATOR.createFromParcel(parcel)

        // 3. Verify the text matches perfectly, including spans
        // TextUtils.equals checks both the character data and the attached spans
        assertThat(TextUtils.equals(original.text, restored.text)).isTrue()

        // 4. Explicitly verify the span survived the trip
        val restoredSpanned = restored.text as Spanned
        val spans = restoredSpanned.getSpans(0, restoredSpanned.length, StyleSpan::class.java)

        assertThat(spans).hasLength(1)
        assertThat(spans[0].style).isEqualTo(Typeface.BOLD)
        assertThat(restoredSpanned.getSpanStart(spans[0])).isEqualTo(0)
        assertThat(restoredSpanned.getSpanEnd(spans[0])).isEqualTo(2)

        parcel.recycle()
    }
}
