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

@Document(name = "com.google.android.appfunctions.schema.common.v1.types.Attachment")
class LegacyAttachment(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /** The display name of the attachment. */
    @Document.StringProperty(required = true) val displayName: String,
    /** The MIME type of the attachment. Format defined in RFC 6838. */
    @Document.StringProperty val mimeType: String? = null,
    /** The URI of the attachment. */
    @Document.DocumentProperty(required = true) val uri: LegacyUri,
)

@Document(name = "com.google.android.appfunctions.schema.common.v1.types.Uri")
data class LegacyUri(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    @Document.StringProperty(required = true) val uri: String,
)
