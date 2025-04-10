package com.testdata

import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionContext
import androidx.appfunctions.AppFunctionSerializable

@AppFunctionSerializable
data class SetField<T>(val value: T)

@AppFunctionSerializable
data class Attachment(val uri: String)

@AppFunctionSerializable
data class UpdateNoteParams(
    val title: SetField<String>? = null,
    val nullableTitle: SetField<String?>? = null,
    val attachments: SetField<List<Attachment>>? = null,
    val nullableAttachments: SetField<List<Attachment>?>? = null,
)

class FunctionWithGenericSerializable {
    @AppFunction
    suspend fun test(
        appFunctionContext: AppFunctionContext,
        params: UpdateNoteParams,
        topLevelGenericParam: SetField<Int>?
    ) {}
}