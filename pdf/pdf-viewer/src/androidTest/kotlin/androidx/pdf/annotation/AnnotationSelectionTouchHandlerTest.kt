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
import java.util.UUID
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class AnnotationSelectionTouchHandlerTest {
    private lateinit var annotationsView: AnnotationsView
    private lateinit var selectionTouchHandler: AnnotationSelectionTouchHandler
    private val testListener = FakeAnnotationSelectedListener()

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
                        return PageInfoProvider.PageInfo(
                            pageNum = 0,
                            pageBounds = RectF(0f, 0f, PAGE_WIDTH, PAGE_HEIGHT),
                            pageToViewTransform = Matrix(),
                            viewToPageTransform = Matrix(),
                        )
                    }
                }

            annotationsView.pageInfoProvider = pageInfoProvider
            selectionTouchHandler = AnnotationSelectionTouchHandler()
            selectionTouchHandler.setListener(testListener)
        }
    }

    @After
    fun tearDown() {
        PdfViewTestActivity.onCreateCallback = {}
    }

    @Test
    fun handleTouchEvent_hitDetected_notifiesListener() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotation = createStampAnnotation()

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Touch at (150, 150) is inside the first path (125, 125 to 175, 175)
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 150f, 150f)
                val consumed = selectionTouchHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                assertThat(testListener.lastSelectedAnnotation).isEqualTo(annotation)
            }
        }
    }

    @Test
    fun handleTouchEvent_outsideBounds_returnsFalse() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotation = createStampAnnotation()

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Touch at (300, 300) is outside the 100-200 bounds
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 300f, 300f)
                val consumed = selectionTouchHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isFalse()
                assertThat(testListener.lastSelectedAnnotation).isNull()
            }
        }
    }

    @Test
    fun handleTouchEvent_swipeGesture_hitDetected() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotation = createStampAnnotation()

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Simulate ACTION_MOVE (Swipe) over the annotation
                val event = obtainMotionEvent(MotionEvent.ACTION_MOVE, 150f, 150f)
                val consumed = selectionTouchHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                assertThat(testListener.lastSelectedAnnotation).isEqualTo(annotation)
            }
        }
    }

    @Test
    fun handleTouchEvent_overlappingAnnotations_selectsTopMost() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val bottomAnnotation = createStampAnnotation()
            val topAnnotation = createStampAnnotation(10f)

            scenario.onActivity {
                // The list order represents Z-order: index 0 is bottom, last index is top
                setupAnnotationsOnView(listOf(bottomAnnotation, topAnnotation))

                // Simulate Touch inside the overlapping bounds
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 150f, 150f)
                val consumed = selectionTouchHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                // Should return topAnnotation because findAnnotationAtPoint iterates in reverse
                assertThat(testListener.lastSelectedAnnotation).isEqualTo(topAnnotation)
                assertThat(testListener.lastSelectedAnnotation).isNotEqualTo(bottomAnnotation)
            }
        }
    }

    @Test
    fun handleTouchEvent_hitDetectedOnSecondPath_notifiesListener() {
        ActivityScenario.launch(PdfViewTestActivity::class.java).use { scenario ->
            val annotation = createStampAnnotation()

            scenario.onActivity {
                setupAnnotationsOnView(listOf(annotation))

                // Touch at (150, 190) is inside the second path (125, 180 to 175, 195)
                val event = obtainMotionEvent(MotionEvent.ACTION_DOWN, 150f, 190f)
                val consumed = selectionTouchHandler.handleTouch(annotationsView, event)

                assertThat(consumed).isTrue()
                assertThat(testListener.lastSelectedAnnotation).isEqualTo(annotation)
            }
        }
    }

    private fun setupAnnotationsOnView(annotations: List<PdfAnnotation>) {
        val keyedAnnotations =
            annotations.map { KeyedPdfAnnotation(key = UUID.randomUUID().toString(), it) }
        val data = PageAnnotationsData(keyedAnnotations, Matrix())
        val sparseArray = SparseArray<PageAnnotationsData>()
        sparseArray.put(0, data)
        annotationsView.annotations = sparseArray
    }

    /**
     * Creates a StampAnnotation with hard-coded bounds and paths for predictable testing. Overall
     * Bounds: (100, 100, 200, 200) Path 1 (Square): (125, 125) to (175, 175) Path 2 (Rectangle):
     * (125, 180) to (175, 195)
     */
    private fun createStampAnnotation(offset: Float = 0f): StampAnnotation {
        val bounds = RectF(100f + offset, 100f + offset, 200f + offset, 200f + offset)

        val path1 =
            PathPdfObject(
                Color.RED,
                0f,
                listOf(
                    PathPdfObject.PathInput(125f, 125f),
                    PathPdfObject.PathInput(175f, 125f),
                    PathPdfObject.PathInput(175f, 175f),
                    PathPdfObject.PathInput(125f, 175f),
                ),
            )

        val path2 =
            PathPdfObject(
                Color.BLUE,
                0f,
                listOf(
                    PathPdfObject.PathInput(125f, 180f),
                    PathPdfObject.PathInput(175f, 180f),
                    PathPdfObject.PathInput(175f, 195f),
                    PathPdfObject.PathInput(125f, 195f),
                ),
            )

        return StampAnnotation(0, bounds, listOf(path1, path2))
    }

    private fun obtainMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0)
    }

    class FakeAnnotationSelectedListener : OnAnnotationSelectedListener {
        var lastSelectedAnnotation: PdfAnnotation? = null

        override fun onAnnotationSelected(keyedPdfAnnotation: KeyedPdfAnnotation) {
            lastSelectedAnnotation = keyedPdfAnnotation.annotation
        }
    }

    private companion object {
        const val PAGE_WIDTH = 500f
        const val PAGE_HEIGHT = 500f
    }
}
