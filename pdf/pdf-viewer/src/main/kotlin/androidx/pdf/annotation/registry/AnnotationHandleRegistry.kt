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

package androidx.pdf.annotation.registry

/** Maintains a bidirectional mapping between internal data identifiers and stable handles. */
internal interface AnnotationHandleRegistry {
    /**
     * Retrieves or generates a stable Handle ID for a given [sourceId].
     *
     * If a handle already exists for this source ID, it is returned. If not, a new unique handle is
     * generated, mapped, and returned.
     *
     * @param sourceId The internal identifier.
     * @return A stable, session-scoped string safe for use.
     */
    fun getHandleId(sourceId: String): String

    /**
     * Resolves a Handle ID back to its underlying Source ID.
     *
     * @param handleId The session-scoped identifier.
     * @return The underlying Source ID, or `null` if the handle is not found/expired.
     */
    fun getSourceId(handleId: String): String?

    /** Clears all mappings. */
    fun clear(): Unit
}
