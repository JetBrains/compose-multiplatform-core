/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.viewfinder.view

import android.graphics.Rect
import android.util.LayoutDirection
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.camera.viewfinder.core.ScaleType
import androidx.camera.viewfinder.core.TransformationInfo
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.internal.DoNotInstrument

// Size of the PreviewView. Aspect ratio 2:1.
private val PREVIEW_VIEW_SIZE = Size(400, 200)
private val PIVOTED_PREVIEW_VIEW_SIZE = Size(PREVIEW_VIEW_SIZE.height, PREVIEW_VIEW_SIZE.width)

// Size of the Surface. Aspect ratio 3:2.
private val SURFACE_SIZE = Size(60, 40)

// 2:1 crop rect.
private val CROP_RECT = Rect(20, 0, 40, 40)

// Off-center crop rect with 0 rotation.
private val CROP_RECT_0 = Rect(0, 15, 20, 25)

// Off-center crop rect with 90 rotation.
private val CROP_RECT_90 = Rect(10, 0, 50, 20)

// 1:1 crop rect.
private val FIT_SURFACE_SIZE = Size(60, 60)
private val MISMATCHED_CROP_RECT = Rect(0, 0, 60, 60)
private const val FLOAT_ERROR = 1e-3f

private const val FRONT_CAMERA = true
private const val BACK_CAMERA = false

private const val ARBITRARY_ROTATION = Surface.ROTATION_0

/** Unit tests for [androidx.camera.viewfinder.core.impl.Transformations]. */
@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
class ViewfinderTransformationsTest {

    private lateinit var viewfinderTransform: ViewfinderTransformation
    private lateinit var view: View

