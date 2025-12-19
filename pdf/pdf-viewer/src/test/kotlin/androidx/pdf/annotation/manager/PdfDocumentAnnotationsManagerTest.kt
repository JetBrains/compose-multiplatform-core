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

package androidx.pdf.annotation.manager

import androidx.pdf.annotation.AnnotationHandleIdGenerator.composeAnnotationId
import androidx.pdf.annotation.KeyedPdfAnnotation
import androidx.pdf.annotation.draftstate.FakeAnnotationEditsDraftState
import androidx.pdf.annotation.models.TestPdfAnnotation
import androidx.pdf.annotation.operations.FakeAnnotationOperationsTracker
import androidx.pdf.annotation.operations.KeyedAnnotationOperation
import androidx.pdf.annotation.registry.FakeAnnotationHandleRegistry
import androidx.pdf.annotation.repository.FakeAnnotationsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [org.robolectric.annotation.Config.TARGET_SDK])
class PdfDocumentAnnotationsManagerTest {

    private lateinit var manager: PdfDocumentAnnotationsManager
    private lateinit var draftState: FakeAnnotationEditsDraftState
    private lateinit var repository: FakeAnnotationsRepository
    private lateinit var handleRegistry: FakeAnnotationHandleRegistry
    private lateinit var operationsTracker: FakeAnnotationOperationsTracker

    private val pageNum = 0

    // Test Data
    private val annotA = TestPdfAnnotation(pageNum = pageNum) // Basic Content A
    private val annotB = TestPdfAnnotation(pageNum = pageNum) // Basic Content B
    private val updatedAnnotA = TestPdfAnnotation(pageNum = pageNum) // Updated Content A

    @Before
    fun setup() {
        draftState = FakeAnnotationEditsDraftState()
        repository = FakeAnnotationsRepository()
        handleRegistry = FakeAnnotationHandleRegistry()
        operationsTracker = FakeAnnotationOperationsTracker()

        manager =
            PdfDocumentAnnotationsManager(draftState, repository, handleRegistry, operationsTracker)
    }

    @Test
    fun getAnnotations_noData_returnsEmptyList() = runTest {
        val result = manager.getAnnotations(pageNum)
        assertThat(result).isEmpty()
    }

    @Test
    fun getAnnotations_onlyPersisted_returnsMappedAnnotations() = runTest {
        val sourceId = "source_1"
        repository.seedAnnotations(pageNum, listOf(KeyedPdfAnnotation(sourceId, annotA)))

        val result = manager.getAnnotations(pageNum)

        assertThat(result).hasSize(1)
        assertThat(result[0].annotation).isEqualTo(annotA)
        // Verify a handle was generated
        val handleId = handleRegistry.getHandleId(sourceId)
        assertThat(result[0].key).isEqualTo(handleId)
    }

    @Test
    fun getAnnotations_onlyDrafts_returnsDraftAnnotations() = runTest {
        manager.addAnnotation(annotB) // This adds to draft state internally

        val result = manager.getAnnotations(pageNum)

        assertThat(result).hasSize(1)
        assertThat(result[0].annotation).isEqualTo(annotB)
    }

    @Test
    fun getAnnotations_persistedAndDrafts_returnsPersistedThenDrafts() = runTest {
        // Setup Persisted
        val sourceId = "source_1"
        repository.seedAnnotations(pageNum, listOf(KeyedPdfAnnotation(sourceId, annotA)))

        // Setup Draft
        manager.addAnnotation(annotB)

        val result = manager.getAnnotations(pageNum)

        assertThat(result).hasSize(2)
        // Order Verification: Persisted (A) then Draft (B)
        assertThat(result[0].annotation).isEqualTo(annotA)
        assertThat(result[1].annotation).isEqualTo(annotB)
    }

    @Test
    fun addAnnotation_addsToDraftAndTracker() = runTest {
        val composedId = manager.addAnnotation(annotA)

        // Verify returned ID format
        assertThat(composedId).contains(pageNum.toString())
        assertThat(composedId).contains("::")

        // Verify Draft State
        val edits = draftState.getEdits(pageNum)
        assertThat(edits).hasSize(1)
        assertThat(edits[0].annotation).isEqualTo(annotA)

        // Verify Tracker
        val snapshot = operationsTracker.getSnapshot()
        assertThat(snapshot).hasSize(1)
        assertThat(snapshot[0].operationType).isEqualTo(KeyedAnnotationOperation.OperationType.ADD)
    }

    @Test
    fun removeAnnotation_draftAnnotation_removesFromDraftAndState() = runTest {
        val composedId = manager.addAnnotation(annotA)

        val removed = manager.removeAnnotation(composedId)

        assertThat(removed).isEqualTo(annotA)
        assertThat(draftState.getEdits(pageNum)).isEmpty()

        // Tracker should effectively be empty due to ADD -> REMOVE squash logic in Fake
        val snapshot = operationsTracker.getSnapshot()
        assertThat(snapshot).isEmpty()
    }

