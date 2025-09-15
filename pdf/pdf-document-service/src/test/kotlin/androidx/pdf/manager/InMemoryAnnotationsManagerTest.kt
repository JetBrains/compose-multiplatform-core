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

package androidx.pdf.manager

import android.net.Uri
import androidx.pdf.FakeEditablePdfDocument
import androidx.pdf.annotation.createStampAnnotationWithPath
import androidx.pdf.annotation.manager.InMemoryAnnotationsManager
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.StampAnnotation
import androidx.pdf.util.createDummyUri
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InMemoryAnnotationsManagerTest {

    private lateinit var fakeUri: Uri
    private lateinit var fakeDocument: FakeEditablePdfDocument
    private lateinit var annotationsManager: InMemoryAnnotationsManager

    @Before
    fun setUp() {
        fakeUri = createDummyUri("test.pdf")
        fakeDocument = FakeEditablePdfDocument(uri = fakeUri, pageCount = 10)
        annotationsManager = InMemoryAnnotationsManager(::getEditsForPage)
    }

    @Test
    fun getAnnotationsForPage_noAnnotations_returnsEmptyList() = runTest {
        val annotations = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotations).isEmpty()
    }

    @Test
    fun getAnnotationsForPage_existingAnnotations_returnsAnnotationsFromDocument() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, existingAnnotation)

        val annotations = annotationsManager.getAnnotationsForPage(0)

        assertThat(annotations).hasSize(1)
        assertThat(annotations[0].editId.pageNum).isEqualTo(0)
    }

    @Test
    fun getAnnotationsForPage_invokeGetTwice_fetchesOnlyOnce() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 1, pathSize = 10)
        fakeDocument.addAnnotationToPage(1, existingAnnotation)

        // Call twice
        annotationsManager.getAnnotationsForPage(1)
        val annotations = annotationsManager.getAnnotationsForPage(1)

        assertThat(annotations).hasSize(1)
        assertThat(fakeDocument.getAnnotationsForPageCallCount[1]).isEqualTo(1)
    }

    @Test
    fun addAnnotation_addsToDraftState_andIsReturnedByGetAnnotations() = runTest {
        val newAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)

        val editId = annotationsManager.addAnnotation(newAnnotation)
        val annotations = annotationsManager.getAnnotationsForPage(0)

        assertThat(annotations).hasSize(1)
        assertThat(annotations[0].editId).isEqualTo(editId)
        assertThat(fakeDocument.getAnnotationsForPageCallCount[0]).isEqualTo(1)
    }

    @Test
    fun addAnnotation_existingAndAdded_returnsBoth() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, existingAnnotation)

        // Fetch existing first to populate cache and draft
        annotationsManager.getAnnotationsForPage(0)

        val newAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        annotationsManager.addAnnotation(newAnnotation)

        val allAnnotations = annotationsManager.getAnnotationsForPage(0)

        assertThat(allAnnotations).hasSize(2)
    }

    @Test
    fun addAnnotation_returnsUniqueEditId() {
        val annotation1 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        val annotation2 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)

        val editId1 = annotationsManager.addAnnotation(annotation1)
        val editId2 = annotationsManager.addAnnotation(annotation2)

        assertThat(editId1).isNotNull()
        assertThat(editId2).isNotNull()
        assertThat(editId1).isNotEqualTo(editId2)
    }

    @Test
    fun getSnapshot_empty_returnsEmptyState() = runTest {
        val snapshot = annotationsManager.getSnapshot()
        assertThat(snapshot.editsByPage).isEmpty()
    }

    @Test
    fun getFullAnnotationStateSnapshot_withAnnotations_returnsCorrectState() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        val newAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)

        fakeDocument.addAnnotationToPage(0, existingAnnotation)
        val existingAnnots = annotationsManager.getAnnotationsForPage(0)
        val editId = annotationsManager.addAnnotation(newAnnotation)

        val snapshot = annotationsManager.getSnapshot()
        val allEdits = snapshot.editsByPage

        assertThat(existingAnnots).hasSize(1)
        assertThat(allEdits.size).isEqualTo(1) // Only single entry in the map
        assertThat(allEdits[0]).isNotNull()
        assertThat(allEdits[0]).hasSize(2)
        assertThat(allEdits[0]!!.map { it.id }).contains(editId)
    }

    @Test
    fun getAnnotationsForPage_multiplePages_annotationsAreSeparate() = runTest {
        val annotationPage0 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        val annotationPage1 = createStampAnnotationWithPath(pageNum = 1, pathSize = 10)
        val newAnnotationPage0 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, annotationPage0)
        fakeDocument.addAnnotationToPage(1, annotationPage1)

        // Get page 0, then add to page 0
        val annotations0Initial = annotationsManager.getAnnotationsForPage(0)
        val editId = annotationsManager.addAnnotation(newAnnotationPage0)

        // Get page 1
        val annotations1 = annotationsManager.getAnnotationsForPage(1)
        val annotations0Final = annotationsManager.getAnnotationsForPage(0)

        assertThat(annotations0Initial.size).isEqualTo(1)
        assertThat(annotations1.size).isEqualTo(1)
        assertThat(annotations0Final.size).isEqualTo(2)
        assertThat(annotations0Final.map { it.editId.value }).contains(editId.value)
    }

    @Test
    fun removeAnnotation_removesAnnotation() = runTest {
        val annotation1 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)

        val editId1 = annotationsManager.addAnnotation(annotation1)
        val annotationsInitial = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsInitial.size).isEqualTo(1)

        annotationsManager.removeAnnotation(editId1)
        val annotationsFinal = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsFinal.size).isEqualTo(0)
    }

    @Test
    fun removeAnnotation_editIdNotPresent() = runTest {
        val annotation1 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)

        val editId1 = annotationsManager.addAnnotation(annotation1)
        val annotationsInitial = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsInitial.size).isEqualTo(1)

        annotationsManager.removeAnnotation(editId1)

        assertThrows(NoSuchElementException::class.java) {
            annotationsManager.removeAnnotation(editId1)
        }
    }

    @Test
    fun updateAnnotation_updatesAnnotation() = runTest {
        val annotation1 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        val annotation2 = createStampAnnotationWithPath(pageNum = 0, pathSize = 20)

        val editId1 = annotationsManager.addAnnotation(annotation1)
        val annotationsInitial = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsInitial.size).isEqualTo(1)

        annotationsManager.updateAnnotation(editId1, annotation2)
        val annotationsFinal = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsFinal.size).isEqualTo(1)
        assertThat(annotationsFinal[0].editId).isEqualTo(editId1)
        assertThat((annotationsFinal[0].annotation as StampAnnotation).pdfObjects.size)
            .isEqualTo(20)
    }

    @Test
    fun updateAnnotation_editIdNotPresent() = runTest {
        val annotation1 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)

        val editId1 = annotationsManager.addAnnotation(annotation1)
        val annotationsInitial = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsInitial.size).isEqualTo(1)

        annotationsManager.removeAnnotation(editId1)

        assertThrows(NoSuchElementException::class.java) {
            annotationsManager.updateAnnotation(editId1, annotation1)
        }
    }

    @Test
    fun clearUncommittedEdits_withNoUncommittedEdits_doesNotChangeAnnotations() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, existingAnnotation)

        // Fetch existing annotations to populate the cache and draft state.
        val initialAnnotations = annotationsManager.getAnnotationsForPage(0)
        assertThat(initialAnnotations).hasSize(1)

        annotationsManager.clearUncommittedEdits()

        val annotationsAfterClear = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsAfterClear).hasSize(1)
        assertThat(annotationsAfterClear[0].annotation).isEqualTo(existingAnnotation)
    }

    @Test
    fun clearUncommittedEdits_withAddedAnnotation_removesTheAddedAnnotation() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, existingAnnotation)

        // Fetch existing annotations to populate the cache and draft state.
        annotationsManager.getAnnotationsForPage(0)

        val newAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 15)
        annotationsManager.addAnnotation(newAnnotation)

        var annotations = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotations).hasSize(2)

        annotationsManager.clearUncommittedEdits()

        annotations = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotations).hasSize(1)
        assertThat(annotations[0].annotation).isEqualTo(existingAnnotation)
        assertThat((annotations[0].annotation as StampAnnotation).pdfObjects.size).isEqualTo(10)
    }

    @Test
    fun clearUncommittedEdits_withRemovedExistingAnnotation_restoresTheAnnotation() = runTest {
        val existingAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, existingAnnotation)

        // Fetch and cache
        val initialAnnotations = annotationsManager.getAnnotationsForPage(0)
        assertThat(initialAnnotations).hasSize(1)
        val editIdToRemove = initialAnnotations[0].editId

        // Remove the existing annotation (this is an uncommitted edit)
        annotationsManager.removeAnnotation(editIdToRemove)
        assertThat(annotationsManager.getAnnotationsForPage(0)).isEmpty()

        annotationsManager.clearUncommittedEdits()

        val annotationsAfterClear = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsAfterClear).hasSize(1)
        assertThat(annotationsAfterClear[0].annotation).isEqualTo(existingAnnotation)
    }

    @Test
    fun clearUncommittedEdits_withUpdatedExistingAnnotation_revertsTheUpdate() = runTest {
        val originalAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        fakeDocument.addAnnotationToPage(0, originalAnnotation)

        // Fetch and cache
        val initialAnnotations = annotationsManager.getAnnotationsForPage(0)
        assertThat(initialAnnotations).hasSize(1)
        val editIdToUpdate = initialAnnotations[0].editId

        // Update the existing annotation (this is an uncommitted edit)
        val updatedAnnotation = createStampAnnotationWithPath(pageNum = 0, pathSize = 20)
        annotationsManager.updateAnnotation(editIdToUpdate, updatedAnnotation)

        val annotationsAfterUpdate = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsAfterUpdate).hasSize(1)
        assertThat(annotationsAfterUpdate[0].annotation).isEqualTo(updatedAnnotation)

        annotationsManager.clearUncommittedEdits()

        val annotationsAfterClear = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsAfterClear).hasSize(1)
        assertThat(annotationsAfterClear[0].annotation).isEqualTo(originalAnnotation)
    }

    @Test
    fun clearUncommittedEdits_withNoExistingAnnotations_clearsAllAddedAnnotations() = runTest {
        val newAnnotationPage0 = createStampAnnotationWithPath(pageNum = 0, pathSize = 10)
        annotationsManager.addAnnotation(newAnnotationPage0)

        val newAnnotationPage1 = createStampAnnotationWithPath(pageNum = 1, pathSize = 5)
        annotationsManager.addAnnotation(newAnnotationPage1)

        // Initial fetch to populate caches and make document aware
        var annotationsPage0 = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsPage0).hasSize(1)
        var annotationsPage1 = annotationsManager.getAnnotationsForPage(1)
        assertThat(annotationsPage1).hasSize(1)

        annotationsManager.clearUncommittedEdits()

        annotationsPage0 = annotationsManager.getAnnotationsForPage(0)
        assertThat(annotationsPage0).isEmpty()
        annotationsPage1 = annotationsManager.getAnnotationsForPage(1)
        assertThat(annotationsPage1).isEmpty()
    }

    private suspend fun getEditsForPage(pageNum: Int): List<PdfAnnotation> =
        fakeDocument.getEditsForPage<PdfAnnotationData>(pageNum).map { it.annotation }
}
