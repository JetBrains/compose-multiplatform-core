package com.testdata

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.`internal`.AppFunctionSerializableFactory
import javax.`annotation`.processing.Generated

@Generated("androidx.appfunctions.compiler.AppFunctionCompiler")
public class `$SetFieldFactory`<T>(
  private val tTypeParameter: AppFunctionSerializableFactory.TypeParameter<T>,
) : AppFunctionSerializableFactory<SetField<T>> {
  override fun fromAppFunctionData(appFunctionData: AppFunctionData): SetField<T> {

    val value = when (tTypeParameter) {
      is AppFunctionSerializableFactory.TypeParameter.ListTypeParameter<*, *> -> {
        appFunctionData.getGenericListField("value", tTypeParameter.itemClazz)
      }
      is AppFunctionSerializableFactory.TypeParameter.PrimitiveTypeParameter -> {
        appFunctionData.getGenericField("value", tTypeParameter.clazz)
      }
    }

    val resultSetField = SetField(value)
    return resultSetField
  }

  override fun toAppFunctionData(appFunctionSerializable: SetField<T>): AppFunctionData {
    val setField_appFunctionSerializable = appFunctionSerializable

    val builder = AppFunctionData.Builder("com.testdata.SetField")
    val value = setField_appFunctionSerializable.value
    when (tTypeParameter) {
      is AppFunctionSerializableFactory.TypeParameter.ListTypeParameter<*, *> -> {
        builder.setGenericListField("value", value as List<*>?, tTypeParameter.itemClazz)
      }
      is AppFunctionSerializableFactory.TypeParameter.PrimitiveTypeParameter -> {
        builder.setGenericField("value", value, tTypeParameter.clazz)
      }
    }

    return builder.build()
  }
}
