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

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.internal.Translator

internal object NotesTranslatorSelector {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun getTranslator(schemaName: String): Translator? =
        when (schemaName) {
            "createNote" -> CreateNoteTranslator()
            "findNotes" -> FindNotesTranslator()
            "updateNote" -> UpdateNoteTranslator()
            else -> null
        }
}
