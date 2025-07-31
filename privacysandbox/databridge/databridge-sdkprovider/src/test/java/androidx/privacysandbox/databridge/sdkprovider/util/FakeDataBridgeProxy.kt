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

package androidx.privacysandbox.databridge.sdkprovider.util

import androidx.privacysandbox.databridge.core.Key
import androidx.privacysandbox.databridge.core.aidl.IDataBridgeProxy
import androidx.privacysandbox.databridge.core.aidl.IGetValuesResultCallback
import androidx.privacysandbox.databridge.core.aidl.IKeyUpdateInternalCallback
import androidx.privacysandbox.databridge.core.aidl.IRemoveValuesResultCallback
import androidx.privacysandbox.databridge.core.aidl.ISetValuesResultCallback
import androidx.privacysandbox.databridge.core.aidl.ResultInternal
import androidx.privacysandbox.databridge.core.aidl.ValueInternal
import java.lang.IllegalStateException

class FakeDataBridgeProxy(
    private val shouldThrowException: Boolean = false,
    private val exceptionName: String? = null,
    private val exceptionMessage: String? = null,
    private val resultInternals: List<ResultInternal> = emptyList(),
) : IDataBridgeProxy.Stub() {
    private val keysRegisteredForUpdates = mutableSetOf<Key>()
    private var keyUpdateInternalCallback: IKeyUpdateInternalCallback? = null

    override fun getValues(
        keyNames: List<String>,
        keyTypes: List<String>,
        callback: IGetValuesResultCallback,
    ) {
        callback.getValuesResult(resultInternals)
    }

    override fun setValues(
        keyNames: List<String>,
        data: List<ValueInternal>,
        callback: ISetValuesResultCallback,
    ) {
        if (shouldThrowException) {
            callback.setValuesResult(exceptionName, exceptionMessage)
        } else {
            callback.setValuesResult(/* exceptionName= */ null, /* exceptionMessage= */ null)
        }
    }

    override fun removeValues(
        keyNames: List<String>,
        keyTypes: List<String>,
        callback: IRemoveValuesResultCallback,
    ) {
        if (shouldThrowException) {
            callback.removeValuesResult(exceptionName, exceptionMessage)
        } else {
            callback.removeValuesResult(/* exceptionName= */ null, /* exceptionMessage= */ null)
        }
    }

    override fun addKeysForUpdates(
        uuid: String,
        keyNames: List<String>,
        keyTypes: List<String>,
        callback: IKeyUpdateInternalCallback,
    ) {
        if (keyUpdateInternalCallback == null) {
            keyUpdateInternalCallback = callback
        }

        val keys = getKeySet(keyNames, keyTypes)
        keysRegisteredForUpdates.addAll(keys)
        triggerFakeUpdate(keys)
    }

    override fun removeKeysFromUpdates(
        uuid: String,
        keyNames: List<String>,
        keyTypes: List<String>,
        unregisterCallback: Boolean,
    ) {
        if (unregisterCallback) {
            keyUpdateInternalCallback = null
        }
        val keys = getKeySet(keyNames, keyTypes)
        keysRegisteredForUpdates.removeAll(keys)
    }

    fun getKeysRegisteredForUpdate(): Set<Key> {
        return keysRegisteredForUpdates
    }

    private fun getKeySet(keyNames: List<String>, keyTypes: List<String>): Set<Key> {
        return keyNames
            .zip(keyTypes)
            .map { (name, typeString) ->
                when (typeString) {
                    "INT" -> Key.createIntKey(name)
                    "LONG" -> Key.createLongKey(name)
                    "FLOAT" -> Key.createFloatKey(name)
                    "DOUBLE" -> Key.createDoubleKey(name)
                    "BOOLEAN" -> Key.createBooleanKey(name)
                    "STRING" -> Key.createStringKey(name)
                    "STRING_SET" -> Key.createStringSetKey(name)
                    "BYTE_ARRAY" -> Key.createByteArrayKey(name)
                    else -> throw IllegalStateException("$typeString is not a valid key type")
                }
            }
            .toSet()
    }

    fun triggerFakeUpdate(keys: Set<Key>) {
        // Make a call to the onKeyUpdate function to ensure that caller receives the callbacks
        keys.forEach { key ->
            keyUpdateInternalCallback?.onKeyUpdated(
                key.name,
                ValueInternal(key.type.toString(), isValueNull = true, null),
            )
        }
    }
}
