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

import android.app.PendingIntent
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
import androidx.appfunctions.internal.Translator
import androidx.appfunctions.schema.notes.ShowNoteAppFunction

@RequiresApi(33)
internal class ShowNoteTranslator : Translator {
    override fun upgradeRequest(request: AppFunctionData): AppFunctionData {
        val parameters =
            ShowNoteAppFunctionParams(noteId = checkNotNull(request.getString("noteId")))

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                "parameters",
                AppFunctionData.serialize(parameters, ShowNoteAppFunctionParams::class.java)
            )
            .build()
    }

    override fun upgradeResponse(response: AppFunctionData): AppFunctionData {
        val upgradedResponse =
            ShowNoteAppFunctionResponse(
                intentToOpen = checkNotNull(response.getPendingIntent(PROPERTY_RETURN_VALUE))
            )

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                PROPERTY_RETURN_VALUE,
                AppFunctionData.serialize(upgradedResponse, ShowNoteAppFunctionResponse::class.java)
            )
            .build()
    }

    override fun downgradeRequest(request: AppFunctionData): AppFunctionData {
        val showNoteAppFunctionParams =
            checkNotNull(request.getAppFunctionData("parameters"))
                .deserialize(ShowNoteAppFunctionParams::class.java)
        return AppFunctionData.Builder(qualifiedName = "")
            .setString("noteId", showNoteAppFunctionParams.noteId)
            .build()
    }

    override fun downgradeResponse(response: AppFunctionData): AppFunctionData {
        val showNoteAppFunctionResponse =
            checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))
                .deserialize(ShowNoteAppFunctionResponse::class.java)
        return AppFunctionData.Builder(qualifiedName = "")
            .setPendingIntent(PROPERTY_RETURN_VALUE, showNoteAppFunctionResponse.intentToOpen)
            .build()
    }
}

@AppFunctionSerializable
internal data class ShowNoteAppFunctionParams(override val noteId: String) :
    ShowNoteAppFunction.Parameters

@AppFunctionSerializable
internal data class ShowNoteAppFunctionResponse(override val intentToOpen: PendingIntent) :
    ShowNoteAppFunction.Response
