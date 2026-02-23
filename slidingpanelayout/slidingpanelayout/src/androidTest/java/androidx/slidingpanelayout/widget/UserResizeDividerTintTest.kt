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

package androidx.slidingpanelayout.widget

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class UserResizeDividerTintTest {
    @Test
    fun userResizingDividerTint_tintIsSetToDrawable() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val view = SlidingPaneLayout(context)
        val drawable = TestDrawable()

        view.setUserResizingDividerDrawable(drawable)
        val tint = ColorStateList.valueOf(Color.RED)
        view.setUserResizingDividerTint(tint)

        assertWithMessage("userResizingDividerTint is set to drawable")
            .that(drawable.tint)
            .isEqualTo(tint)
    }

    @Test
    fun userResizingDividerTint_setDrawableAfterTint_tintIsNotSetToDrawable() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val view = SlidingPaneLayout(context)
        val drawable = TestDrawable()

        val tint = ColorStateList.valueOf(Color.RED)
        view.setUserResizingDividerTint(tint)

        view.setUserResizingDividerDrawable(drawable)
        assertWithMessage("userResizingDividerTint is not set to drawable")
            .that(drawable.tint)
            .isNull()
    }

    @Test
    fun userResizingDividerTint_setTintToNull() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val view = SlidingPaneLayout(context)
        val drawable = TestDrawable().apply { setTintList(ColorStateList.valueOf(Color.RED)) }
        view.setUserResizingDividerDrawable(drawable)

        view.setUserResizingDividerTint(null)
        assertWithMessage("userResizingDividerTint is set to null").that(drawable.tint).isNull()
    }
}

private class TestDrawable : Drawable() {
    var tint: ColorStateList? = null
        private set

    override fun draw(canvas: Canvas) {}

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
    }
}
