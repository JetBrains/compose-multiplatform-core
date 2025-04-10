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
import androidx.appfunctions.schema.notes.FindNotesAppFunction
import androidx.appfunctions.schema.notes.translators.NoteTranslator.downgradeNote
import androidx.appfunctions.schema.notes.translators.NoteTranslator.upgradeNote
import java.time.LocalDateTime
import java.time.ZoneId

@RequiresApi(33)
internal class FindNotesTranslator : Translator {
    override fun upgradeRequest(request: AppFunctionData): AppFunctionData {
        val legacyFindNoteParams = checkNotNull(request.getAppFunctionData("findNotesParams"))

        val parameters =
            FindNotesAppFunctionParams(
                query = legacyFindNoteParams.getStringOrNull("query"),
                modifiedAfter =
                    legacyFindNoteParams.getAppFunctionData("startDate")?.toLocalDateTime(),
                modifiedBefore =
                    legacyFindNoteParams.getAppFunctionData("endDate")?.toLocalDateTime()
            )

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                "parameters",
                AppFunctionData.serialize(parameters, FindNotesAppFunctionParams::class.java)
            )
            .build()
    }

    override fun upgradeResponse(response: AppFunctionData): AppFunctionData {
        val legacyFindNotesResponse =
            checkNotNull(response.getAppFunctionDataList(PROPERTY_RETURN_VALUE))

        val notes = legacyFindNotesResponse.map { it.upgradeNote() }

        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData(
                PROPERTY_RETURN_VALUE,
                AppFunctionData.serialize(
                    FindNotesAppFunctionResponse(notes),
                    FindNotesAppFunctionResponse::class.java
                )
            )
            .build()
    }

    override fun downgradeRequest(request: AppFunctionData): AppFunctionData {
        val parametersData = checkNotNull(request.getAppFunctionData("parameters"))
        val findNotesAppFunctionParams =
            parametersData.deserialize(FindNotesAppFunctionParams::class.java)
        val downgradedRequestData =
            AppFunctionData.Builder(qualifiedName = "")
                .apply {
                    if (findNotesAppFunctionParams.query != null) {
                        setString("query", findNotesAppFunctionParams.query)
                    }
                    if (findNotesAppFunctionParams.modifiedAfter != null) {
                        setAppFunctionData(
                            "startDate",
                            findNotesAppFunctionParams.modifiedAfter.toLegacyAppFunctionData()
                        )
                    }
                    if (findNotesAppFunctionParams.modifiedBefore != null) {
                        setAppFunctionData(
                            "endDate",
                            findNotesAppFunctionParams.modifiedBefore.toLegacyAppFunctionData()
                        )
                    }

                    setInt("maxCount", Int.MAX_VALUE)
                }
                .build()
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionData("findNotesParams", downgradedRequestData)
            .build()
    }

    override fun downgradeResponse(response: AppFunctionData): AppFunctionData {
        val responseData = checkNotNull(response.getAppFunctionData(PROPERTY_RETURN_VALUE))
        val findNotesAppFunctionResponse =
            responseData.deserialize(FindNotesAppFunctionResponse::class.java)
        return AppFunctionData.Builder(qualifiedName = "")
            .setAppFunctionDataList(
                PROPERTY_RETURN_VALUE,
                findNotesAppFunctionResponse.notes.map { it.downgradeNote() }
            )
            .build()
    }

    companion object {
        private fun AppFunctionData.toLocalDateTime(): LocalDateTime {
            val date = checkNotNull(getAppFunctionData("date"))
            val timeOfDay = checkNotNull(getAppFunctionData("timeOfDay"))
            return LocalDateTime.of(
                /* year= */ date.getInt("year"),
                /* month= */ date.getInt("month"),
                /* dayOfMonth= */ date.getInt("day"),
                /* hour= */ timeOfDay.getInt("hours"),
                /* minute= */ timeOfDay.getInt("minutes"),
                /* second= */ timeOfDay.getInt("seconds"),
                /* nanoOfSecond= */ timeOfDay.getInt("nanos")
            )
        }

        private fun LocalDateTime.toLegacyAppFunctionData() =
            AppFunctionData.Builder(qualifiedName = "")
                // Use default time zone on device since in new schema we don't support specifying
                // time zone.
                .setString("timeZone", ZoneId.systemDefault().toString())
                .setAppFunctionData(
                    "date",
                    AppFunctionData.Builder(qualifiedName = "")
                        .setInt("year", year)
                        .setInt("month", monthValue)
                        .setInt("day", dayOfMonth)
                        .build()
                )
                .setAppFunctionData(
                    "timeOfDay",
                    AppFunctionData.Builder(qualifiedName = "")
                        .setInt("hours", hour)
                        .setInt("minutes", minute)
                        .setInt("seconds", second)
                        .setInt("nanos", nano)
                        .build()
                )
                .build()
    }
}

@AppFunctionSerializable
internal data class FindNotesAppFunctionParams(
    override val query: String? = null,
    override val modifiedAfter: LocalDateTime? = null,
    override val modifiedBefore: LocalDateTime? = null
) : FindNotesAppFunction.Parameters

@AppFunctionSerializable
internal data class FindNotesAppFunctionResponse(
    override val notes: List<AppFunctionNoteImpl> = emptyList()
) : FindNotesAppFunction.Response
