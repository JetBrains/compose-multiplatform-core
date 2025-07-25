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
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.internal.Translator
import androidx.appfunctions.schema.notes.UpdateNoteAppFunction
import androidx.appfunctions.schema.notes.translators.AttachmentTranslator.downgradeAttachment
import androidx.appfunctions.schema.notes.translators.AttachmentTranslator.upgradeAttachment
import androidx.appfunctions.schema.notes.translators.NoteTranslator.downgradeNote
import androidx.appfunctions.schema.notes.translators.NoteTranslator.upgradeNote
import androidx.appfunctions.schema.types.SetField

@RequiresApi(33)
internal class UpdateNoteTranslator : Translator {
    override fun upgradeRequest(request: AppFunctionData): AppFunctionData {
        val legacyUpdateNoteParams = checkNotNull(request.getAppFunctionData("updateNoteParams"))
        val parameters =
            UpdateNoteAppFunctionParams(
                noteId = checkNotNull(legacyUpdateNoteParams.getString("noteId")),
                title =
                    legacyUpdateNoteParams.getAppFunctionData("title")?.let {
                        SetField(checkNotNull(it.getString("value")))
                    },
                content =
                    legacyUpdateNoteParams.getAppFunctionData("content")?.let {
                        SetField(it.getString("value"))
                    },
                attachments =
                    legacyUpdateNoteParams.getAppFunctionData("attachments")?.let {
                        SetField(
                            checkNotNull(it.getAppFunctionDataList("value")).map { attachment ->
                                attachment.upgradeAttachment()
                            }
                        )
                    },
            )
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                "parameters",
                AppFunctionData.serialize(parameters, UpdateNoteAppFunctionParams::class.java)
            )
            .build()
    }

    override fun upgradeResponse(response: AppFunctionData): AppFunctionData {
        val legacyUpdateNoteResponse =
            checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))

        val upgradedResponse =
            UpdateNoteAppFunctionResponse(updatedNote = legacyUpdateNoteResponse.upgradeNote())

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                PROPERTY_RETURN_VALUE,
                AppFunctionData.serialize(
                    upgradedResponse,
                    UpdateNoteAppFunctionResponse::class.java
                )
            )
            .build()
    }

    override fun downgradeRequest(request: AppFunctionData): AppFunctionData {
        val parametersData = checkNotNull(request.getAppFunctionData("parameters"))

        val updateNoteAppFunctionParams =
            parametersData.deserialize(UpdateNoteAppFunctionParams::class.java)

        val downgradedRequest =
            AppFunctionData.Builder(qualifiedName = "")
                .setString("noteId", updateNoteAppFunctionParams.noteId)
                .apply {
                    updateNoteAppFunctionParams.title?.let {
                        setAppFunctionData(
                            "title",
                            AppFunctionData.Builder(qualifiedName = "")
                                .setString("value", it.value)
                                .build()
                        )
                    }

                    updateNoteAppFunctionParams.content?.let {
                        setAppFunctionData(
                            "content",
                            AppFunctionData.Builder(qualifiedName = "")
                                .apply {
                                    if (it.value != null) {
                                        setString("value", it.value)
                                    }
                                }
                                .build()
                        )
                    }

                    updateNoteAppFunctionParams.attachments?.let {
                        setAppFunctionData(
                            "attachments",
                            AppFunctionData.Builder(qualifiedName = "")
                                .setAppFunctionDataList(
                                    "value",
                                    it.value.map { attachment -> attachment.downgradeAttachment() }
                                )
                                .build()
                        )
                    }
                }
                .build()

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData("updateNoteParams", downgradedRequest)
            .build()
    }

    override fun downgradeResponse(response: AppFunctionData): AppFunctionData {
        val responseData = checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))

        val updateNoteAppFunctionResponse =
            responseData.deserialize(UpdateNoteAppFunctionResponse::class.java)
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                PROPERTY_RETURN_VALUE,
                updateNoteAppFunctionResponse.updatedNote.downgradeNote()
            )
            .build()
    }
}

@AppFunctionSerializable
internal data class UpdateNoteAppFunctionParams(
    override val noteId: String,
    override val title: SetField<String>? = null,
    override val content: SetField<String?>? = null,
    override val attachments: SetField<List<AttachmentImpl>>? = null
) : UpdateNoteAppFunction.Parameters

@AppFunctionSerializable
internal data class UpdateNoteAppFunctionResponse(override val updatedNote: AppFunctionNoteImpl) :
    UpdateNoteAppFunction.Response
