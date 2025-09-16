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

package androidx.pdf.annotation.draftstate

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfEdits
import java.util.Collections
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A simple implementation of [AnnotationEditsDraftState] that stores annotation edits in memory.
 *
 * ## Thread-Safety
 *
 * This class is thread-safe. All public methods that modify the internal state are protected by a
 * [ReentrantLock] to ensure atomicity of compound operations. Read operations rely on the
 * thread-safe nature of the underlying synchronized collections.
 *
 * ## Time Complexity
 *
 * The time complexity of the operations is as follows:
 * - [getEdits]: O(k) where k is the number of annotations on the page (due to list creation).
 * - [addEdit]: O(1) on average.
 * - [removeEdit]: O(1) on average.
 * - [updateEdit]: O(1) on average.
 * - [toPdfEdits]: O(N) where N is the total number of edits across all pages.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class InMemoryAnnotationEditsDraftState() : AnnotationEditsDraftState {
    /**
     * The primary data store. Maps a page number to its own map of edits. The inner map uses
     * [EditId] as the key for efficient lookups and [PdfAnnotationData] as the value.
     *
     * A [LinkedHashMap] is used as the inner map to preserve the insertion order of annotations,
     * which is a critical requirement.
     */
    private val editsByPage: MutableMap<Int, MutableMap<EditId, PdfAnnotationData>> =
        Collections.synchronizedMap(HashMap())
    private val lock: ReentrantLock = ReentrantLock()

    /**
     * Retrieves a list of saved annotations for a given page number.
     *
     * @param pageNum The page number to retrieve edits for.
     * @return A list of [PdfAnnotationData] objects for the specified page, or an empty list if no
     *   edits exist.
     */
    override fun getEdits(pageNum: Int): List<PdfAnnotationData> =
        editsByPage[pageNum]?.values?.toList() ?: emptyList()

    /**
     * Adds a new edit for the given annotation.
     *
     * @param annotation The [PdfAnnotation] to add.
     * @return The [EditId] of the newly added edit.
     */
    override fun addEdit(annotation: PdfAnnotation): EditId {
        val pageNum = annotation.pageNum
        val editId = createEditIdForPage(pageNum)
        val annotationData = PdfAnnotationData(editId, annotation)

        lock.withLock {
            val pageEdits =
                editsByPage.getOrPut(pageNum) {
                    // Using LinkedHashMap to maintain insertion order for each page's annotations.
                    Collections.synchronizedMap(LinkedHashMap())
                }
            pageEdits[editId] = annotationData
        }

        return editId
    }

    /**
     * Removes an edit with the specified [EditId].
     *
     * @param editId The ID of the edit to remove.
     * @return The removed [PdfAnnotation].
     * @throws NoSuchElementException if the edit with the given ID is not found.
     */
    override fun removeEdit(editId: EditId): PdfAnnotation {
        lock.withLock {
            val pageEdits =
                editsByPage[editId.pageNum]
                    ?: throw NoSuchElementException(
                        "No annotations present on page ${editId.pageNum}."
                    )

            val removedData =
                pageEdits.remove(editId)
                    ?: throw NoSuchElementException("Annotation with ID $editId not found.")

            if (pageEdits.isEmpty()) {
                editsByPage.remove(editId.pageNum)
            }
            return removedData.annotation
        }
    }

    /**
     * Updates an existing edit with the specified [EditId] and new [PdfAnnotation].
     *
     * @param editId The ID of the edit to update.
     * @param annotation The new [PdfAnnotation] data.
     * @return The old [PdfAnnotation].
     * @throws NoSuchElementException if the edit with the given ID is not found.
     */
    override fun updateEdit(editId: EditId, annotation: PdfAnnotation): PdfAnnotation {
        lock.withLock {
            val pageEdits =
                editsByPage[editId.pageNum]
                    ?: throw NoSuchElementException(
                        "No annotations present on page ${editId.pageNum}."
                    )

            if (!pageEdits.containsKey(editId)) {
                throw NoSuchElementException("Annotation with ID $editId not found.")
            }

            val annotationData = PdfAnnotationData(editId, annotation)
            val previousData = pageEdits.put(editId, annotationData)!!
            return previousData.annotation
        }
    }

    /**
     * Creates a snapshot of all current edits.
     *
     * @return A [PdfEdits] object containing a copy of all edits.
     */
    override fun toPdfEdits(): PdfEdits {
        lock.withLock {
            val snapshot =
                editsByPage.entries.associate { (pageNum, pageEdits) ->
                    pageNum to pageEdits.values.toList()
                }
            return PdfEdits(snapshot)
        }
    }

    /**
     * Retrieves the mutable map of edits to savedAnnotation for a given edit ID.
     *
     * @param editId The ID of the edit to retrieve.
     * @return The [PdfAnnotation] for the given edit ID.
     * @throws NoSuchElementException if the edit with the given ID is not found.
     */
    @VisibleForTesting
    public fun getPdfAnnotationForId(editId: EditId): PdfAnnotation {
        return editsByPage[editId.pageNum]?.get(editId)?.annotation
            ?: throw NoSuchElementException("Annotation with ID $editId not found.")
    }

    /**
     * Generates a new unique [EditId] for a given page number.
     *
     * @param pageNum The page number for which to generate the new ID.
     * @return A new, unique [EditId].
     */
    private fun createEditIdForPage(pageNum: Int): EditId {
        return EditId(pageNum, UUID.randomUUID().toString())
    }
}
