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
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData

@RequiresApi(33)
internal object UriTranslator {
    fun upgradeUri(legacyUri: AppFunctionData): Uri = Uri.parse(legacyUri.getString("uri"))

    fun downgradeUri(uri: Uri): AppFunctionData {
        return AppFunctionData.Builder(qualifiedName = "").setString("uri", uri.toString()).build()
    }
}
