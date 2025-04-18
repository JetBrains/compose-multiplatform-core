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

package androidx.appfunctions

import androidx.appsearch.annotation.Document

@Document(name = "com.google.android.appfunctions.schema.common.v1.notes.CreateNoteParams")
data class LegacyCreateNoteParams(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /** The title of the note. */
    @Document.StringProperty(required = true) val title: String,
    /** The content of the note. */
    @Document.StringProperty val content: String? = null,
    /** The attachments of the note. */
    @Document.DocumentProperty val attachments: List<LegacyAttachment> = emptyList(),
    /** The ID of the folder the note is in, if any. */
    @Document.StringProperty val folderId: String? = null,
    /**
     * The ID the agent will use to refer to this note in all subsequent requests. If provided, the
     * app must use this ID (eg. by maintaining a mapping between its internal ID and this external
     * ID).
     */
    @Document.StringProperty val externalId: String? = null,
)

@Document(name = "com.google.android.appfunctions.schema.common.v1.notes.Note")
data class LegacyNote(
    @Document.Namespace val namespace: String = "", // unused
    /** The ID of the note. */
    @Document.Id val id: String,
    /** The title of the note. */
    @Document.StringProperty(required = true) val title: String,
    /** The content of the note. */
    @Document.StringProperty val content: String? = null,
    /** The attachments of the note. */
    @Document.DocumentProperty val attachments: List<LegacyAttachment> = emptyList(),
    /** The ID of the folder the note is in, if any. */
    @Document.StringProperty val folderId: String? = null,
)
