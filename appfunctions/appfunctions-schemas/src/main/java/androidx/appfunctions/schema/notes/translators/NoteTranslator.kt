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

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.schema.notes.translators.AttachmentTranslator.downgradeAttachment
import androidx.appfunctions.schema.notes.translators.AttachmentTranslator.upgradeAttachment

@RequiresApi(33)
internal object NoteTranslator {
    fun AppFunctionNoteImpl.downgradeNote(): AppFunctionData {
        return AppFunctionData.Builder(
                qualifiedName = "",
                id = id,
            )
            .setString("title", title)
            .apply {
                if (content != null) {
                    setString("content", content)
                }
                setAppFunctionDataList("attachments", attachments.map { it.downgradeAttachment() })
                if (groupId != null) {
                    setString("folderId", groupId)
                }
            }
            .build()
    }

    fun AppFunctionData.upgradeNote(): AppFunctionNoteImpl =
        AppFunctionNoteImpl(
            id = id,
            title = checkNotNull(getString("title")),
            content = getStringOrNull("content"),
            attachments =
                getAppFunctionDataList("attachments")?.map { it.upgradeAttachment() }
                    ?: emptyList(),
            groupId = getStringOrNull("folderId")
        )
}
