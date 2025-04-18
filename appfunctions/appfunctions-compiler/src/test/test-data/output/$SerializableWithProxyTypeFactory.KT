package com.testdata

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.`internal`.AppFunctionSerializableFactory
import androidx.appfunctions.`internal`.serializableproxies.`$LocalDateTimeFactory`
import androidx.appfunctions.`internal`.serializableproxies.`$UriFactory`
import javax.`annotation`.processing.Generated

@Generated("androidx.appfunctions.compiler.AppFunctionCompiler")
public class `$SerializableWithProxyTypeFactory` : AppFunctionSerializableFactory<SerializableWithProxyType> {
  override fun fromAppFunctionData(appFunctionData: AppFunctionData): SerializableWithProxyType {
    val appFunctionLocalDateTimeFactory = `$LocalDateTimeFactory`()
    val appFunctionUriFactory = `$UriFactory`()

    val localDateTimeData = checkNotNull(appFunctionData.getAppFunctionData("localDateTime"))
    val localDateTime = appFunctionLocalDateTimeFactory.fromAppFunctionData(localDateTimeData)
    val localDateTimeListData = appFunctionData.getAppFunctionDataList("localDateTimeList") ?: emptyList()
    val localDateTimeList = localDateTimeListData.map { data ->
      appFunctionLocalDateTimeFactory.fromAppFunctionData(data)
    }
    val androidUriData = checkNotNull(appFunctionData.getAppFunctionData("androidUri"))
    val androidUri = appFunctionUriFactory.fromAppFunctionData(androidUriData)
    val androidUriListData = appFunctionData.getAppFunctionDataList("androidUriList") ?: emptyList()
    val androidUriList = androidUriListData.map { data ->
      appFunctionUriFactory.fromAppFunctionData(data)
    }

    val resultSerializableWithProxyType = SerializableWithProxyType(localDateTime, localDateTimeList, androidUri, androidUriList)
    return resultSerializableWithProxyType
  }

  override fun toAppFunctionData(appFunctionSerializable: SerializableWithProxyType): AppFunctionData {
    val serializableWithProxyType_appFunctionSerializable = appFunctionSerializable
    val appFunctionLocalDateTimeFactory = `$LocalDateTimeFactory`()
    val appFunctionUriFactory = `$UriFactory`()

    val builder = AppFunctionData.Builder("com.testdata.SerializableWithProxyType")
    val localDateTime = serializableWithProxyType_appFunctionSerializable.localDateTime
    builder.setAppFunctionData("localDateTime", appFunctionLocalDateTimeFactory.toAppFunctionData(localDateTime))
    val localDateTimeList = serializableWithProxyType_appFunctionSerializable.localDateTimeList
    builder.setAppFunctionDataList("localDateTimeList", localDateTimeList.map{ appFunctionLocalDateTime ->
      appFunctionLocalDateTimeFactory.toAppFunctionData(appFunctionLocalDateTime)
    })
    val androidUri = serializableWithProxyType_appFunctionSerializable.androidUri
    builder.setAppFunctionData("androidUri", appFunctionUriFactory.toAppFunctionData(androidUri))
    val androidUriList = serializableWithProxyType_appFunctionSerializable.androidUriList
    builder.setAppFunctionDataList("androidUriList", androidUriList.map{ appFunctionUri ->
      appFunctionUriFactory.toAppFunctionData(appFunctionUri)
    })

    return builder.build()
  }
}
