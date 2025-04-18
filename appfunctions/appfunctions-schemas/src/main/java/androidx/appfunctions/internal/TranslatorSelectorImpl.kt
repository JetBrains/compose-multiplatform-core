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

package androidx.appfunctions.internal

import androidx.annotation.RequiresApi
import androidx.appfunctions.metadata.AppFunctionSchemaMetadata
import androidx.appfunctions.schema.notes.APP_FUNCTION_SCHEMA_CATEGORY_NOTES
import androidx.appfunctions.schema.notes.translators.CreateNoteTranslator

@RequiresApi(33)
internal class TranslatorSelectorImpl : TranslatorSelector {

    override fun getTranslator(schemaMetadata: AppFunctionSchemaMetadata): Translator? {
        // TODO: Generate the mapping.
        if (schemaMetadata.category == APP_FUNCTION_SCHEMA_CATEGORY_NOTES) {
            if (schemaMetadata.name == "createNote") {
                return CreateNoteTranslator()
            }
        }
        return null
    }
}
