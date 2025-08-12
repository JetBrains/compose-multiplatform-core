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

package androidx.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.annotation.RequiresExtension
import androidx.pdf.annotation.EditablePdfDocument
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.StampAnnotation
import androidx.pdf.models.FormEditRecord
import androidx.pdf.models.FormWidgetInfo
import androidx.pdf.utils.AnnotationUtilsTest.Companion.isRequiredSdkExtensionAvailable
import androidx.pdf.utils.TestUtils
import androidx.pdf.utils.assertStampAnnotationEquals
import androidx.pdf.utils.createPfd
import androidx.pdf.utils.getSampleStampAnnotation
import androidx.pdf.utils.writeAnnotationsToFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.io.File
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM, codeName = "VanillaIceCream")
@RunWith(AndroidJUnit4::class)
class SandboxedPdfDocumentTest {

    @Test
    fun getPageInfo_validPageNumber_returnsValidPageInfo() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = 0

            val pageInfo = document.getPageInfo(pageNumber)

            val expectedHeight = 792
            val expectedWidth = 612
            assertThat(pageInfo.pageNum == pageNumber).isTrue()
            assertThat(pageInfo.height == expectedHeight).isTrue()
            assertThat(pageInfo.width == expectedWidth).isTrue()
        }
    }

    @Test
    fun getPageInfo_validDimension_onCorruptedPage() = runTest {
        withDocument(PDF_DOCUMENT_PARTIALLY_CORRUPTED_FILE) { document ->
            val pageNumber = 5

            val pageInfo = document.getPageInfo(pageNumber)

            val expectedHeight = 400
            val expectedWidth = 400
            assertThat(pageInfo.pageNum == pageNumber).isTrue()
            assertThat(pageInfo.height == expectedHeight).isTrue()
            assertThat(pageInfo.width == expectedWidth).isTrue()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun getPageInfo_invalidPage_throwsIllegalArgumentException() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = 4

            document.getPageInfo(pageNumber)
        }
    }

    @Test
    fun getPageInfos_partialPageRange_returnsValidPageInfos() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageRange = 1..2

            val pageInfos = document.getPageInfos(pageRange)

            val expectedHeight = 792
            val expectedWidth = 612
            val pageIterator = pageInfos.iterator()

            assertThat(pageInfos.size == 2).isTrue()
            for (index: Int in pageRange) {
                assertThat(pageIterator.hasNext()).isTrue()
                val pageInfo = pageIterator.next()
                assertThat(pageInfo.pageNum == index).isTrue()
                assertThat(pageInfo.height == expectedHeight).isTrue()
                assertThat(pageInfo.width == expectedWidth).isTrue()
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun getPageInfos_invalidPageRange_throwsIllegalArgumentException() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val invalidPageRange = 2..4

            document.getPageInfos(invalidPageRange)
        }
    }

    @Test
    fun searchDocument_singlePageSearch_returnsSparseArrayOfResults() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val query = "lorem"
            val pageRange = 0..0

            val results = document.searchDocument(query, pageRange)

            val expectedTotalPageResults = 1
            val expectedFirstPageResults = 2

            assertThat(results.size() == expectedTotalPageResults).isTrue()
            assertThat(results[0].size == expectedFirstPageResults).isTrue()
        }
    }

    @Test
    fun searchDocument_partialDocumentSearch_returnsSparseArrayOfResults() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val query = "lorem"
            val pageRange = 1..2

            val results = document.searchDocument(query, pageRange)

            val expectedTotalPageResults = 2
            val expectedSecondPageResults = 1
            val expectedThirdPageResults = 1

            assertThat(results.size() == expectedTotalPageResults).isTrue()
            assertThat(results[0] == null).isTrue()
            assertThat(results[1].size == expectedSecondPageResults).isTrue()
            assertThat(results[2].size == expectedThirdPageResults).isTrue()
        }
    }

    @Test
    fun searchDocument_fullDocumentSearch_returnsSparseArrayOfResults() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val query = "lorem"
            val pageRange = 0..2

            val results = document.searchDocument(query, pageRange)

            val expectedTotalPageResults = 3
            val expectedFirstPageResults = 2
            val expectedSecondPageResults = 1
            val expectedThirdPageResults = 1

            assertThat(results.size() == expectedTotalPageResults).isTrue()
            assertThat(results[0].size == expectedFirstPageResults).isTrue()
            assertThat(results[1].size == expectedSecondPageResults).isTrue()
            assertThat(results[2].size == expectedThirdPageResults).isTrue()
        }
    }

    @Test
    fun searchDocument_fullDocumentSearch_withSinglePageResults() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val query = "pages are all the same size"
            val pageRange = 0..2

            val results = document.searchDocument(query, pageRange)

            // Assert sparse array doesn't contain empty result lists
            assertThat(results.size()).isEqualTo(1)
            // Assert single result on first page
            assertThat(results[0].size).isEqualTo(1)
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    @Test
    fun getSelectionBounds_returnsPageSelection() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = 0
            val start = PointF(100f, 100f)
            val stop = PointF(120f, 100f)

            val selection = document.getSelectionBounds(pageNumber, start, stop)

            val expectedSelectedText = "F i"
            assertThat(selection != null).isTrue()
            assertThat(selection!!.page == pageNumber).isTrue()
            assertThat(selection.selectedTextContents.size == 1).isTrue()
            assertThat(selection.selectedTextContents[0].text == expectedSelectedText).isTrue()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun getSelectionBounds_invalidPageNumber_throwsIllegalArgumentException() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = -1
            val start = PointF(100f, 100f)
            val stop = PointF(200f, 200f)

            document.getSelectionBounds(pageNumber, start, stop)
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    @Test
    fun getSelectionBounds_emptySelection_returnsNull() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = 0
            val start = PointF(100f, 100f)
            val stop = PointF(100f, 100f) // Empty selection

            val selection = document.getSelectionBounds(pageNumber, start, stop)

            assertThat(selection == null).isTrue()
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    @Test
    fun getSelectAllSelectionBounds() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = 0

            val selection = document.getSelectAllSelectionBounds(pageNumber)?.selectedTextContents
            val expectedSelection = document.getPageContent(pageNumber)?.textContents

            assertNotNull(selection)
            assertNotNull(expectedSelection)
            assertThat(selection?.size == expectedSelection?.size).isTrue()
            for (index: Int in 0..selection!!.size - 1) {
                assertThat(selection[index].text == expectedSelection!![index].text).isTrue()
            }
        }
    }

    @Test
    fun getPageContent_validPageNumber_returnsPageContentWithTextAndImages() = runTest {
        withDocument(PDF_DOCUMENT_WITH_TEXT_AND_IMAGE) { document ->
            val pageNumber = 0
            val expectedImageContentSize = 1
            val expectedAltText = "Social Security Administration Logo"
            val expectedTextContentSize = 1

            val pageContent = document.getPageContent(pageNumber)

            assertThat(pageContent != null).isTrue()
            assertThat(pageContent!!.textContents.size == expectedTextContentSize).isTrue()
            assertThat(pageContent.imageContents.size == expectedImageContentSize).isTrue()
            assertThat(pageContent.imageContents[0].altText == expectedAltText).isTrue()
        }
    }

    @Test
    fun getPageContent_pageWithOnlyText_returnsPageContentWithText() = runTest {
        withDocument(PDF_DOCUMENT) { document ->
            val pageNumber = 0

            val pageContent = document.getPageContent(pageNumber)

            assertThat(pageContent != null).isTrue()
            assertThat(pageContent!!.textContents.isNotEmpty()).isTrue()
            assertThat(pageContent.imageContents.isEmpty()).isTrue()
        }
    }

    @Test
    fun getPageLinks_validPageNumber_returnsPageLinksWithGotoAndExternalLinks() = runTest {
        withDocument(PDF_DOCUMENT_WITH_LINKS) { document ->
            val pageNumber = 0

            val pageLinks = document.getPageLinks(pageNumber)

            assertThat(pageLinks.gotoLinks.isNotEmpty()).isTrue()
            assertThat(pageLinks.externalLinks.isNotEmpty()).isTrue()
        }
    }

    @Test
    fun getBitmap_fullPage_returnsValidBitmap() = runTest {
        val document = openDocument(PDF_DOCUMENT)
        val pageNumber = 0
        val scaledPageSizePx = Size(500, 600)

        val bitmapSource = document.getPageBitmapSource(pageNumber)
        val bitmap = bitmapSource.getBitmap(scaledPageSizePx, tileRegion = null)

        assertThat(bitmap.width == scaledPageSizePx.width).isTrue()
        assertThat(bitmap.height == scaledPageSizePx.height).isTrue()
        assertFalse(bitmap.checkIsAllWhite())
        // TODO(b/377922353): Update this test for a more accurate bitmap comparison
    }

    @Test
    fun getBitmap_tileRegion_returnsValidBitmap() = runTest {
        val document = openDocument(PDF_DOCUMENT)
        val pageNumber = 0
        val scaledPageSizePx = Size(500, 600)
        val tileRegion = Rect(100, 100, 300, 400) // Example tile region

        val bitmapSource = document.getPageBitmapSource(pageNumber)
        bitmapSource.getBitmap(scaledPageSizePx, tileRegion = null)
        val bitmap = bitmapSource.getBitmap(scaledPageSizePx, tileRegion)

        assertThat(bitmap.width == tileRegion.width()).isTrue()
        assertThat(bitmap.height == tileRegion.height()).isTrue()
        assertFalse(bitmap.checkIsAllWhite())
        // TODO(b/377922353): Update this test for a more accurate bitmap comparison
    }

    @Test
    fun write_modifiedFormFields_returnsModifiedDocument() = runTest {
        val document = openDocument("click_form.pdf")
        val pageNum = 0
        val editableFormWidget =
            document.getFormWidgetInfos(pageNum).find {
                !it.readOnly && it.widgetType == FormWidgetInfo.WIDGET_TYPE_CHECKBOX
            }
        requireNotNull(editableFormWidget)

        // assert that the check-box is unselected
        assertThat(editableFormWidget.textValue).isEqualTo("false")

        val editRecord =
            FormEditRecord(
                pageNumber = pageNum,
                widgetIndex = editableFormWidget.widgetIndex,
                clickPoint =
                    Point(
                        editableFormWidget.widgetRect.centerX(),
                        editableFormWidget.widgetRect.centerY(),
                    ),
            )

        // Apply edit to select the check-box
        document.applyEdit(pageNum, editRecord)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val editedPdfFile = File(context.cacheDir, "edited_test_pdf.pdf")
        var pfd: ParcelFileDescriptor? = null
        try {
            if (!editedPdfFile.exists()) {
                editedPdfFile.createNewFile()
            }
            pfd = ParcelFileDescriptor.open(editedPdfFile, ParcelFileDescriptor.MODE_READ_WRITE)

            document.write(pfd!!)
            document.close()

            val editedDocumentUri = Uri.fromFile(editedPdfFile)

            val editedDocument =
                SandboxedPdfLoader(context, Dispatchers.Main).openDocument(editedDocumentUri)
            val editedFormWidget =
                editedDocument.getFormWidgetInfos(pageNum).find {
                    it.widgetIndex == editableFormWidget.widgetIndex
                }
            // assert that the check-box is selected in the edited pdf.
            assertThat(editedFormWidget?.textValue).isEqualTo("true")
            editedDocument.close()
        } finally {
            pfd?.close()
            editedPdfFile.delete()
        }
    }

    @Test
    fun applyEdits_writingAnnotationToStorage() = runTest {
        if (!isRequiredSdkExtensionAvailable()) return@runTest

        val pageNum = 1
        val sampleAnnotation = getSampleStampAnnotation(pageNum)
        val document = openDocument(PDF_DOCUMENT)
        assertThat(document is EditablePdfDocument).isTrue()
        if (document is EditablePdfDocument) {

            val context = ApplicationProvider.getApplicationContext<Context>()

            // Create a ParcelFileDescriptor for the testing annotations document in read-write
            // mode.
            val pfd = createPfd(context, PDF_ANNOTATION_DOCUMENT, "rwt")

            writeAnnotationsToFile(
                pfd,
                listOf(PdfAnnotationData(EditId(pageNum = 0, value = "0"), sampleAnnotation)),
            )

            val annotationResult = document.applyEdits(pfd)

            val actualAnnotations = annotationResult.success

            assertNotNull(actualAnnotations)
            assertThat(actualAnnotations.size).isEqualTo(1)
            assert(actualAnnotations[0].annotation is StampAnnotation)
            assertStampAnnotationEquals(
                sampleAnnotation,
                actualAnnotations[0].annotation as StampAnnotation,
            )
        }
    }

    @Test
    fun getAnnotationsForPage_addAndGetAnnotationFromService() = runTest {
        if (!isRequiredSdkExtensionAvailable()) return@runTest

        val pageNum = 1
        val expectedAnnotation1 = getSampleStampAnnotation(pageNum)
        val expectedAnnotation2 =
            getSampleStampAnnotation(pageNum = pageNum, bounds = RectF(100f, 100f, 200f, 200f))
        val document = openDocument(PDF_DOCUMENT)
        assertThat(document is EditablePdfDocument).isTrue()
        if (document is EditablePdfDocument) {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Create a ParcelFileDescriptor for the testing annotations document in read-write
            // mode.
            val pfd = createPfd(context, PDF_ANNOTATION_DOCUMENT, "rwt")

            val pdfAnnotationsData =
                listOf(
                    PdfAnnotationData(EditId(pageNum = 0, value = "1"), expectedAnnotation1),
                    PdfAnnotationData(EditId(pageNum = 0, value = "2"), expectedAnnotation2),
                )
            writeAnnotationsToFile(pfd, pdfAnnotationsData)
            document.applyEdits(pfd)

            val actualAnnotations = document.getAnnotationsForPage(pageNum)
            assertThat(actualAnnotations.size).isEqualTo(2)
            assert(actualAnnotations[0] is StampAnnotation)
            assertStampAnnotationEquals(
                expectedAnnotation1,
                actualAnnotations[0] as StampAnnotation,
            )
            assertStampAnnotationEquals(
                expectedAnnotation2,
                actualAnnotations[1] as StampAnnotation,
            )
        }
    }

    @Test
    fun getAnnotationsForPage_addAndGetEmptyAnnotationFromService() = runTest {
        if (!isRequiredSdkExtensionAvailable()) return@runTest

        val pageNum = 1
        val document = openDocument(PDF_DOCUMENT)
        assertThat(document is EditablePdfDocument).isTrue()
        if (document is EditablePdfDocument) {
            val context = ApplicationProvider.getApplicationContext<Context>()

            // Create a ParcelFileDescriptor for the testing annotations document in read-write
            // mode.
            val pfd = createPfd(context, PDF_ANNOTATION_DOCUMENT, "rwt")

            val pdfAnnotationsData = listOf<PdfAnnotationData>()
            writeAnnotationsToFile(pfd, pdfAnnotationsData)
            document.applyEdits(pfd)

            val actualAnnotations = document.getAnnotationsForPage(pageNum)
            assertThat(actualAnnotations.size).isEqualTo(0)
        }
    }

    companion object {
        private const val PDF_DOCUMENT = "sample.pdf"
        private const val PDF_ANNOTATION_DOCUMENT = "annotation_sample.json"
        private const val PDF_DOCUMENT_WITH_LINKS = "sample_links.pdf"
        private const val PDF_DOCUMENT_PARTIALLY_CORRUPTED_FILE = "partially_corrupted.pdf"
        private const val PDF_DOCUMENT_WITH_TEXT_AND_IMAGE = "alt_text.pdf"

        internal suspend fun withDocument(filename: String, block: suspend (PdfDocument) -> Unit) {
            val document = openDocument(filename)
            try {
                block(document)
            } catch (exception: Exception) {
                throw exception
            } finally {
                runTest { document.close() }
            }
        }

        private suspend fun openDocument(filename: String): PdfDocument {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val loader = SandboxedPdfLoader(context, Dispatchers.Main)
            val uri = TestUtils.openFile(context, filename)

            return loader.openDocument(uri)
        }

        private fun Bitmap.checkIsAllWhite(): Boolean {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    if (getPixel(x, y) != Color.WHITE) {
                        return false
                    }
                }
            }
            return true
        }
    }
}
