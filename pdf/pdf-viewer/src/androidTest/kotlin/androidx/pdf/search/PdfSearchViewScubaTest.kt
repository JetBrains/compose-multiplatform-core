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

package androidx.pdf.search

import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.pdf.SCREENSHOT_GOLDEN_DIRECTORY
import androidx.pdf.SEARCH_VIEW_IN_LTR_MODE
import androidx.pdf.SEARCH_VIEW_IN_RTL_MODE
import androidx.pdf.assertScreenshot
import androidx.pdf.updateContext
import androidx.pdf.view.PdfViewTestActivity
import androidx.pdf.view.search.PdfSearchView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.screenshot.AndroidXScreenshotTestRule
import java.util.Locale
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PdfSearchViewScubaTest {

    @get:Rule val screenshotRule = AndroidXScreenshotTestRule(SCREENSHOT_GOLDEN_DIRECTORY)

    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun testPdfSearchViewInLTRMode() {
        setupPdfSearchView()
        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            assertScreenshot(PDF_SEARCH_VIEW_ID, screenshotRule, SEARCH_VIEW_IN_LTR_MODE)

            close()
        }
    }

    @Test
    fun testPdfSearchViewInRTLMode() {
        PdfViewTestActivity.onAttachCallback = { activity ->
            // set locale that supports RTL mode
            val rtlLocale = Locale("ar", "SA") // Arabic is an RTL language
            updateContext(activity, rtlLocale)
        }
        setupPdfSearchView()

        with(ActivityScenario.launch(PdfViewTestActivity::class.java)) {
            assertScreenshot(PDF_SEARCH_VIEW_ID, screenshotRule, SEARCH_VIEW_IN_RTL_MODE)

            close()
        }
    }

    private fun setupPdfSearchView() {
        PdfViewTestActivity.onCreateCallback = { activity ->
            val container = FrameLayout(activity)
            container.addView(
                PdfSearchView(activity).apply { id = PDF_SEARCH_VIEW_ID },
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
            )
            activity.setContentView(container)
        }
    }

    companion object {
        /** Arbitrary fixed ID for PdfSearchView */
        private const val PDF_SEARCH_VIEW_ID = 987654321
    }
}