    @Test
    fun removeAnnotation_persistedAnnotation_addsTombstoneToTracker() = runTest {
        val sourceId = "source_1"
        repository.seedAnnotations(pageNum, listOf(KeyedPdfAnnotation(sourceId, annotA)))

        // We need the handle ID to compose the ID passed to removeAnnotation
        val handleId = handleRegistry.getHandleId(sourceId)
        val composedId = composeAnnotationId(pageNum, handleId)

        val removed = manager.removeAnnotation(composedId)

        assertThat(removed).isEqualTo(annotA)

        // Verify Tracker has REMOVE op
        val snapshot = operationsTracker.getSnapshot()
        assertThat(snapshot).hasSize(1)
        assertThat(snapshot[0].operationType)
            .isEqualTo(KeyedAnnotationOperation.OperationType.REMOVE)
        assertThat(snapshot[0].keyedAnnotation.key).isEqualTo(handleId)

        // Verify getAnnotations hides it
        val visible = manager.getAnnotations(pageNum)
        assertThat(visible).isEmpty()
    }

    @Test
    fun removeAnnotation_persistedWithLocalUpdate_removesUpdatedContent() = runTest {
        val sourceId = "source_1"
        repository.seedAnnotations(pageNum, listOf(KeyedPdfAnnotation(sourceId, annotA)))
        val handleId = handleRegistry.getHandleId(sourceId)
        val composedId = composeAnnotationId(pageNum, handleId)

        // Update first
        manager.updateAnnotation(composedId, updatedAnnotA)

        // Remove
        val removed = manager.removeAnnotation(composedId)

        // Should return the UPDATED content, not original
        assertThat(removed).isEqualTo(updatedAnnotA)

        // Verify Tracker has REMOVE op
        assertThat(operationsTracker.isDeleted(handleId)).isTrue()
    }

    @Test
    fun updateAnnotation_draftAnnotation_updatesDraftAndTracker() = runTest {
        val composedId = manager.addAnnotation(annotA)

        val previous = manager.updateAnnotation(composedId, updatedAnnotA)

        assertThat(previous).isEqualTo(annotA)

        // Verify Draft State has new content
        val drafts = draftState.getEdits(pageNum)
        assertThat(drafts[0].annotation).isEqualTo(updatedAnnotA)

        // Verify Tracker has UPDATE op (or ADD with new content depending on squash implementation)
        val snapshot = operationsTracker.getSnapshot()
        assertThat(snapshot).isNotEmpty()
    }

    @Test
    fun updateAnnotation_persistedAnnotation_updatesTrackerAndReconciles() = runTest {
        val sourceId = "source_1"
        repository.seedAnnotations(pageNum, listOf(KeyedPdfAnnotation(sourceId, annotA)))
        val handleId = handleRegistry.getHandleId(sourceId)
        val composedId = composeAnnotationId(pageNum, handleId)

        val previous = manager.updateAnnotation(composedId, updatedAnnotA)

        assertThat(previous).isEqualTo(annotA)

        // Verify Tracker
        val updatedContent = operationsTracker.getUpdatedAnnotation(handleId)
        assertThat(updatedContent).isEqualTo(updatedAnnotA)

        // Verify getAnnotations returns updated content with same handle
        val result = manager.getAnnotations(pageNum)
        assertThat(result).hasSize(1)
        assertThat(result[0].key).isEqualTo(handleId)
        assertThat(result[0].annotation).isEqualTo(updatedAnnotA)
    }

    @Test
    fun updateAnnotation_persistedAnnotationMultipleTimes_returnsLastUpdatedValue() = runTest {
        val sourceId = "source_1"
        repository.seedAnnotations(pageNum, listOf(KeyedPdfAnnotation(sourceId, annotA)))
        val handleId = handleRegistry.getHandleId(sourceId)
        val composedId = composeAnnotationId(pageNum, handleId)

        manager.updateAnnotation(composedId, updatedAnnotA)
        val previous2 = manager.updateAnnotation(composedId, annotB)

        // The previous value for the second update should be the result of the first update
        assertThat(previous2).isEqualTo(updatedAnnotA)

        // Final state check
        val result = manager.getAnnotations(pageNum)
        assertThat(result[0].annotation).isEqualTo(annotB)
    }

    @Test
    fun removeAnnotation_invalidId_throwsException() = runTest {
        // Not in draft, not in registry/repo
        val invalidId = composeAnnotationId(pageNum, id = "fake_handle")

        assertThrows(Exception::class.java) { runBlocking { manager.removeAnnotation(invalidId) } }
    }

    @Test
    fun updateAnnotation_invalidId_throwsException() = runTest {
        val invalidId = composeAnnotationId(pageNum, id = "fake_handle")

        assertThrows(Exception::class.java) {
            runBlocking { manager.updateAnnotation(invalidId, annotA) }
        }
    }
}
