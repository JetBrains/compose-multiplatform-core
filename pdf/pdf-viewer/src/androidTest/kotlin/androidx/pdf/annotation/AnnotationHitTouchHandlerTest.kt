/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.pdf.annotation

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.pdf.annotation.AnnotationsView.PageAnnotationsData
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.StampAnnotation
import androidx.pdf.view.PdfViewTestActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AnnotationHitTouchHandlerTest {
    private lateinit var annotationsView: AnnotationsView
    private lateinit var hitHandler: AnnotationHitTouchHandler
    private val testListener = FakeAnnotationHitListener()

    @Before
    fun setUp() {
        PdfViewTestActivity.onCreateCallback = { activity ->
            // Setup AnnotationsView
            annotationsView =
                AnnotationsView(activity).apply {
                    layoutParams =
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                }
            activity.container.addView(annotationsView)

            // Setup Handler with a Fake Provider mapping 1:1 to view coordinates
            val pageInfoProvider =
                object : PageInfoProvider {
                    override fun getPageInfoFromViewCoordinates(
                        viewX: Float,
                        viewY: Float,
                    ): PageInfoProvider.PageInfo {
                        val pageBounds = RectF(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT)
                        return PageInfoProvider.PageInfo(
                            pageNum = 0,
                            pageBounds = pageBounds,
                            pageToViewTransform = Matrix(),
                            viewToPageTransform = Matrix(),
                        )
                    }
                }

            annotationsView.pageInfoProvider = pageInfoProvider
            hitHandler = AnnotationHitTouchHandler()
            hitHandler.setListener(testListener)
        }
    }

    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun handleTouchEvent_hitDetected_notifiesListener() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotationBounds = RectF(100f, 100f, 200f, 200f)
            val annotation = createStampAnnotation(annotationBounds)

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Simulate Touch inside bounds
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 150f, 150f)
                val consumed = hitHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                assertThat(testListener.lastHitAnnotation).isEqualTo(annotation)
            }
        }
    }

    @Test
    fun handleTouchEvent_outsideBounds_returnsFalse() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotationBounds = RectF(100f, 100f, 200f, 200f)
            val annotation = createStampAnnotation(annotationBounds)

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Touch outside at (300, 300)
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 300f, 300f)
                val consumed = hitHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isFalse()
                assertThat(testListener.lastHitAnnotation).isNull()
            }
        }
    }

    @Test
    fun handleTouchEvent_swipeGesture_hitDetected() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotationBounds = RectF(100f, 100f, 200f, 200f)
            val annotation = createStampAnnotation(annotationBounds)

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Simulate ACTION_MOVE (Swipe) over the annotation
                val event = obtainMotionEvent(MotionEvent.ACTION_MOVE, 150f, 150f)
                val consumed = hitHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                assertThat(testListener.lastHitAnnotation).isEqualTo(annotation)
            }
        }
    }

    @Test
    fun handleTouchEvent_overlappingAnnotations_selectsTopMost() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            // Both annotations share the same bounds
            val bounds = RectF(100f, 100f, 200f, 200f)
            val bounds1 = RectF(125f, 125f, 175f, 175f)
            val bottomAnnotation = createStampAnnotation(bounds)
            val topAnnotation = createStampAnnotation(bounds1)

            scenario.onActivity {
                // The list order represents Z-order: index 0 is bottom, last index is top
                setupAnnotationsOnView(listOf(bottomAnnotation, topAnnotation))

                // Simulate Touch inside the overlapping bounds
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 150f, 150f)
                val consumed = hitHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                // Should return topAnnotation because findAnnotationAtPoint iterates in reverse
                assertThat(testListener.lastHitAnnotation).isEqualTo(topAnnotation)
                assertThat(testListener.lastHitAnnotation).isNotEqualTo(bottomAnnotation)
            }
        }
    }

    // Updated helper to support multiple annotations
    private fun setupAnnotationsOnView(annotations: List<PdfAnnotation>) {
        val data = PageAnnotationsData(annotations, Matrix())
        val sparseArray = SparseArray<PageAnnotationsData>()
        sparseArray.put(0, data)
        annotationsView.annotations = sparseArray
    }

    private fun createStampAnnotation(bounds: RectF): StampAnnotation {
        val width = bounds.width()
        val height = bounds.height()

        // Mock a simple rectangular path slightly inset from bounds
        val pathInputs =
            listOf(
                PathPdfObject.PathInput(bounds.left + width / 4, bounds.top + height / 4),
                PathPdfObject.PathInput(bounds.right - width / 4, bounds.top + height / 4),
                PathPdfObject.PathInput(bounds.right - width / 4, bounds.bottom - height / 4),
                PathPdfObject.PathInput(bounds.left + width / 4, bounds.bottom - height / 4),
                PathPdfObject.PathInput(bounds.left + width / 4, bounds.top + height / 4),
            )

        val pathObject = PathPdfObject(Color.RED, 10f, pathInputs)
        return StampAnnotation(0, bounds, listOf(pathObject))
    }

    private fun obtainMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0)
    }

    class FakeAnnotationHitListener : OnAnnotationHitListener {
        var lastHitAnnotation: PdfAnnotation? = null

        override fun onAnnotationHit(annotation: PdfAnnotation) {
            lastHitAnnotation = annotation
        }
    }

    private companion object {
        const val PAGE_WIDTH = 500f
        const val PAGE_HEIGHT = 500f
    }
}
