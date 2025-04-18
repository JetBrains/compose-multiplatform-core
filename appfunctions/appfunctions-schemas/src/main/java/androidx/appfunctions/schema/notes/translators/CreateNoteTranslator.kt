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
import androidx.appfunctions.schema.notes.AppFunctionNote
import androidx.appfunctions.schema.notes.CreateNoteAppFunction

@RequiresApi(33)
internal class CreateNoteTranslator : Translator {
    override fun upgradeRequest(request: AppFunctionData): AppFunctionData {
        val legacyCreateNoteParams = checkNotNull(request.getAppFunctionData("createNoteParams"))
        val parameters =
            CreateNoteAppFunctionParams(
                checkNotNull(legacyCreateNoteParams.getString("title")),
                legacyCreateNoteParams.getString("content"),
                legacyCreateNoteParams.getString("externalId")
            )
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                "parameters",
                AppFunctionData.serialize(parameters, CreateNoteAppFunctionParams::class.java)
            )
            .build()
    }

    override fun upgradeResponse(response: AppFunctionData): AppFunctionData {
        val legacyCreateNoteResponse =
            checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))
        val upgradedResponse =
            CreateNoteAppFunctionResponse(
                createdNote =
                    AppFunctionNoteImpl(
                        id = legacyCreateNoteResponse.id,
                        title = checkNotNull(legacyCreateNoteResponse.getString("title")),
                        content = legacyCreateNoteResponse.getString("content")
                    )
            )
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                PROPERTY_RETURN_VALUE,
                AppFunctionData.serialize(
                    upgradedResponse,
                    CreateNoteAppFunctionResponse::class.java
                )
            )
            .build()
    }

    override fun downgradeRequest(request: AppFunctionData): AppFunctionData {
        val parametersData = checkNotNull(request.getAppFunctionData("parameters"))
        val createNoteAppFunctionParams =
            parametersData.deserialize(CreateNoteAppFunctionParams::class.java)
        val downgradedRequestData =
            AppFunctionData.Builder(checkNotNull(CreateNoteAppFunctionParams::class.qualifiedName))
                .setString("title", createNoteAppFunctionParams.title)
                .apply {
                    createNoteAppFunctionParams.content?.let { setString("content", it) }
                    createNoteAppFunctionParams.externalUuid?.let { setString("externalId", it) }
                }
                .build()
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData("createNoteParams", downgradedRequestData)
            .build()
    }

    override fun downgradeResponse(response: AppFunctionData): AppFunctionData {
        val responseData = checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))
        val createNoteAppFunctionResponse =
            responseData.deserialize(CreateNoteAppFunctionResponse::class.java)
        val downgradedData =
            AppFunctionData.Builder(
                    checkNotNull(AppFunctionNoteImpl::class.qualifiedName),
                    id = createNoteAppFunctionResponse.createdNote.id,
                )
                .setString("title", createNoteAppFunctionResponse.createdNote.title)
                .apply {
                    createNoteAppFunctionResponse.createdNote.content?.let {
                        setString("content", it)
                    }
                }
                .build()
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(PROPERTY_RETURN_VALUE, downgradedData)
            .build()
    }
}

@AppFunctionSerializable
internal data class CreateNoteAppFunctionParams(
    override val title: String,
    override val content: String? = null,
    override val externalUuid: String? = null
) : CreateNoteAppFunction.Parameters

@AppFunctionSerializable
internal data class CreateNoteAppFunctionResponse(override val createdNote: AppFunctionNoteImpl) :
    CreateNoteAppFunction.Response

@AppFunctionSerializable
internal data class AppFunctionNoteImpl(
    override val id: String,
    override val title: String,
    override val content: String? = null
) : AppFunctionNote
