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

package androidx.appfunctions.integration.tests

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionOpenable
import androidx.appfunctions.AppFunctionSerializable
import java.time.LocalDateTime

@AppFunctionSerializable data class SetField<T>(val value: T)

@AppFunctionSerializable
data class CreateNoteParams(
    val title: String,
    val content: List<String>,
    val owner: Owner,
    val attachments: List<Attachment>,
)

// TODO(b/401517540): Test AppFunctionSerializable
@AppFunctionSerializable
data class UpdateNoteParams(
    val title: SetField<String>? = null,
    val nullableTitle: SetField<String?>? = null,
    val content: SetField<List<String>>? = null,
    val nullableContent: SetField<List<String>?>? = null,
)

@AppFunctionSerializable
data class Owner(
    val name: String,
)

@AppFunctionSerializable
data class Attachment(
    val uri: String,
    val nested: Attachment? = null,
)

@AppFunctionSerializable
data class Note(
    val title: String,
    val content: List<String>,
    val owner: Owner,
    val attachments: List<Attachment>,
)

@AppFunctionSerializable
data class OpenableNote(
    val title: String,
    val content: List<String>,
    val owner: Owner,
    val attachments: List<Attachment>,
    override val intentToOpen: PendingIntent
) : AppFunctionOpenable

@AppFunctionSerializable data class DateTime(val localDateTime: LocalDateTime)

@Suppress("UNUSED_PARAMETER")
class TestFunctions {
    @AppFunction
    fun add(appFunctionContext: AppFunctionContext, num1: Long, num2: Long) = num1 + num2

    @AppFunction
    fun logLocalDateTime(appFunctionContext: AppFunctionContext, dateTime: DateTime) {
        Log.d("TestFunctions", "LocalDateTime: ${dateTime.localDateTime}")
    }

    @AppFunction
    fun getLocalDate(appFunctionContext: AppFunctionContext): DateTime {
        return DateTime(localDateTime = LocalDateTime.now())
    }

    @AppFunction
    fun doThrow(appFunctionContext: AppFunctionContext) {
        throw AppFunctionInvalidArgumentException("invalid")
    }

    @AppFunction fun voidFunction(appFunctionContext: AppFunctionContext) {}

    @AppFunction
    fun createNote(
        appFunctionContext: AppFunctionContext,
        createNoteParams: CreateNoteParams
    ): Note {
        return Note(
            title = createNoteParams.title,
            content = createNoteParams.content,
            owner = createNoteParams.owner,
            attachments = createNoteParams.attachments
        )
    }

    @AppFunction
    fun updateNote(
        appFunctionContext: AppFunctionContext,
        updateNoteParams: UpdateNoteParams,
    ): Note {
        return Note(
            title =
                (updateNoteParams.title?.value ?: "DefaultTitle") +
                    "_" +
                    (updateNoteParams.nullableTitle?.value ?: "DefaultTitle"),
            content =
                (updateNoteParams.content?.value ?: listOf("DefaultContent")) +
                    (updateNoteParams.nullableContent?.value ?: listOf("DefaultContent")),
            owner = Owner("test"),
            attachments = listOf(),
        )
    }

    @AppFunction
    fun getOpenableNote(
        appFunctionContext: AppFunctionContext,
        createNoteParams: CreateNoteParams
    ): OpenableNote {
        return OpenableNote(
            title = createNoteParams.title,
            content = createNoteParams.content,
            owner = createNoteParams.owner,
            attachments = createNoteParams.attachments,
            intentToOpen =
                PendingIntent.getActivity(
                    appFunctionContext.context,
                    0,
                    Intent(),
                    PendingIntent.FLAG_IMMUTABLE
                )
        )
    }
}

@Suppress("UNUSED_PARAMETER")
class TestFactory {
    private val createdByFactory: Boolean

    constructor() : this(false)

    constructor(createdByFactory: Boolean) {
        this.createdByFactory = createdByFactory
    }

    @AppFunction
    fun isCreatedByFactory(appFunctionContext: AppFunctionContext): Boolean = createdByFactory
}