    @Before
    fun setUp() {
        viewfinderTransform = ViewfinderTransformation()
        view = View(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun ratioMatch_surfaceIsScaledToFillPreviewView() {
        // Arrange.

        viewfinderTransform.transformationInfo =
            TransformationInfo(
                sourceRotation = 90,
                isSourceMirroredHorizontally = false,
                isSourceMirroredVertically = false,
                cropRectLeft = CROP_RECT.left.toFloat(),
                cropRectTop = CROP_RECT.top.toFloat(),
                cropRectRight = CROP_RECT.right.toFloat(),
                cropRectBottom = CROP_RECT.bottom.toFloat()
            )

        viewfinderTransform.setResolution(SURFACE_SIZE)

        // Act.
        viewfinderTransform.transformView(
            PREVIEW_VIEW_SIZE,
            LayoutDirection.LTR,
            view,
            ARBITRARY_ROTATION
        )

        // Assert.
        val correctCropRectWidth =
            CROP_RECT.height().toFloat() / SURFACE_SIZE.height * SURFACE_SIZE.width
        assertThat(view.scaleX)
            .isWithin(FLOAT_ERROR)
            .of(PREVIEW_VIEW_SIZE.width / correctCropRectWidth)
        val correctCropRectHeight: Float =
            CROP_RECT.width().toFloat() / SURFACE_SIZE.width * SURFACE_SIZE.height
        assertThat(view.scaleY)
            .isWithin(FLOAT_ERROR)
            .of(PREVIEW_VIEW_SIZE.height / correctCropRectHeight)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(0f)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(-200f)
    }

    @Test
    fun mismatchedCropRect_fitStart() {
        assertForMismatchedCropRect(
            ScaleType.FIT_START,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            0f,
            0f,
            BACK_CAMERA
        )
    }

    @Test
    fun mismatchedCropRect_fitCenter() {
        assertForMismatchedCropRect(
            ScaleType.FIT_CENTER,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            100f,
            0f,
            BACK_CAMERA
        )
    }

    @Test
    fun mismatchedCropRect_fitEnd() {
        assertForMismatchedCropRect(
            ScaleType.FIT_END,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            200f,
            0f,
            BACK_CAMERA
        )
    }

    @Test
    fun mismatchedCropRectFrontCamera_fitStart() {
        assertForMismatchedCropRect(
            ScaleType.FIT_START,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            0f,
            0f,
            FRONT_CAMERA
        )
    }

    @Test
    fun mismatchedCropRect_fillStart() {
        assertForMismatchedCropRect(
            ScaleType.FILL_START,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.width.toFloat() / MISMATCHED_CROP_RECT.width(),
            0f,
            0f,
            BACK_CAMERA
        )
    }

    @Test
    fun mismatchedCropRect_fillCenter() {
        assertForMismatchedCropRect(
            ScaleType.FILL_CENTER,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.width.toFloat() / MISMATCHED_CROP_RECT.width(),
            0f,
            -100f,
            BACK_CAMERA
        )
    }

    @Test
    fun mismatchedCropRect_fillEnd() {
        assertForMismatchedCropRect(
            ScaleType.FILL_END,
            LayoutDirection.LTR,
            PREVIEW_VIEW_SIZE.width.toFloat() / MISMATCHED_CROP_RECT.width(),
            0f,
            -200f,
            BACK_CAMERA
        )
    }

    @Test
    fun mismatchedCropRect_fitStartWithRtl_actsLikeFitEnd() {
        assertForMismatchedCropRect(
            ScaleType.FIT_START,
            LayoutDirection.RTL,
            PREVIEW_VIEW_SIZE.height.toFloat() / MISMATCHED_CROP_RECT.height(),
            200f,
            0f,
            BACK_CAMERA
        )
    }

    private fun assertForMismatchedCropRect(
        scaleType: ScaleType,
        layoutDirection: Int,
        scale: Float,
        translationX: Float,
        translationY: Float,
        isFrontCamera: Boolean
    ) {
        // Arrange.
        viewfinderTransform.transformationInfo =
            TransformationInfo(
                sourceRotation = 90,
                isSourceMirroredHorizontally = false,
                // 90 degree rotation would have a vertical flip for a front camera
                isSourceMirroredVertically = isFrontCamera,
                cropRectLeft = MISMATCHED_CROP_RECT.left.toFloat(),
                cropRectTop = MISMATCHED_CROP_RECT.top.toFloat(),
                cropRectRight = MISMATCHED_CROP_RECT.right.toFloat(),
                cropRectBottom = MISMATCHED_CROP_RECT.bottom.toFloat()
            )

        viewfinderTransform.setResolution(FIT_SURFACE_SIZE)

        viewfinderTransform.scaleType = scaleType

        // Act.
        viewfinderTransform.transformView(
            PREVIEW_VIEW_SIZE,
            layoutDirection,
            view,
            ARBITRARY_ROTATION
        )

        // Assert.
        assertThat(view.scaleX).isWithin(FLOAT_ERROR).of(scale)
        assertThat(view.scaleY).isWithin(FLOAT_ERROR).of(scale)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(translationX)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(translationY)
    }

    @Test
    fun frontCamera0_transformationIsMirrored() {
        testOffCenterCropRectMirroring(FRONT_CAMERA, CROP_RECT_0, PREVIEW_VIEW_SIZE, 0)

        // Assert:
        assertThat(view.scaleX).isWithin(FLOAT_ERROR).of(20F)
        assertThat(view.scaleY).isWithin(FLOAT_ERROR).of(20F)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(-800F)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(-300F)
    }

    @Test
    fun backCamera0_transformationIsNotMirrored() {
        testOffCenterCropRectMirroring(BACK_CAMERA, CROP_RECT_0, PREVIEW_VIEW_SIZE, 0)

        // Assert:
        assertThat(view.scaleX).isWithin(FLOAT_ERROR).of(20F)
        assertThat(view.scaleY).isWithin(FLOAT_ERROR).of(20F)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(0F)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(-300F)
    }

    @Test
    fun frontCameraRotated90_transformationIsMirrored() {
        testOffCenterCropRectMirroring(FRONT_CAMERA, CROP_RECT_90, PIVOTED_PREVIEW_VIEW_SIZE, 90)

        // Assert:
        assertThat(view.scaleX).isWithin(FLOAT_ERROR).of(6.666F)
        assertThat(view.scaleY).isWithin(FLOAT_ERROR).of(15F)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(0F)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(-100F)
    }

    @Test
    fun previewViewSizeIs0_noOps() {
        testOffCenterCropRectMirroring(FRONT_CAMERA, CROP_RECT_90, Size(0, 0), 90)

        // Assert: no transform applied.
        assertThat(view.scaleX).isWithin(FLOAT_ERROR).of(1F)
        assertThat(view.scaleY).isWithin(FLOAT_ERROR).of(1F)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(0F)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(0F)
    }

    @Test
    fun backCameraRotated90_transformationIsNotMirrored() {
        testOffCenterCropRectMirroring(BACK_CAMERA, CROP_RECT_90, PIVOTED_PREVIEW_VIEW_SIZE, 90)

        // Assert:
        assertThat(view.scaleX).isWithin(FLOAT_ERROR).of(6.666F)
        assertThat(view.scaleY).isWithin(FLOAT_ERROR).of(15F)
        assertThat(view.translationX).isWithin(FLOAT_ERROR).of(-200F)
        assertThat(view.translationY).isWithin(FLOAT_ERROR).of(-100F)
    }

    private fun testOffCenterCropRectMirroring(
        isFrontCamera: Boolean,
        cropRect: Rect,
        previewViewSize: Size,
        rotationDegrees: Int
    ) {
        viewfinderTransform.transformationInfo =
            TransformationInfo(
                sourceRotation = rotationDegrees,
                isSourceMirroredHorizontally = isFrontCamera && rotationDegrees in setOf(0, 180),
                isSourceMirroredVertically = isFrontCamera && rotationDegrees in setOf(90, 270),
                cropRectLeft = cropRect.left.toFloat(),
                cropRectTop = cropRect.top.toFloat(),
                cropRectRight = cropRect.right.toFloat(),
                cropRectBottom = cropRect.bottom.toFloat()
            )
        viewfinderTransform.setResolution(SURFACE_SIZE)
        viewfinderTransform.transformView(
            previewViewSize,
            LayoutDirection.LTR,
            view,
            ARBITRARY_ROTATION
        )
    }
}
