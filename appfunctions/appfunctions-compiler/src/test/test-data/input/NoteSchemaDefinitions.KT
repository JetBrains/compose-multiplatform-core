package com.testdata

import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSchemaDefinition
import androidx.appfunctions.AppFunctionSerializableInterface
import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class SetField<out T>(val value: T)

@AppFunctionSerializableInterface
interface Attachment {
    val uri: String
}

@AppFunctionSerializableInterface
interface Note {
    val id: String
    val title: String
    val content: List<String>?
    val attachments: List<Attachment>
}

@AppFunctionSchemaDefinition(category = "note", name = "updateNote", version = 2)
interface UpdateNoteFunction<Params: UpdateNoteFunction.Parameters, Res: UpdateNoteFunction.Response> {

    suspend fun updateNote(
        appFunctionContext: AppFunctionContext,
        parameters: Params
    ): Res

    @AppFunctionSerializableInterface
    interface Parameters {
        val noteId: String
        val title: SetField<String>?
        val content: SetField<String?>?
        val attachments: SetField<List<Attachment>>?
    }

    @AppFunctionSerializableInterface
    interface Response {
        val note: Note
    }
}
