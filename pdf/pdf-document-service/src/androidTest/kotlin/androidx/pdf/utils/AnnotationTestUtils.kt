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

package androidx.pdf.utils

import android.graphics.RectF
import androidx.annotation.RestrictTo
import androidx.pdf.annotation.models.PathPdfObject
import androidx.pdf.annotation.models.StampAnnotation
import junit.framework.TestCase.assertEquals

/** Returns a sample [PathPdfObject] for testing purposes. */
private fun getSamplePathObject(): PathPdfObject {
    return PathPdfObject(
        255,
        10f,
        listOf(
            PathPdfObject.PathInput(10f, 10f),
            PathPdfObject.PathInput(20f, 20f),
            PathPdfObject.PathInput(30f, 30f),
            PathPdfObject.PathInput(40f, 40f),
            PathPdfObject.PathInput(50f, 50f),
        ),
    )
}

/**
 * Returns a sample [StampAnnotation] for testing purposes.
 *
 * @param pageNum The page number for the annotation.
 * @param bounds The bounds of the annotation.
 * @return A sample [StampAnnotation].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
fun getSampleStampAnnotation(
    pageNum: Int,
    bounds: RectF = RectF(0f, 0f, 100f, 100f),
): StampAnnotation {
    return StampAnnotation(pageNum, bounds, listOf(getSamplePathObject()))
}

/**
 * Asserts that two [StampAnnotation] objects are equal for testing purposes.
 *
 * @param expectedAnnotation The expected [StampAnnotation].
 * @param actualAnnotation The actual [StampAnnotation].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
fun assertStampAnnotationEquals(
    expectedAnnotation: StampAnnotation,
    actualAnnotation: StampAnnotation,
) {
    assertEquals(expectedAnnotation.bounds, actualAnnotation.bounds)
    assertEquals(expectedAnnotation.pageNum, actualAnnotation.pageNum)

    // Assert that the list of PdfObjects within the annotation is the same
    assertEquals(expectedAnnotation.pdfObjects.size, actualAnnotation.pdfObjects.size)
    for (i in expectedAnnotation.pdfObjects.indices) {
        val actualPathPdfObject = actualAnnotation.pdfObjects[i] as PathPdfObject
        val expectedPathPdfObject = expectedAnnotation.pdfObjects[i] as PathPdfObject
        assertEquals(actualPathPdfObject.brushColor, expectedPathPdfObject.brushColor)
        assertEquals(actualPathPdfObject.brushWidth, expectedPathPdfObject.brushWidth)

        // Assert that the list of PathInputs within the PathPdfObject is the same
        assertEquals(actualPathPdfObject.inputs.size, expectedPathPdfObject.inputs.size)
        for (j in actualPathPdfObject.inputs.indices) {
            val actualPathInput = actualPathPdfObject.inputs[j]
            val expectedPathInput = expectedPathPdfObject.inputs[j]
            assertEquals(actualPathInput.x, expectedPathInput.x)
            assertEquals(actualPathInput.y, expectedPathInput.y)
        }
    }
}
