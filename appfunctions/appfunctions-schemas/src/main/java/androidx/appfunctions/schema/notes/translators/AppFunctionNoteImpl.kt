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

package androidx.appfunctions.schema.notes.translators

import android.net.Uri
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.schema.notes.AppFunctionNote

@AppFunctionSerializable
internal data class AppFunctionNoteImpl(
    override val id: String,
    override val title: String,
    override val content: String? = null,
    override val attachments: List<AttachmentImpl> = emptyList(),
    override val groupId: String? = null,
) : AppFunctionNote

@AppFunctionSerializable
internal data class AttachmentImpl(
    override val uri: Uri,
    override val displayName: String,
    override val mimeType: String? = null,
) : AppFunctionNote.Attachment
