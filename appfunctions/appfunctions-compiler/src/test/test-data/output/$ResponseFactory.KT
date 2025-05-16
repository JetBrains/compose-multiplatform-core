package com.testdata

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.`internal`.AppFunctionSerializableFactory
import javax.`annotation`.processing.Generated

@Generated("androidx.appfunctions.compiler.AppFunctionCompiler")
public class `$ResponseFactory` : AppFunctionSerializableFactory<CreateNoteFunction.Response> {
  override fun fromAppFunctionData(appFunctionData: AppFunctionData): CreateNoteFunction.Response {
    val myNoteFactory = `$MyNoteFactory`()

    val createdNoteData = checkNotNull(appFunctionData.getAppFunctionData("createdNote"))
    val createdNote = myNoteFactory.fromAppFunctionData(createdNoteData)

    val resultResponse = CreateNoteFunction.Response(createdNote)
    return resultResponse
  }

  override fun toAppFunctionData(appFunctionSerializable: CreateNoteFunction.Response): AppFunctionData {
    val response_appFunctionSerializable = appFunctionSerializable
    val myNoteFactory = `$MyNoteFactory`()

    val builder = AppFunctionData.Builder("com.testdata.CreateNoteFunction${'$'}Response")
    val createdNote = response_appFunctionSerializable.createdNote
    builder.setAppFunctionData("createdNote", myNoteFactory.toAppFunctionData(createdNote))

    return builder.build()
  }
}
