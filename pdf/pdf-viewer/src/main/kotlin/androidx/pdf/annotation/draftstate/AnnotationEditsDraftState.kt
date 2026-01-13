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
import androidx.pdf.EditsDraft
import androidx.pdf.annotation.KeyedPdfAnnotation
import androidx.pdf.annotation.models.EditId
import androidx.pdf.annotation.models.PdfAnnotation
import androidx.pdf.annotation.models.PdfAnnotationData
import androidx.pdf.annotation.models.PdfEdits

/** Responsible for managing the draft edits of annotations. */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public interface AnnotationEditsDraftState {
    /**
     * Retrieves the draft annotation by id for a specific page.
     *
     * @param pageNum The page number (0-indexed).
     * @param handleId The id associated with the annotation on the page.
     * @return [PdfAnnotation] object or null if the object is not found.
     */
    public fun getDraftAnnotation(pageNum: Int, handleId: String): PdfAnnotation?

    /**
     * Retrieves the draft annotations for a specific page.
     *
     * @param pageNum The page number (0-indexed).
     * @return A list of [KeyedPdfAnnotation] objects.
     */
    public fun getDraftAnnotations(pageNum: Int): List<KeyedPdfAnnotation>

    /**
     * Retrieves a snapshot of all current modifications accumulated in this draft state.
     *
     * @return An [EditsDraft] object containing the ordered collection of operations (Inserts,
     *   Updates, Removes).
     */
    public fun getModificationsSnapshot(): EditsDraft

    /**
     * Retrieves all annotation edits for a specific page.
     *
     * @param pageNum The page number (0-indexed) for which to retrieve edits.
     * @return A list of [PdfAnnotationData] objects representing the id and the persisted
     *   annotation.
     */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun getEdits(pageNum: Int): List<PdfAnnotationData>

    /**
     * Adds a new annotation edit to the draft state.
     *
     * @param id The [EditId] used to identify the annotation.
     * @param annotation The [PdfAnnotation] to add.
     */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun addEditById(id: EditId, annotation: PdfAnnotation)

    /**
     * Adds a keyed annotation to the draft.
     *
     * @param keyedAnnotation The [KeyedPdfAnnotation] to add.
     * @return The id assigned to the newly added annotation.
     */
    public fun addDraftAnnotation(keyedAnnotation: KeyedPdfAnnotation): String

    /**
     * Adds a new annotation edit to the draft state.
     *
     * @param annotation The [PdfAnnotation] to add.
     * @return The id assigned to the newly added annotation.
     */
    public fun addDraftAnnotation(annotation: PdfAnnotation): String

    /**
     * Adds a new annotation edit to the draft state.
     *
     * @param annotation The [PdfAnnotation] to add.
     * @return The [EditId] assigned to the newly added annotation.
     */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun addEdit(annotation: PdfAnnotation): EditId

    /**
     * Removes an existing annotation from the draft state.
     *
     * @param pageNum The specified page number.
     * @param annotationId The id of the annotation to remove.
     * @return The [PdfAnnotation] that was removed.
     */
    public fun removeAnnotation(pageNum: Int, annotationId: String): PdfAnnotation

    /**
     * Removes an existing annotation edit from the draft state.
     *
     * @param editId The [EditId] of the annotation to remove.
     * @return The [PdfAnnotation] that was removed.
     */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun removeEdit(editId: EditId): PdfAnnotation

    /**
     * Updates an existing annotation edit in the draft state.
     *
     * @param pageNum The specified page number.
     * @param annotationId The id of the annotation to update.
     * @param newAnnotation The new [PdfAnnotation] to replace the existing annotation.
     * @return The previous [PdfAnnotation].
     */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun updateDraftAnnotation(
        pageNum: Int,
        annotationId: String,
        newAnnotation: PdfAnnotation,
    ): PdfAnnotation

    /**
     * Updates an existing annotation edit in the draft state.
     *
     * @param editId The [EditId] of the annotation to update.
     * @param annotation The new [PdfAnnotation] to replace the existing annotation.
     * @return The updated [PdfAnnotation].
     */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun updateEdit(editId: EditId, annotation: PdfAnnotation): PdfAnnotation

    /** Returns the state of the draft as a [PdfEdits] object. */
    // TODO(b/462602307): Clean up after moving the draft state to view model
    public fun toPdfEdits(): PdfEdits

    /** Clears all annotation edits from the draft state. */
    public fun clear()

    public companion object {
        public fun create(): AnnotationEditsDraftState {
            return InMemoryAnnotationEditsDraftState()
        }
    }
}
