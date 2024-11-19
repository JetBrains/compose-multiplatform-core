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

package androidx.pdf.view

import android.graphics.Point
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfViewGestureTest {
    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun testScrollGesture() {
        val fakePdfDocument = FakePdfDocument(List(100) { Point(500, 1000) })
        PdfViewTestActivity.onCreateCallback = { activity ->
            val container = FrameLayout(activity)
            container.addView(
                PdfView(activity).apply {
                    pdfDocument = fakePdfDocument
                    id = PDF_VIEW_ID
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            activity.setContentView(container)
        }

        var scrollBefore = Int.MAX_VALUE
        var scrollAfter = Int.MIN_VALUE
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            Espresso.onView(withId(PDF_VIEW_ID))
                .check { view, noViewFoundException ->
                    view ?: throw noViewFoundException
                    scrollBefore = view.scrollY
                }
                .perform(ViewActions.swipeUp())
                .check { view, noViewFoundException ->
                    view ?: throw noViewFoundException
                    scrollAfter = view.scrollY
                }
            close()
        }

        assertThat(scrollAfter).isGreaterThan(scrollBefore)
    }

    @Test fun testZoomGesture() {}
}

/** Arbitrary fixed ID for PdfView */
private const val PDF_VIEW_ID = 123456789
