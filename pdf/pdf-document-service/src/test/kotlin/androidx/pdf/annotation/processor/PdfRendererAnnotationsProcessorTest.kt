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

package androidx.pdf.annotation.processor

import android.os.Build
import androidx.annotation.RequiresExtension
import androidx.pdf.adapter.FakePdfDocumentRenderer
import androidx.pdf.adapter.FakePdfPage
import androidx.pdf.adapter.PdfDocumentRenderer
import androidx.pdf.annotation.createPdfAnnotationDataList
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RequiresExtension(extension = Build.VERSION_CODES.S, version = 18)
@RunWith(RobolectricTestRunner::class)
class PdfRendererAnnotationsProcessorTest {
    private lateinit var processor: PdfRendererAnnotationsProcessor
    private lateinit var pdfDocumentRenderer: PdfDocumentRenderer

    @Before
    fun setUp() {
        pdfDocumentRenderer =
            FakePdfDocumentRenderer(isLinearized = false, pageCount = 10, formType = 1) { pageNum ->
                FakePdfPage(pageNum, 100, 100)
            }
        processor = PdfRendererAnnotationsProcessor(pdfDocumentRenderer)
    }

    @Test
    fun process_emptyList_returnsEmptyResult() {
        val result = processor.process(emptyList())
        assertTrue(result.success.isEmpty())
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun process_singleSuccessfulAnnotation_returnsSuccess() {
        val expectedNumAnnots = 1
        val annotations = createPdfAnnotationDataList(numAnnots = expectedNumAnnots, pathLength = 1)

        val result = processor.process(annotations)

        assertTrue(result.success.isNotEmpty())
        assertTrue(result.success.size == expectedNumAnnots)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun process_singleFailedAnnotation_addPageAnnotationFails_returnsFailure() {
        val annotations =
            createPdfAnnotationDataList(numAnnots = 1, pathLength = 1, invalidRatio = 1f)

        val result = processor.process(annotations)

        assertTrue(result.success.isEmpty())
        assertTrue(result.failures.isNotEmpty())
    }

    @Test
    fun process_multipleAnnotations_returnsSuccess() {
        val expectedNumAnnots = 10
        val annotations = createPdfAnnotationDataList(numAnnots = expectedNumAnnots, pathLength = 1)

        val result = processor.process(annotations)

        assertTrue(result.success.isNotEmpty())
        assertTrue(result.success.size == expectedNumAnnots)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun process_multipleAnnotations_addPageAnnotationFails_returnsFailure() {
        val expectedNumAnnots = 10
        val annotations =
            createPdfAnnotationDataList(
                numAnnots = expectedNumAnnots,
                pathLength = 1,
                invalidRatio = 1f,
            )

        val result = processor.process(annotations)

        assertTrue(result.success.isEmpty())
        assertTrue(result.failures.isNotEmpty())
        assertTrue(result.failures.size == 10)
    }

    @Test
    fun process_multipleAnnotations_partialSuccess_returnsSuccessAndFailures() {
        val expectedNumAnnots = 10
        val annotations =
            createPdfAnnotationDataList(
                numAnnots = expectedNumAnnots,
                pathLength = 1,
                invalidRatio = 0.5f,
            )

        val result = processor.process(annotations)

        assertTrue(result.success.isNotEmpty())
        assertTrue(result.success.size == 5)
        assertTrue(result.failures.isNotEmpty())
        assertTrue(result.failures.size == 5)
    }
}
