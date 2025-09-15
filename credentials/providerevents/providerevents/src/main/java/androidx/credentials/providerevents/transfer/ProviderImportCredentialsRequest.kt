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

package androidx.credentials.providerevents.transfer

import android.net.Uri
import androidx.annotation.RestrictTo
import androidx.credentials.provider.CallingAppInfo

/**
 * Request received by the provider after the query phase of the import flow is complete i.e. the
 * user was presented with a list of entries, and the user has now made a selection from the list of
 * [ExportEntry] presented on the selector UI.
 *
 * When the provider is responding to this request, it must write the response to the [uri] provided
 * in this request, so that the importer can read the response from the transfer medium.
 *
 * @property request a request to import the provider's credentials
 * @property callingAppInfo the caller's app info
 * @property uri the FileProvider uri that the importer will read the response from
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ProviderImportCredentialsRequest(
    public val request: ImportCredentialsRequest,
    public val callingAppInfo: CallingAppInfo,
    public val uri: Uri,
)
